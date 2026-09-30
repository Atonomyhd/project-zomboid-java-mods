# Shard transfer mod — investigation and design (2026-09-07)

Goal: a Storm java mod (this repo) that lets a main server ("home", A) start and
supervise a second dedicated server ("shard", B), move a player's character to B,
and make a death on B count as a death on A. Working name: `shard-transfer`
(placeholder — rename at mod creation).

Paths: PZ source = `~/projects/project-zomboid-base/src/main/java` (`PZ/`),
Storm = `~/projects/project-zomboid-storm/src/main/java/io/pzstorm/storm` (`STORM/`).
All line numbers are 42.20.4.

## 1. Headline decisions

1. **Do not proxy RakNet packets through A.** It is not feasible in Steam mode
   and not worth it in nosteam mode (§3). Instead: A forwards the *character*
   to B out of band (HTTP between the two JVMs) and tells the *client* to
   reconnect to B directly. B then runs its normal login / relevance / ownership
   machinery against a real client connection.
2. **Raw `IsoPlayer.save()` blob is the transfer format**, made portable by
   seeding B's save dir with A's `WorldDictionary.bin` at shard creation (§4.2).
   No custom character format needed.
3. **Death sync is one SQLite column** on A: `networkPlayers.isDead` in
   `<save>/players.db`, written through A's own `ServerPlayerDB` connection (§6).
4. **Client side needs mod Java, not a bytecode patch.** The hop is driven from
   `@SubscribeEvent` handlers on existing Lua-event bridges plus the
   `LauncherAutoJoin` connect-popup pattern. Cost per hop ≈ a manual
   disconnect + rejoin (IngameState reset + ResetLua ≈ 25–30 s + load). A
   faster "don't reload identical mods" tier needs patches and is optional (§5.4).

## 2. Component map

```
 home server A (JVM)                       shard server B (JVM, child process of A)
 ┌──────────────────────────┐              ┌──────────────────────────┐
 │ ShardSupervisor          │──spawn/stop──▶│ (same mod jar, role=shard)│
 │ ShardLink (HttpClient)   │◀──HTTP/loopback─▶ ShardLink (@HttpEndpoint) │
 │ TransferRegistry (json)  │              │ TransferRegistry          │
 │ /shard command, gates    │              │ login gate, death hook    │
 └──────────┬───────────────┘              └──────────┬───────────────┘
            │ sendServerCommand("shard","hop",{host,port,pw,nonce})
            ▼                                          ▲ normal RakNet join
 client (Storm launcher, mod jar loaded client-side): ShardHopClient
   OnServerCommand → stash target → Core.exitToMenu() → OnMainMenuEnter →
   drive ConnectToServer:connect(popup,"",username,password,host,"",port,pw,relay,false,1)
```

Both server JVMs run the same jar; role is decided by a system property the
supervisor passes (`-Dshard.role=shard -Dshard.home=http://127.0.0.1:<A http port>`).

## 3. Why not forward packets (what was checked)

- **Steam auth is native and identity-bound.** B's RakNet native validates the
  client's Steam ticket before Java sees anything, then calls
  `SteamUtils.clientInitiateConnectionCallback(steamID)` (`PZ/zombie/core/znet/SteamUtils.java:214`).
  A's JVM initialises only `SteamGameServer` (`SteamUtils.init` → `n_Init(GameServer.server)`,
  line 97); there is no `ISteamUser`, so A cannot mint or replay a ticket. B would
  see A's IP and no user identity; `ServerWorldDatabase.authClient` (1056-1064)
  rejects unless `Open`.
- **The server JVM routes by static flags.** `RakNetPeerInterface.Startup(int)`
  hard-codes `GameServer.server` as the isServer flag (`RakNetPeerInterface.java:64-66`);
  `UdpEngine.decode` routes inbound by `GameServer.server` / `GameClient.client`
  (`UdpEngine.java:185, 295`). A client-mode peer inside A would have B's replies
  fed into `GameServer.addIncoming` as if from a player. Every existing
  "client peer" (`FakeClientManager`, `STORM/query/StormQueryClient`,
  `ServerModListProbe`) runs in its own child JVM for this reason.
- **No envelope to rewrite.** Slot/online ids (`GameServer.receiveClientConnect:2659-2672`,
  `ConnectionDetails.java:64`), zombie/vehicle owner ids, chunk requests and
  `RequestDataManager` transfer state are per-server and embedded in ~300
  packet-specific payloads. A proxy would parse and rewrite all of them both ways
  and mirror B's `UdpConnection` relevance state (`releventPos`, `loadedCells`,
  zombie `authOwner`) for a connection it does not own.
- Nosteam-only impersonation is possible (Login is just username/password,
  `LoginPacket.java:232-237`) but that is not the production regime.

## 4. Character transfer

### 4.1 What a character is on the server

- Row in `<Saves/Multiplayer/<serverName>>/players.db` table `networkPlayers`
  keyed `(username, world, playerIndex)` on dedicated servers
  (`PZ/zombie/savefile/ServerPlayerDB.java:129-133`; schema `PlayerDBHelper.java:40`).
  Columns: `name, steamid, x, y, z, worldversion (=249), data BLOB, isDead`.
- `data` = exactly `IsoPlayer.save(ByteBuffer)`: modData, SurvivorDesc, visuals,
  full inventory (nested containers), Stats, BodyDamage, XP/perks/traits, worn
  items, hours survived, kills, nutrition, fitness, chat tag, extraInfoFlags,
  **absolute x/y/z**, **seated vehicle (x, y, seat)**, read books.
- Write cadence: every 180 s per connection (`UdpConnection.java:88`), on
  disconnect (`GameServer.java:3003`), on world save, on CreatePlayer. SQL runs on
  `SPVThread` every 500 ms; serialization happens on the enqueuing (main) thread.
- Client never uploads character bytes (B42 is server-authoritative). On join:
  `LoadPlayerProfile` returns the row (`LoadPlayerProfilePacket.java:153-205`, 10 s
  client walk timeout), then `receivePlayerConnect` re-loads it from the DB
  (`GameServer.java:2775-2779`) and kicks with `UI_LoadPlayerProfileError` if the
  load fails. **On load exception the row is DELETED** (`ServerPlayerDB.java:255-273`) —
  never insert an unvalidated blob.
- Account row lives elsewhere: `db/<serverName>.db` table `whitelist`
  (username, password hash, role, steamid) (`ServerWorldDatabase.java:450`).
  Password hash uses a fixed BCrypt salt (`PZcrypt.java:6`) so A's stored hash is
  valid on B.

### 4.2 Portability: the WorldDictionary trap and its fix

Every item in the blob is written as `short registry_id` from `WorldDictionary`
(`InventoryItem.java:1660`; same for `alreadyReadBook`, `IsoPlayer.java:1482`).
Ids are allocated by `nextInfoId++` in mod load order (`DictionaryData.java:257-265`)
and persisted in `<save>/WorldDictionary.bin`. `WorldDictionary.init()` loads
that file **before** parsing the load list (`WorldDictionary.java:270-276`), so
already-known types keep their ids.

Therefore: when the supervisor creates B's save dir, copy A's
`WorldDictionary.bin` (and only that) into it before B's first boot, and run B
with the identical `Mods=` / `WorkshopItems=` lines. Both dictionaries are then
byte-equivalent and the raw blob is portable. Guard it anyway: at link-up, both
sides exchange a hash of `itemTypeToInfoMap` (fullType → id) and refuse transfers
on mismatch (fail closed; log the diff). Ids only diverge after a mod-set change,
which restarts both servers; re-seed is impossible once B has its own world, so a
mismatch means "rebuild the shard" — an operator decision, surfaced loudly.

### 4.3 Export on A (main thread)

1. Player must be live (`GameServer.IDToPlayerMap` / `getPlayerByUserName`);
   offline export uses `ServerPlayerDB.serverLoadNetworkCharacter(0, username)`
   (precedent: `PlayerInventoryPacket.java:53-57`).
2. Refuse if: in a vehicle (or force-exit first), dead, trading, has an active
   timed action, or role/capability policy says no.
3. `player.save(buf)` into a growable buffer (see `NetworkCharacterData` ctor,
   `ServerPlayerDB.java:348-386`, 32 KB → 1 MB; Storm's `CreateTestCharCommand`
   uses 500 KB).
4. Payload to B: `{nonce, username, steamid (connection.getIDStr()), displayName,
   worldVersion, blob(base64), whitelistRow{passwordHash, role}, target{x,y,z},
   dictionaryHash}`.
5. Record `TransferRegistry[username] = {shard, nonce, state=EXPORTED, ts}` on
   disk (JSON in A's cachedir; must survive A restarts).

### 4.4 Import on B (main thread, world loaded)

1. Verify `dictionaryHash`, `worldVersion == IsoWorld.getWorldVersion()`.
2. Round-trip: `new IsoPlayer(IsoWorld.instance.currentCell)`; `player.load(buf, wv)`.
   Any exception → reply 409, do not touch the DB.
3. Rewrite: `setX/Y/Z(target)`, clear the saved-vehicle fields, drop `Key` items
   whose `keyId` matches no B vehicle (or keep — they just won't open anything).
4. `player.save(buf2)`; UPSERT into B's `networkPlayers` `(username, world=B name,
   playerIndex=0, x,y,z, worldversion, isDead=0, data, name, steamid)` via
   `ServerPlayerDB.getInstance().conn` + `commit()` (autocommit is off) —
   the exact shape of `STORM/commands/CreateTestCharCommand.java:82-118`.
5. UPSERT B's `whitelist` row (username, password hash, role, steamid) so
   `authClient` passes without `Open=true`.
6. Reply 200 `{nonce}`; mark `TransferRegistry[username] = {home, nonce, state=READY}`.

`ServerPlayerDB.getInstance()` is null until `IsoWorld.init` calls `setAllow(true)`;
the endpoint must 503 until B reports READY (§7).

### 4.5 Ordering and the disconnect save

A's `GameServer.disconnect` enqueues a save of the live player *after* the hop
command is sent, and that save races nothing on B (B has its own row). But the
export must capture the final state, so the sequence is:

1. Admin/trigger → A validates → A sends `hop` to the client → client disconnects.
2. A's disconnect hook (Storm `OnPlayerDisconnect`-style event or advice on
   `GameServer.disconnect`) sees a PENDING transfer → exports from the live
   object right there (still on main thread, object intact) → POST to B.
3. B imports (ms on loopback). The client meanwhile spends ≥25 s in
   IngameState.exit + ResetLua, so B is ready long before its `LoadPlayerProfile`.
4. Guard the race anyway: B's login gate (advice on `LoginPacket.processServer`
   or the pre-login `addIncoming` seam `STORM/patch/networking/ServerQueryPatch`)
   kicks with "transfer in progress, retry" when a transfer for that username is
   ANNOUNCED but not READY.

### 4.6 While the character is away, and the return trip

- A's login gate: if `TransferRegistry[username].state == AWAY`, A does not load
  its (stale) row. Policy options: (a) redirect the client to the shard again, or
  (b) pull the character back: A asks B `GET /shard/export?username` (B does §4.3
  from its live or offline copy), A imports (§4.4 with A's own target = last A
  position stored in the registry), clears the record, then lets login proceed.
  Prefer (b) — it also handles "shard is done with me" without a second UI.
- If B is unreachable at return time: refuse login with a clear message rather
  than loading the stale A row (that would dupe the inventory).
- B pushes a snapshot to A on its own save cadence (piggyback on
  `OnPreSaveEvent` / every 180 s). A stores it as `lastSnapshot` so a dead shard
  costs at most one interval of progress. Whether to auto-restore from a snapshot
  after N minutes of shard silence is a policy knob.
- Same code path in reverse makes the mod symmetric: every node has a link, a
  registry, an export endpoint and an import endpoint; only "home" spawns children.

### 4.7 Sidecar state (decide per feature, default = does not travel)

Safehouse/faction membership (`map_meta.bin`, username-keyed), `global_mod_data.bin`
entries (economy/bank mods), `map_visited_server/<user>.zip`, vehicle keys and the
seated vehicle, PVP safety state (in-memory). None of these need to move for an
excursion-style shard; all are username-keyed so they are intact on return.

## 5. The client hop

### 5.1 What survives leaving a server

`GameClient.username / password / serverPassword / steamID` are statics that
`IngameState.exit()` and `MainScreenState.enter()` do not clear (only
`GameClient.client=false`, `MainScreenState.java:372`). `password` is already the
stored hash form, which is what `doConnect(..., doHash=false)` expects. The RakNet
peer is created once and reused (`GameClient.startClient`, `GameClient.java:264-280`).
Storm's `StormTcpChannel` re-handshakes on the new connection by itself.

### 5.2 Vanilla has no redirect

`KickedPacket` carries only strings; there is no Redirect/Reconnect packet type.
The closest thing is the Steam join-request path, which in-game just calls
`getCore():quit()` and relies on the `args.server.connect` System property being
read at the next main menu (`MainScreen.lua:2236-2270`, `LuaManager.java:4115-4134`).
That property is clear-on-read and settable from any Java in the JVM.

### 5.3 Tier 1 hop (no bytecode patch; ships in this mod's jar, runs client-side)

1. Server: `GameServer.sendServerCommand(player, "shard", "hop",
   {host, port, serverPassword, nonce})` (`GameServer.java:3525`).
2. Client Java `@SubscribeEvent` on the `OnServerCommand` bridge event
   (delivered by Storm's `LuaEventManagerPatch`): stash the target in a static,
   then `Core.getInstance().exitToMenu()`.
3. `@SubscribeEvent` on `OnMainMenuEnterEvent` (fires twice per menu — guard):
   arm; on `OnFETickEvent` after a 2-frame delay run the same Lua that
   `STORM/client/LauncherAutoJoin.java:57-83` runs (`DRIVE_POPUP_LUA`) via
   `LuaCompiler.loadstring` + `LuaManager.caller.pcall`, substituting B's
   host/port/password and `GameClient.username/password`, `doHash=false`.
   `LauncherAutoJoin` itself is one-shot (`done` after the first menu) and only
   registered under `-Dstorm.autojoin.file`, so the mod re-implements the driver
   (≈60 lines). A Storm-core `LauncherAutoJoin.armInMemory(...)` seam would be
   cleaner; optional.
4. Everything after that is vanilla: Login → ConnectionDetails → workshop check
   (instant when items are current) → CheckMods → ResetLua("client") → queue →
   GameLoadingState → LoadPlayerProfile → PlayerConnect.

Same mod list on both servers is what makes step 4 cheap and keeps the launcher
from re-syncing. B must not be `Public` (no browser listing) and should carry a
random `ServerPassword` that A hands out in the hop command.

### 5.4 Tier 2 (optional, later): skip the double reload

Both `IngameState.exit()` (reset to `loadMods("default")`, `IngameState.java:965-1068`)
and `ConnectToServerState.Finish` → `Core.ResetLua("client")` are unconditional.
Storm's `StormFastResetLua` lite path elects only when `pristine`
(`STORM/client/StormFastResetLua.java:128-133`), which a hop never is. Making it
hop-aware needs: an `IngameState.exit` advice that keeps the world teardown but
skips the mod/script reload when B's mod list equals the loaded set, plus caching
of `GameClient.checksum` / `NetChecksum.GroupOfFiles` — `finishChecksum` consumes
the MD5 digest (`NetChecksum.java:55-57`), so a skipped Lua pass would hand B
`MD5("")` and get kicked. Two new client patches with re-validation on every PZ
update; only worth it if hop time matters.

Hopping without leaving IngameState is not possible: `ConnectionDetails.parse`
hands the connect state machine to `MainScreenState` and `GameLoadingState`
constructs a new `IsoWorld`.

## 6. Death sync

- Death is server-authoritative: B's `IsoGameCharacter.update` → `die()` →
  `DoDeath` (`IsoGameCharacter.java:2021-2050`); the client only replays a
  `DeadPlayerPacket`. On a Storm server `OnPlayerDeath` fires **server-side** from
  `DoDeath` (`STORM/patch/events/OnDeathTriggerPatch`, server-only), so the mod
  hooks `@SubscribeEvent(OnPlayerDeathEvent)` on B. No packet interception needed.
- B → A: `POST /shard/death {username, nonce, killer, coords}`.
- A: if the player is somehow online on A → `player.setHealth(0)` on the main
  thread (vanilla kills it next tick and writes `isDead` at the next save).
  Otherwise `UPDATE networkPlayers SET isDead=1 WHERE username=? AND world=? AND
  playerIndex=?` + `commit()` on A's `ServerPlayerDB.getInstance().conn`, for every
  playerIndex slot. Then mirror `DoDeath` side effects A cares about: the `user`
  log line, `AnnounceDeath` chat, `DropOffWhiteListAfterDeath` →
  `ServerWorldDatabase.removeUser`. Clear the transfer record (character is gone).
- Precondition that makes this correct: A must not hold a live `IsoPlayer` for
  that username (it doesn't — the player disconnected from A to hop). A's next
  `LoadPlayerProfile` returns `isDead=true` → client goes to character creation →
  `CreatePlayer` overwrites the row. A corpse on A is not reproducible offline
  and is not needed (vanilla produces "dead row, no corpse" whenever the chunk
  unloads).
- Failure mode to close: B dies before delivering the death → A restores from
  `lastSnapshot` (alive) = resurrection. Persist the death on B first
  (`TransferRegistry` state=DEAD_UNDELIVERED) and retry delivery on B restart.

## 7. Supervisor: A starts and runs B

- Launch recipe = `PZ/zombie/network/CoopMaster.launchServer` (`CoopMaster.java:72-127`)
  plus what `STORM/patch/networking/CoopMasterPatch` injects: `<java.home>/bin/java
  -Xmx… -Djava.library.path=… -Djava.class.path=… -javaagent:<storm-bootstrap.jar>
  -Dstorm.server=true -DSTORM_LOG_SOURCE=SERVER [-DstormType=local]
  -Dstorm.http.port=<B port> -DprometheusPort=<B port> -DSTORM_LOG_DIR=<B dir>
  -Dshard.role=shard -Dshard.home=<A url> -Dshard.token=<secret>
  zombie.network.GameServer -servername <A>_<shard> -cachedir=<A cachedir>/shards/<name>
  -port <udp> -udpport <steam> -adminpassword <random>`.
  `CoopMasterPatch.resolveBootstrapJar()` shows how to find the bootstrap jar in
  both local and workshop installs; reuse it.
- Separate `-cachedir` per shard is the clean choice (per-server `server-console.txt`
  and Storm logs otherwise collide). Before first boot the supervisor writes into it:
  `Server/<name>.ini` (copy of A's with `DefaultPort/UDPPort/PublicName` changed,
  `Public=false`, `ServerPassword=<random>`, `Open=false`, identical `Mods=` and
  `WorkshopItems=`, optional different `Map=`), `Server/<name>_SandboxVars.lua`
  (copy or shard-specific), spawn regions if needed, and
  `Saves/Multiplayer/<name>/WorldDictionary.bin` (§4.2).
- Ports: distinct `DefaultPort` (RakNet UDP + Storm game-port TCP), `UDPPort`
  (Steam), Storm HTTP, Prometheus. `-port`/`-udpport` args override the ini in
  memory (`GameServer.java:~607-612`).
- Stdin is B's console (`-coop` mode is not wanted; plain mode tees to
  `server-console.txt`). Read stdout on a thread for the `*** SERVER STARTED ****`
  marker, then poll B's Storm `/health` and the mod's `/shard/ready` (which flips
  only after `ServerPlayerDB` is allowed, i.e. `OnServerStartedEvent`).
- Lifecycle: start at A's `OnServerStartedEvent` (or lazily on first `/shard start`),
  restart on unexpected exit with backoff, `quit` via stdin on A shutdown
  (`OnServerShutdown`-style hook; fallback `destroy` after 30 s — see
  `STORM/src/test/.../liveserver/ServerExtension.java:142-165` for the tested
  pattern). Persist `{pid, ports, cachedir}` so an A restart re-attaches or kills
  an orphan instead of spawning a duplicate.
- Workshop race: both JVMs run the Steam workshop update against the same
  `steamapps/workshop` at boot. Start B only after A's own boot is complete, and
  watch for the "invalid LOC header" ZipException on B; if it bites, give B
  `-Dstorm.workshop.mods` pinned to A's resolved set.
- Both servers must be Steam mode (Steam clients cannot join `-nosteam` servers).
  Steam permits several game-server logins per host on distinct ports.

## 8. Inter-server link

Both JVMs already host `StormHttpServer` (`-Dstorm.http.port`) with an
`@HttpEndpoint` registry (`STORM/http/HttpEndpointDispatcher`; handler = any class
registered with `StormEventDispatcher.registerEventHandler`, method
`void m(HttpRequestEvent[, BodyT])`, Jackson-decoded). Client side: JDK
`java.net.http.HttpClient` (as `STORM/client/StormTcpChannel` does). Endpoints run
on the HTTP thread pool — anything touching `IsoPlayer`, `IsoWorld` or the SPV
queue must hop to the main thread (`StormServerTaskQueue` / an `OnTick` handler)
and reply after completion or via a poll.

Endpoints (both roles): `GET /shard/ready`, `POST /shard/import`,
`GET /shard/export?username`, `POST /shard/death`, `POST /shard/snapshot`,
`GET /shard/dictionary-hash`. Bind A's HTTP to loopback for these or require a
shared bearer token (`-Dshard.token`) — the backend port already hosts `/eval`,
so never expose it beyond the host.

## 9. Mod skeleton (this repo)

```
shard-transfer/
  build.gradle            modInfo { name, description, modVersion, workshopId* }
  media/sandbox-options.txt   ShardTransfer.* knobs (shard map, ports base, snapshot interval, auto-restore)
  media/lua/shared/Translate/EN/Sandbox.json
  src/main/java/com/sentientsimulations/projectzomboid/shardtransfer/
    ShardTransferMod.java         implements ZomboidMod; registers handlers; commands
    ShardRole.java                home | shard from -Dshard.role
    supervisor/ShardSupervisor.java, ShardConfigWriter.java, ShardProcess.java
    link/ShardLink.java (client), ShardEndpoints.java (@HttpEndpoint)
    transfer/CharacterExporter.java, CharacterImporter.java, TransferRegistry.java, DictionaryHash.java
    death/ShardDeathRelay.java    @SubscribeEvent(OnPlayerDeathEvent) on shard; A-side apply
    gate/LoginGateAdvice.java     (server patch, isStormServer-gated) refuse/redirect by registry state
    client/ShardHopClient.java    OnServerCommand + OnMainMenuEnter/OnFETick driver (client JVM only)
    commands/ShardCommand.java    /shard start|stop|status|send <user> [shard]|recall <user>
```

Client/server split inside one jar is runtime-gated (`StormEnv.isStormServer()`),
same as `another-vehicle-claim-system` and `mouse-vehicle-steering`. The only
bytecode patch in v1 is the server-side login gate (needed because the vanilla
login path has no event before the DB row is read). Everything else uses existing
Storm events and endpoints.

## 10. Build order (each step independently testable)

1. Link + registry + `/shard/ready` + dictionary hash. Two hand-started servers.
2. Export/import round trip with `/shard send` on a character that is offline.
   Validate by joining B manually.
3. Client hop (`sendServerCommand` + `ShardHopClient`). Measure hop time.
4. Disconnect-hook export + B login gate (the ordering in §4.5).
5. Death relay + A-side `isDead` write; verify A shows character creation.
6. Return trip (`/shard recall`, A login gate pull).
7. Supervisor (spawn, config seeding, restart, shutdown, orphan re-attach).
8. Snapshots + shard-loss policy.

## 11. Open questions for the owner

- What is the shard for (separate map / instance / load split)? Decides whether
  sidecar state (§4.7) and vehicles must travel, and whether B is long-lived.
- Home-side policy while away: redirect-on-login vs. pull-back (§4.6).
- Auto-restore from snapshot after shard loss: allowed, or always operator-driven?
- Is a ~30 s hop acceptable for v1, or is Tier 2 (§5.4) a launch requirement?

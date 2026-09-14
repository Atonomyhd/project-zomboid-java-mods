# Evidence and source scope

This repair plan draws on a September 13, 2026 audit of an isolated Project Zomboid **42.20.4** test setup with **99 selected mods**, **1,629 Lua files** in selected/common version directories and **11 embedded Lua resources**. The installed Storm distribution was **42.20.4_2.10.1**. Native game bytecode was inspected where Lua delegated faction lookup and rendering to Java.

Historical logs describe different sessions/builds; none is a confirmed capture of the originally reported production admin incident. We did not measure a performance improvement or establish a single cause for disappearing UI, trees, zombies and vehicles. The plan includes source-derived defects, workload patterns to profile, and failure signatures to reproduce. It does not include replacement third-party files, game bytecode dumps, full private logs or deployed configuration.

The source hashes in [source-snapshot.json](source-snapshot.json) identify the reviewed files. `mod-*` paths describe packaged test/Workshop folders, not directories in this repository. Version-specific observations may need updating after a Workshop update.

## Universal Admin Core

The reviewed core is the separate [PR #7 proposal](https://github.com/guspuffygit/project-zomboid-java-mods/pull/7), at source revision `a1013bb`. It is not assumed to be merged or enabled on the production server. It postdates the original admin complaint.

- [AdminTools.lua](https://github.com/Atonomyhd/project-zomboid-java-mods/blob/a1013bb/universal-admin-core/media/lua/client/AdminCore/AdminTools.lua): `drawDatas` at 404 resolves factions and formats account rows before checking viewport clipping; `T.faction` at 66 delegates to vanilla. Installed `ISScrollingListBox:prerender` calls the row renderer for every item. Installed `Faction.getPlayerFaction(String)` loops over factions and member lists. This establishes the off-screen repeated-work issue in UI-01, not its contribution to a production incident.
- [DataAndObserve.lua](https://github.com/Atonomyhd/project-zomboid-java-mods/blob/a1013bb/universal-admin-core/media/lua/client/AdminCore/DataAndObserve.lua): visible-row stats requests are throttled separately; observation renews its camera lease while active and sends a two-second heartbeat.
- [AdminCoreBridge.java](https://github.com/Atonomyhd/project-zomboid-java-mods/blob/a1013bb/universal-admin-core/src/main/java/com/sentientsimulations/projectzomboid/admincore/AdminCoreBridge.java): `follow` at 236 checks the target and sends a nearby teleport when actor/target distance exceeds eight tiles or their integer floors differ. SYNC-08 is a reproduction proposal, not a demonstrated streaming defect.

## AVCS synchronization

The relevant checks were also compared with this repository's fetched upstream revision `0da6c064101fdf811e97e5437516c5dd4dd29f2c`.

- [AVCSClient.lua](../another-vehicle-claim-system/media/lua/client/AVCSClient.lua): `updateClientClaimVehicle` and `updateClientUnclaimVehicle` validate the vehicle index but can dereference a missing player/owner index. In unclaim, one index is modified before the unchecked access to the other. The local full-sync helper sends immediately; several missing-entry paths call it. The batch-coordinate handler already combines missing entries within a single batch.
- `registerClientVehicleSQLID` only applies an identity update if the vehicle is loaded. Later normal synchronization might repair the dropped update; lasting desync has not been established.
- [AVCSUserPermissionPanel.lua](../another-vehicle-claim-system/media/lua/client/UI/AVCSUserPermissionPanel.lua), its client delta handler, and server permission handler were inspected for save acknowledgment/revision handling. The proposed rapid off/on/off scenario requires testing; it is not evidence that all reported permission failures have the same cause.
- [AVCSMapRenderer.lua](../another-vehicle-claim-system/media/lua/client/AVCSMapRenderer.lua) rebuilds detailed metadata for the current player's personal/safehouse/faction claims during map rendering. That is not a full physical-world vehicle scan.

## Other source observations

- [ZoneMarkerEditorMode.lua](../zone-marker/media/lua/client/ZoneMarkerEditorMode.lua) uses category/count shortcuts for list refresh and a count shortcut for its category combo. [ZoneMarkerClient.lua](../zone-marker/media/lua/client/ZoneMarkerClient.lua) increments a version when it replaces cache data. Same-count changes can therefore leave stale editor entries; use revision-based invalidation.
- Chunk List 42 `ChunkLister.lua` registers its player-update callback only while a selection is active. The callback revisits all accumulated boundary coordinates. It highlights floors and does not directly hide every world-object type.
- ATF core 42.14 safehouse synchronization builds full payloads and broadcasts from the fallback even when its compared state is unchanged. Client reconciliation uses display titles. Its location HUD already skips provider evaluation when rounded position is unchanged.
- True Music Radio 42.20 `adjustSounds` has a six-tick cadence for its main sound-cache work. It accesses the local player before a guard. The existing ATF `whereAreYou` typo shim is a different fix and its application is logged in the current test session.
- Lifestyle's object handler scans a 121 x 121 area once on startup and a 17 x 17 area periodically. `LSPerTick.lua` accesses `getModData()` before checking the player. These are all-client paths; neither is inherently admin-only.
- Error Magnifier 42.15 parses new Lua errors, retains unique normalized entries and refreshes an open error window. It does not reparse the full history every tick. Actual error-storm overhead still needs measurement.

## Historical local failures

### Vehicle preview, September 11, 2026

The local console contains **13** matching exception messages beginning at **17:12:59** local time:

```text
java.lang.NullPointerException:
Cannot read the array length because "skinTransforms" is null
UI3DScene$VehicleRenderData.initPartModel
UI3DScene$VehicleRenderData.initPartModels
UI3DScene$VehicleRenderData.initVehicle
UI3DScene$SceneVehicle.renderMain
UI3DScene.render
UIManager.render
```

The stack does not identify the vehicle model or establish which window was open. Installed `UIManager.render` catches exceptions per top-level UI element and continues its loop. It would be inaccurate to claim this stack proves all world rendering aborts.

### Native allocation failure, September 10, 2026

The fatal JVM report at **21:59:30** local time records:

```text
Native memory allocation (malloc) failed to allocate 3617864 bytes.
Error detail: Chunk::new
System-wide physical memory: 32601M (465M free)
TotalPageFile size: 47778M (AvailPageFile size: 10M)
Process working set: 4144M; peak: 4780M
Process private bytes: 6127M; peak: 6127M
```

This confirms the immediate cause of one local crash, not a particular mod leak or production machine state. `Chunk::new` here is a JVM allocation label, not a game-map diagnostic. Investigation must distinguish heap, process-native memory and system commit pressure.

### Vehicle engine parsing, September 8, 2026

The DebugLog at **03:33:25** local time records a null `BaseVehicle.getVehicleSounds()` result while calling `getIgnitionFailSound()`. The stack passes through `BaseVehicle.onEngineStateChanged`, `VehicleEngine.parse`, `VehicleUpdatePacket.parse` and `GameClient.update`.

The installed Storm 2.10.1 JAR contains `VehicleSoundsClientCreatePatch` and `VehicleChunkRehomePatch`. Their presence does not establish successful activation on every client or that the historical issue remains unfixed. Check version and behavior before writing duplicate patches.

## Verification scope

This documentation was checked for item IDs, local link targets, source-path mapping and separation of confirmed observations from hypotheses. No gameplay, packet-handling, resource-management or UI behavior changes are implemented here. Runtime/performance acceptance belongs to each subsequent implementation PR. Use the [test plan](05-test-and-pr-order.md) to capture comparable evidence.

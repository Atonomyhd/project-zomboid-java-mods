# Memory, crashes and repeated errors

## MEM-01 — Repair the vehicle-preview model failure [INVESTIGATE EARLY]

- **Evidence:** a September 11 local console contains 13 `skinTransforms`-null exceptions in `UI3DScene$VehicleRenderData.initPartModel`, reached through `UIManager.render`. The exact preview window/model is not established by the stack. Vanilla catches exceptions per top-level UI element; this is not proof that one exception hides all trees or UI.
- **First action:** reproduce with the specific vehicle model and preview/editor window, retaining its asset/version details. Distinguish missing animation/model data from premature rendering before initialization.
- **Repair once understood:** handle unavailable model/transform data before submitting that preview part, retain a valid fallback, and retry only when its model state changes or through a bounded retry. Correct malformed mod assets at their source when appropriate. Do not suppress every render exception or permanently disable a model for a temporary loading state.
- **Acceptance:** a valid vanilla/mod vehicle renders; a failed/delayed model does not throw each frame; reopening/changing the model recovers. Check rendering state cleanup and resource release.
- **Owner:** Storm if a general vanilla defect; the vehicle mod if its assets are wrong. This has not been attributed to AVCS.
- **Evidence:** [September 11 vehicle-preview signature](evidence.md#historical-local-failures).

## MEM-02 — Identify and fix the owner of native/process memory growth [INVESTIGATE EARLY]

- **Evidence:** the September 10 fatal JVM report confirms native allocation failure with only 10M available pagefile/commit capacity and 6127M process private bytes. It does not identify a leaking mod. `Chunk::new` in that report is a JVM allocation label, not proof of a game map-chunk error.
- **First action:** correlate Windows private bytes/working set/system commit with Java heap and a repeatable action. Compare one client with the server/second-client workload; measure after closing panels, stopping Observe and travelling back. Capture a bounded profiler recording when suitable rather than adding a permanent whole-world scanner.
- **Repair depends on evidence:** release leaked preview textures/emitters/native resources, remove stale strong references, bound a growing cache/queue, or correct test-machine memory pressure. Do not increase heap or force GC on a timer as a general remedy; neither identifies the owner.
- **Acceptance:** repeat the reproducer through multiple cycles; retained memory should stop growing without losing live state. Record system commit headroom and GC behavior. No fabricated FPS/RAM savings target is assigned before baseline measurement.
- **Owner:** whichever component profiling identifies; launcher changes only for a demonstrated launch/resource configuration issue.
- **Evidence:** [crash excerpt](evidence.md#historical-local-failures); Windows watcher in the [Admin Diagnostics proposal](https://github.com/guspuffygit/project-zomboid-java-mods/pull/9), `admin-diagnostics/tools/Watch-AdminClient.ps1`.

## MEM-03 — Bound Error Magnifier's retained data and refresh cost [P2]

- **Evidence:** new Lua errors are parsed on error-count changes, unique normalized error entries are retained, and the open error window refreshes. It processes new entries, not the entire history every tick. It can add work during an error storm; this is not proof that it causes one.
- **Repair:** cap retained unique errors and per-entry size; keep repetition counts; process incoming text in bounded batches and refresh the visible window at a measured cadence. Preserve enough first/last-occurrence evidence to identify the originating error. Fix repeated errors at their source too.
- **Source:** `mod-errorMagnifier/42.15/media/lua/client/errorMagnifier_events.lua:9`; `errorMagnifier_Main.lua:234,333,346`.
- **Acceptance:** synthetic repeated and unique error bursts keep memory/refresh work bounded; counts, selection, timestamps and clearing still work. Native Java exceptions are not guaranteed to appear in the Lua error list.
- **Owner:** original mod or a scoped ATF compatibility patch.

## MEM-04 — Budget Lifestyle object scanning [P2]

- **Evidence:** one-time game-start scan covers 121 x 121 squares; periodic scans cover 17 x 17 squares and inspect each square's objects. The periodic scan already uses elapsed game-time and `GTLSCheck`; it is not a whole-world scan every tick.
- **Repair if timing is significant:** process the initial area through a bounded queue and a per-tick square/time budget; keep a keyed object registry for duplicate checks where it helps. Audit existing object-removal/refresh hooks before adding eviction; stale references have not yet been proven to leak. Avoid retaining squares solely to keep scanning unloaded areas.
- **Source:** `mod-LifestyleHobbies/common/media/lua/client/Properties/Objects/Handler.lua:60,77,124,163`.
- **Acceptance:** same nearby supported objects discovered without a large startup stall; teleport/disconnect cancels stale work; existing emitter cleanup still runs. Compare normal players too, since this is not admin-only.
- **Owner:** original mod or ATF third-party compatibility.

## MEM-05 — Guard player/device references before tick work [P1]

- **Evidence:** confirmed unsafe ordering: Lifestyle `LSAtEveryTick` calls `getModData()` before checking `getPlayer()`; True Music Radio `adjustSounds` calls player position methods without a prior player guard. Some sound-cache device chains also assume objects remain available. Whether each invalid state occurs in an actual callback needs transition tests.
- **Repair:** validate the player and required square/device references before dereferencing; perform module-appropriate cleanup or skip that sample while unavailable. Do not detach a useful hook forever on a temporary missing player. Prevent duplicated callbacks after reload, and avoid a broad exception wrapper that silently hides persistent defects.
- **Source:** Lifestyle `common/media/lua/client/LSEffects/LSPerTick.lua:147-151`; True Music Radio `42.20/media/lua/client/TrueMusicRadio/TMRadio.lua:1432,1547` in their selected mod folders.
- **Acceptance:** missing player, respawn, disconnect, Lua reload, radio transfer/removal and unloaded device. No repeated error flood; normal behavior resumes. Preserve existing music/ATF typo patches.
- **Owner:** original mods or separate compatibility patches in `guspuffy-atf-patches`.

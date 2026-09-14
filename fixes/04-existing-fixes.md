# Existing fixes to verify before rewriting

These are deployment/acceptance checks. Their existence in files is not proof every affected production client loads them. Existing PR references are recorded submission links; check current upstream changes again before implementation.

| ID | Existing work | Evidence/status | Required check |
| --- | --- | --- | --- |
| CHECK-01 | Storm `VehicleSoundsClientCreatePatch` | Class present in installed Storm 2.10.1; historical log has matching vehicle engine parsing NPE. | Verify affected client activation/version; reproduce engine transitions and check for `VehicleUpdatePacket.parse` exceptions. |
| CHECK-02 | Storm `VehicleChunkRehomePatch` | Class present; intended to repair occupied vehicle bookkeeping when its old chunk unloads. | Passenger/driver travel across chunk boundaries, both clients agreeing on location, without disabling vanilla cleanup. |
| CHECK-03 | Extra Logging connection error-loop fix | [PR #8](https://github.com/guspuffygit/project-zomboid-java-mods/pull/8); deployed source removes OnTick before protected logging. The locally fetched upstream main also contains that removal/protection. | Non-Steam/missing-ID login and forced snapshot failure produce bounded logging; confirm exact deployed version before doing further work. |
| CHECK-04 | True Music Radio `whereAreYou` typo shim | Current client log reports `[ATFPatches] TMRadio.whereAreYou container-branch typo shim applied`. | Radio/container transfers stop generating the known repeating error; do not install another wrapper for the same fix. |
| CHECK-05 | Talis New Music server zombie-scan disable | Current test server startup confirms `atf_patches_zombie_scans_disabled`. | Verify that patch remains active after updating New Music, and observe intended music behavior. Do not list the disabled server scan as an active client culprit. |
| CHECK-06 | True Music Radio playlist unicast patch | Selected `guspuffy-atf-patches` source includes `ATFPatches_TMRadioPlaylistUnicast.lua`. | Verify application and targeted response behavior under multiple clients. File presence alone is insufficient. |
| CHECK-07 | Observe heartbeat, camera lease and stop/return corrections | Already in our Universal Admin Core contribution, [PR #7](https://github.com/guspuffygit/project-zomboid-java-mods/pull/7). | Test target switch, timeout, stop, role change and reconnect before another lifecycle rewrite; SYNC-08 concerns remaining movement/streaming behavior. |
| CHECK-08 | AVCS vehicle relocation synchronization | Consolidated contribution in [PR #6](https://github.com/guspuffygit/project-zomboid-java-mods/pull/6), including client/server relocation handling. | Two-client loaded/unloaded source moves, reconnect, and vanilla/modded vehicles. Server success alone does not establish correct client movement. Preserve runtime/persistent identity checks and occupant/tow restrictions. |

Admin Diagnostics [PR #9](https://github.com/guspuffygit/project-zomboid-java-mods/pull/9) is a measurement tool rather than a completed desync fix. Its recorded validation used installed Storm 2.10.1 because the current upstream build's requested 2.10.0 Maven artifact was unresolved. In-game recording and high-population overhead remain acceptance items.

The earlier claim-window resizing and invitation search work are not listed as performance repairs: correcting layout/search usability does not establish a fix for admin world disappearance.

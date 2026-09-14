# UI and client performance

## UI-01 — Skip off-screen account rendering and cache faction data [P1]

- **Evidence:** confirmed code issue in our new User List. `ISScrollingListBox:prerender` calls each row renderer; our renderer resolves faction membership and creates/formats row data before checking the viewport. Vanilla `Faction.getPlayerFaction(String)` scans factions and members.
- **Repair:** return early for off-screen rows before lookups/allocations; maintain a username-to-faction lookup with invalidation when membership changes; cache formatted cells until their inputs change. Keep sorting and scroll heights correct.
- **Expected benefit:** less admin-window CPU work and allocation as the account/faction lists grow. This is not an entity-visibility fix by itself.
- **Source:** `mod-universal-admin-core-b42/42/media/lua/client/AdminCore/AdminTools.lua:66,404`; vanilla `ISUI/ISScrollingListBox.lua:518`; [installed-bytecode observation](evidence.md#universal-admin-core).
- **Acceptance:** compare a large account directory with only a few visible rows. Instrument faction lookups per draw. Off-screen rows should perform no faction lookup or formatting; membership updates and scrolling must remain correct.
- **Owner:** Universal Admin Core. This is a follow-up to our overhaul, not a cause established for earlier incidents.

## UI-02 — Bound Chunk List selection work [P1]

- **Evidence:** confirmed growing-work pattern. Begin List adds boundary coordinates for every visited chunk, searches the existing coordinate array to avoid duplicates, and rechecks all accumulated coordinates on each player-update callback. Stop removes the hook; the coordinate/dump tables remain until a new selection resets them.
- **Repair:** store coordinate membership in a keyed set; keep saved selection data separate from currently highlighted nearby coordinates; limit work per update and avoid looking up distant unloaded squares. Release transient highlighting data after cancel/export when no longer needed. Preserve the full requested chunk list for export.
- **Expected benefit:** prevent increasingly expensive highlighting during long admin travel/teleport sessions and reduce unnecessary retained transient data.
- **Source:** `mod-ChunkList/42/media/lua/client/ChunkLister.lua:91,123,192,203,251`.
- **Acceptance:** travel through many chunks with selection active; callback time should stay bounded by nearby highlights/work budget. Exported coordinates must still match the complete selection. Cancel/restart must leave one active hook at most.
- **Owner:** original Chunk List maintainer or an ATF compatibility patch. Confirm the allowed distribution approach before publishing a replacement third-party file.

## UI-03 — Cache AVCS map records [P2]

- **Evidence:** repeated work confirmed, impact not measured. The map renderer recreates per-vehicle tables and translated names each draw for personal, safehouse and faction claims.
- **Repair:** cache the relevant vehicle IDs/display data; invalidate on claim, permission, membership, locale and coordinate changes as appropriate. Keep coordinate updates cheap and separate from metadata reconstruction. Cull labels outside the map viewport before expensive drawing.
- **Source:** `another-vehicle-claim-system-b42/42/media/lua/client/AVCSMapRenderer.lua:17,216`; incoming changes in `AVCSClient.lua`.
- **Acceptance:** a static open map should not rebuild all metadata every frame. New claims, moved vehicles and membership changes must appear immediately after the corresponding update. Compare allocation and map render time.
- **Owner:** AVCS. Scope is relevant group claims, not all physical server vehicles.

## UI-04 — Reduce Zone Marker overlay traversal/drawing [P2]

- **Evidence:** repeated outer traversal of enabled categories/zones on each map draw. Existing drawing checks do not remove that outer traversal.
- **Repair:** cache enabled zone metadata using `ZoneMarkerCache.version`; reject world bounds outside the viewport before label work. Introduce a coarse spatial lookup only if measured zone counts justify it; screen-coordinate caches must be invalidated by pan/zoom/resize.
- **Source:** `mod-zone-marker-b42/42/media/lua/client/ZoneMarkerRenderer.lua:156`; `ZoneMarkerClient.lua:54`.
- **Acceptance:** compare render time with many off-screen zones. Zone changes, renamed categories, filter toggles and map movement must remain correct. Pair with SYNC-07 for editor correctness.
- **Owner:** Zone Marker.

## UI-05 — Reduce safehouse HUD work and logging bursts [P2]

- **Evidence:** position changes trigger linear safehouse checks; safehouse-change events rebuild the list and print a line per entry. Stationary player updates already have a position-cache guard.
- **Repair:** debounce repeated change notifications, rebuild once per data revision, and replace per-safehouse routine printing with a summary/debug option. Measure a coarse spatial lookup for membership tests if the list is large; do not delay authoritative PvP/safety decisions to optimize HUD text.
- **Source:** `mod-after_the_fall_core_b42/42.14/media/lua/client/ATF_SafetySystem/ATF_AutomaticSafehouseSafety.lua:118,159,228`; `ATF_LocationHUD.lua:83`.
- **Acceptance:** move across safehouse/zone boundaries while changing claims; HUD and safety transitions remain timely. A single sync burst should produce one cache rebuild/summary, not redundant whole-list logging.
- **Owner:** ATF core/ATF compatibility. Runs for ordinary clients too.

## UI-06 — Avoid unnecessary Admin Powers rebuilds [INVESTIGATE]

- **Evidence:** vanilla `RefreshCheats` refreshes the powers panel when an instance exists. Storm preserves pending checkbox edits but still calls the original rebuild. Busy-server frequency and cost have not been measured.
- **Repair if measurements justify it:** coalesce redundant refreshes; avoid rebuilding when the relevant local role/power state has not changed. Preserve permission revocations and external changes, and refresh on reopening the panel.
- **Source:** vanilla `ISUI/AdminPanel/ISAdminPanelUI.lua:431`; installed Storm resource `lua/client/ISUI/AdminPanel/StormAdminPowerUIFix.lua:44`.
- **Acceptance:** simulate other players' state updates and a local role change. Pending edits must survive irrelevant updates; local capability changes must remain accurate. Do not add polling when events suffice.
- **Owner:** Storm for a reusable vanilla UI/performance correction. This is not proof of admin throttling.

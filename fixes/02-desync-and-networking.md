# Desync and networking

## SYNC-01 — Recover safely from partially initialized AVCS caches [P1]

- **Evidence:** confirmed unsafe path. Claim/unclaim handlers check the vehicle index but then dereference the player/owner index without validating it. Unclaim removes the vehicle record before the unchecked owner-index write, so failure can leave a half-applied update. Full indexes arrive separately.
- **Repair:** validate message shape and both indexes before mutation; apply snapshots atomically when possible. Make duplicate removals safe. Distinguish unknown/loading state from confirmed unclaimed, retain server authorization and request bounded recovery rather than enabling actions based on missing data.
- **Source:** `another-vehicle-claim-system-b42/42/media/lua/client/AVCSClient.lua:18,46`; access lookup in `shared/AVCSShared.lua`. Also present in the locally fetched upstream `main` snapshot at `0da6c06`.
- **Acceptance:** deliver updates before either snapshot, between snapshots, duplicated and with a missing owner record. No exceptions or partially updated indexes; both clients eventually agree with the server. Unknown claim state must not weaken permissions.
- **Owner:** AVCS. Combine with SYNC-02.

## SYNC-02 — Coalesce full-resync requests [P1]

- **Evidence:** the client `requestFullSync` helper sends immediately; several missing-data paths call it. Repeated single updates or repeated batches can request repeated complete snapshots. A batch already coalesces its own missing rows into one request, but calls across messages are not coalesced there. Server/transport limits must be checked before estimating actual amplification.
- **Repair:** one in-flight request per client, bounded retry/backoff with a timeout, and reset on a complete canonical response/disconnect. Consider targeted record recovery for known IDs after measuring full-snapshot size. Add server-side per-client coalescing where needed.
- **Source:** `AVCSClient.lua:14,63,82,104,118`; server `requestFullSync` dispatch in `AVCSServer.lua`.
- **Acceptance:** replay many unknown-ID updates, lose a reply, then restore delivery. Request count stays bounded and recovery still completes; the throttle must not permanently strand the client.
- **Owner:** AVCS. Last-known map coordinates are separate from physical vehicle interpolation.

## SYNC-03 — Add acknowledged, ordered permission saves [P2]

- **Evidence:** source-derived race candidate from the earlier permission review. The inspected UI compares changes to its cached claim, sends a save and closes; there is no explicit save acknowledgment/revision in that flow. Off -> on -> off before the first update arrives can compare the second edit against stale data. This scenario still needs reproduction against the exact implementation being changed.
- **Repair:** save request IDs, canonical server replies, monotonically ordered claim revisions, and pending/saved/error UI states. Ignore stale replies. Keep authorization and Boolean field allowlisting server-side. Version/negotiate protocol additions for older clients.
- **Source:** `client/UI/AVCSUserPermissionPanel.lua` confirmation handler; `AVCSClient.lua:118`; server permission-update handler; [source observations](evidence.md#avcs-synchronization).
- **Acceptance:** rapid toggles, delayed/duplicate replies, denied saves, disconnect/rejoin, two editors and old-client compatibility. A rejected save must never look successfully persisted.
- **Owner:** AVCS. Treat permissions policy and delivery correctness as separate concerns.

## SYNC-04 — Recover claim identity updates received while unloaded [P2]

- **Evidence:** `registerClientVehicleSQLID` only applies an identity update if `getVehicleById` already finds the vehicle. Otherwise it drops the update. Later normal synchronization may repair it; an actual lasting mismatch is not yet reproduced.
- **Repair:** bounded, expiring reconciliation for not-yet-loaded vehicles, or fetch canonical identity when they load. Protect against runtime-ID reuse using an authoritative persistent identity/generation; never apply an old claim imprint to a different vehicle. Clear pending state on disconnect.
- **Source:** `AVCSClient.lua:139-143`; canonical identity lookup in AVCS shared/Java code.
- **Acceptance:** receive the imprint before loading the vehicle, then load it; unload/reload and reuse a runtime ID. Correct identity appears and stale updates are rejected. Queue size remains bounded.
- **Owner:** AVCS. Do not force-load the world to repair an imprint.

## SYNC-05 — Send safehouse updates only when data changes [P1]

- **Evidence:** confirmed full-broadcast pattern. `diffAndUpdate` broadcasts the complete payload even when the compared state has not changed. The fallback is checked by `EveryOneMinute` with a 55-second real-time guard. Payload/recipient count can become large.
- **Repair:** compute a version/digest over all meaningful synchronized fields; suppress unchanged broadcasts. Keep an initial full snapshot and explicit recovery; add deltas only with sequencing and missed-update recovery. Never base a no-change decision only on titles/counts if ownership, geometry or membership can change.
- **Source:** `mod-after_the_fall_core_b42/42.14/media/lua/server/ATF_SafetySystem/ATF_SafehouseGuard_Server.lua:51,70,129,151`; client counterpart:101.
- **Acceptance:** idle server produces no redundant full updates; changes to every supported field synchronize. Joining/rejoining clients still receive complete state. Measure bytes and dispatch time with hundreds of safehouses and multiple clients.
- **Owner:** ATF core/`guspuffy-atf-patches` as appropriate. All-client network work, not established admin-only throttling.

## SYNC-06 — Use stable safehouse identity and preserve canonical metadata [P2]

- **Evidence:** current reconciliation builds lookups by display title, removes missing titles and adds new ones. Existing same-title entries are treated as matching. Duplicate/renamed titles and metadata changes are correctness risks, not a proven explanation of the earlier offline-invite incident.
- **Repair:** agree a stable server identity and version; reconcile ownership, members, geometry and title from authoritative state without unnecessary remove/recreate cycles. Handle legacy data and native chat/membership synchronization. Do not apply destructive client-side reconciliation from an incomplete snapshot.
- **Source:** `ATF_SafehouseGuard_Client.lua:103-138`; server payload builder at `ATF_SafehouseGuard_Server.lua:51`.
- **Acceptance:** duplicate titles, rename, ownership transfer, offline member changes, partial/out-of-order snapshot and reconnect. The safehouse retains correct identity, membership and permissions on both clients.
- **Owner:** ATF core/ATF compatibility. Coordinate with SYNC-05; this is more invasive than suppressing an unchanged send.

## SYNC-07 — Refresh the zone editor by cache revision [P1]

- **Evidence:** confirmed stale-cache condition. The client increments `ZoneMarkerCache.version` when replacing data, while `refreshZoneList` checks category/count and `fillCategoryCombo` checks count. A rename/edit or remove/add with unchanged count can retain stale UI entries.
- **Repair:** use cache version plus category identity for invalidation; preserve selection by a stable server ID. If the protocol lacks an ID, introduce one with migration or clear/re-resolve selection safely. Keep mutation targets tied to current canonical data.
- **Source:** `mod-zone-marker-b42/42/media/lua/client/ZoneMarkerEditorMode.lua:266` and `fillCategoryCombo`; `ZoneMarkerClient.lua:54`.
- **Acceptance:** replace data without changing list size; rename a category; remove the selected zone. Display updates, selection is valid, and an edit cannot target an old object.
- **Owner:** Zone Marker. This fixes stale administration data, not physical player/vehicle replication.

## SYNC-08 — Investigate Observe movement and camera coordination [INVESTIGATE]

- **Evidence:** current Observe code renews a camera lease each tick, sends a two-second heartbeat and checks whether to teleport the admin near a moving target every two seconds. It already guards inactive sessions and invalid targets. No recording proves repeated relocations caused the general disappearing-world complaint.
- **Repair if reproduced:** allow one authoritative relocation in flight, observe completion/position before resending, and use distance hysteresis plus a bounded timeout. Bind any acknowledgment to the server-authorized session; do not trust client-provided destination claims. Keep a safe local-camera fallback while the target/square is unavailable. Preserve stop, return, role-loss and reconnect behavior.
- **Source:** Universal Admin Core `DataAndObserve.lua:131`, `AdminCoreServer.lua:227`; `AdminCoreBridge.java:236`, `AdminCoreObserveCamera.java:25` in the [Universal Admin Core proposal](evidence.md#universal-admin-core).
- **Acceptance:** target walking/driving, floor change, long teleport, packet delay, switch target, stop/escape, disconnect and powers revoked. Track actor and camera separately. Existing lease/stop fixes should be retained.
- **Owner:** Universal Admin Core. This feature postdates the original complaint.

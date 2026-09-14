# Implementation, test and PR order

## Suggested work packages

1. **Universal Admin Core — UI-01.** Small rendering fix with a direct workload reduction. Preserve the existing core branch/package first; benchmark a large account list and faction changes.
2. **AVCS — SYNC-01 + SYNC-02.** Repair partial-index handling and recovery traffic together. Use a message-order/recovery harness and two-client acceptance. Follow separately with SYNC-03/04 because they can change protocol behavior.
3. **Zone Marker — SYNC-07.** Correct same-count stale state and selection before adding overlay optimization UI-04.
4. **Chunk List compatibility — UI-02.** Separate implementation/PR from the reusable core. Keep full export semantics while bounding display work.
5. **ATF safehouses — SYNC-05 + UI-05.** Start with safe suppression of unchanged payloads and redundant cache/log work. Treat stable identity/membership migration SYNC-06 as a separate reviewed change with backups.
6. **Third-party error guards — MEM-05.** Small per-mod fixes with transition tests. Keep the patches independent so one can be rolled back without removing the others.
7. **Measured follow-ups — UI-03/04/06, MEM-03/04.** Implement when a baseline demonstrates meaningful overhead. Preserve cache invalidation, update cadence and cleanup semantics.
8. **Incident-driven work — SYNC-08, MEM-01/02.** Capture Observe/preview/native-memory reproductions early; choose the repair from that evidence. These can be investigated while the smaller code fixes proceed.

## Placement consistent with the developer's repositories

- Reusable administration features/fixes belong in `universal-admin-core`.
- Vehicle claim behavior belongs in `another-vehicle-claim-system`; zone editor/overlay fixes belong in `zone-marker`.
- Vanilla renderer/vehicle engine defects belong in Storm when reproduced and corrected generally. Keep opinionated features out of the framework or behind an appropriate opt-in flag.
- ATF-specific behavior and compatibility fixes for third-party mods belong in `guspuffy-atf-patches`, or the original mod repository if its maintainer prefers. Do not move ATF-specific ownership/PvP assumptions into a universal core.
- Native-memory findings belong to the measured resource owner; there is no generic "memory fix" module planned.

Fetch current upstream before each work package; historical PR snapshots are not a guarantee that a change is still needed. Use an isolated development branch/worktree. Retain previous source, Git history and a hash-verified package before edits. Use fresh deployment packages with rollback instead of modifying hard-linked runtime files.

## Meaningful validation

**Performance:** measure time spent in the target callback/render function, allocations/GC, request count and payload bytes before/after the same scenario. Include enough accounts, factions, safehouses, claims or selected chunks to exercise the scaling issue. A faster average FPS alone does not establish that desync is repaired.

**Correctness:** test joins, reconnects, empty and partially initialized state, duplicated/delayed/out-of-order updates where applicable, role changes, invalid selections and mod reload. Caches must invalidate correctly; retries/queues must be bounded without losing eventual recovery. Preserve server authority and existing permissions. Do not force-load areas or delete entities to make counters smaller.

**In-world:** compare an admin and ordinary player at the same place/time. Begin with suspect panels closed, Observe off and Chunk List selection cancelled. Enable one path at a time. For vehicles, compare both clients, driving/passenger state, loaded/unloaded source areas and a reconnect.

**Incident capture:** on the admin client, Ctrl+Shift+F10 starts diagnostics and Ctrl+Shift+F11 marks failure. Keep recording at least 20 seconds after the marker, then stop. Use the Windows watcher for private bytes/system commit and an unresponsive process. Keep relevant client console/DebugLog/fatal report. Record exact window, action, coordinates, player count and loaded client versions. The recorder does not measure which function is expensive or count rendered trees.

**Disappearance diagnosis:** steady loaded counts with an empty screen suggests checking renderer/camera/UI state; falling counts after movement suggests checking loading/replication. These are investigation directions, not automatic diagnoses. A near-exhausted system commit limit calls for native/process memory investigation alongside heap metrics.

Publish small per-module PRs with the actual before/after behavior, measured results and remaining acceptance items. Preserve existing work and avoid promising that every crash or desync shares the same cause.

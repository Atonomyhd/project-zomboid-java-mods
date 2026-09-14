# Performance and desync fixes

Prepared September 13, 2026. **19 repair/investigation items**, grouped by subsystem, plus **8 existing fixes to verify** before duplicating work. Implementation status: **pending**. This folder contains the requested fix list and acceptance criteria.

Start with [FIX-LIST.txt](FIX-LIST.txt). The detailed groups are:

| File | Contents |
| --- | --- |
| [01-ui-performance.md](01-ui-performance.md) | User List, Chunk List, map overlays, safehouse HUD, Admin Powers refresh |
| [02-desync-and-networking.md](02-desync-and-networking.md) | AVCS synchronization, safehouse synchronization, zone-editor stale data, Observe |
| [03-memory-and-crashes.md](03-memory-and-crashes.md) | Vehicle preview exceptions, native memory pressure, error handling, background scans |
| [04-existing-fixes.md](04-existing-fixes.md) | Storm/ATF/previous contribution fixes that already exist locally |
| [05-test-and-pr-order.md](05-test-and-pr-order.md) | Suggested work order, measurements, rollback and repository placement |
| [evidence.md](evidence.md) | Source observations and selected historical failure signatures |
| [source-snapshot.json](source-snapshot.json) | Audited versions, packaged source names and reference hashes |

**Best first code changes:** skip off-screen User List work; repair AVCS partial-cache handling and coalesce resync requests; use zone-cache revisions in the editor; bound Chunk List highlighting; avoid unchanged full safehouse broadcasts; guard missing player/device references in tick callbacks.

The source audit identifies code problems and workload patterns. It does not prove these cause every production incident or promise a particular FPS improvement. Items explicitly marked **investigate first** need a recording or reproduction before selecting a repair. Our User List and Observe changes postdate the original admin complaint.

The foundation is the [source observations and historical failure evidence](evidence.md). AVCS delta handling and the zone editor were rechecked while preparing this list. PR status references describe recorded submissions, not a fresh query of GitHub review/merge status. Full private logs and local-only development reports are excluded.

Source names beginning with `mod-` refer to the packaged Workshop/test mod folders recorded in `source-snapshot.json`; they are not paths within this repository. Module-owned files map to the corresponding module's `media/lua` directory. Vanilla paths are relative to the installed game's `media/lua/client`. [Evidence notes](evidence.md) link the relevant repository sources and separate our unmerged Universal Admin Core proposal from existing server mods. Use a source checkout and retain the current source/package before testing rather than editing a deployed snapshot directly.

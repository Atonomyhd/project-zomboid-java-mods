# Implemented code and pull requests

Updated September 14, 2026. The original 19-item list is retained as an audit and
acceptance plan. Code now addresses 18 items; **MEM-02, the owner of native/process
RAM growth, remains unconfirmed**. Automated checks do not prove the cause of the
production admin visibility incidents or establish a measured FPS/RAM improvement.

| Pull request | Actual changes |
| --- | --- |
| [Universal Admin Core #7](https://github.com/guspuffygit/project-zomboid-java-mods/pull/7) | UI-01: skip hidden rows, cap/cache faction names and formatted cells. SYNC-08: wait for outstanding observation relocations while retaining permission and lease checks. Includes the earlier control center, invite search and offline-membership fixes. |
| [AVCS #6](https://github.com/guspuffygit/project-zomboid-java-mods/pull/6) | UI-03: cache/deduplicate map records and label widths, cull offscreen labels. SYNC-01/02: atomic claim indexes and bounded recovery. SYNC-03: acknowledged permission saves. SYNC-04: native vehicle-part identity synchronization. Includes earlier resize, permission and Untow changes. |
| [Zone Marker #11](https://github.com/guspuffygit/project-zomboid-java-mods/pull/11) | UI-04: cache enabled overlays and label widths, cull current viewport bounds. SYNC-07: refresh editor/options on revisions, including same-count changes. |
| [ATF compatibility #12](https://github.com/guspuffygit/project-zomboid-java-mods/pull/12) | UI-02: bounded nearby Chunk List preview with full export. UI-05: indexed safehouse checks without per-house log bursts. MEM-05: missing-player guards. Source patches also address SYNC-05/06 safehouse broadcasts/identity, MEM-03 bounded Error Magnifier, MEM-04 budgeted Lifestyle scan and MEM-05 stale radio devices. |
| [Storm #9](https://github.com/guspuffygit/project-zomboid-storm/pull/9) | UI-06: skip unchanged Admin Powers widget rebuilds. MEM-01: return pooled preview data and skip the current part when skin transforms are unavailable; later frames recover. |

These PRs are open and ready for maintainer review. Java-mod changes are integrated
with main `0da6c064101fdf811e97e5437516c5dd4dd29f2c`; Storm uses main
`1bfffb84957537e4116a14919b86fb5587434604`. The documentation PR #10 remains an
index/plan, with the implementation in the module PRs above.

## What needs source integration

The ATF Patches module automatically loads the Chunk List, safehouse HUD and tick
compatibility hooks when their target mods are present. Its jar alone does **not**
apply the five vendor Lua source changes. Those patches target specific ATF Core,
Lifestyle, Error Magnifier and True Music Radio releases; the PR includes a hash-checked
applicator that preserves originals. Apply/distribute matching ATF server and client
files together. Original third-party mods are not republished in the PR.

Unusual AVCS vehicles lacking the configured mule part retain a guarded legacy
loaded-only identity hint. Explicit client physics resynchronization and the optional
Observe camera require client Storm/Java; ordinary Lua interfaces do not.

## Validation and remaining work

- Complete Universal Admin Core Lua suite: 810 assertions; all three Java tests pass.
- AVCS: complete management, layout, resize, warp and synchronization Lua suites;
  all 32 Java tests pass.
- Ten targeted Lua fixtures pass in both Lua 5.1 and installed B42.20.4 Kahlua,
  including actual patched vendor sources. All four vendor patch sets reproduce the
  expected five resulting file hashes.
- ATF module: existing Java test and jar build pass. Zone module: compile/jar pass
  (no Java test sources). Storm: two targeted Java tests and jar build pass; its full
  unrelated test suite was not run. Module formatting checks pass; changed Storm
  sources were formatted.
- Java-mod validation used an external installed-Storm-2.10.1 fixture because the
  upstream Maven 2.10.0 coordinate was unavailable, and excluded JaCoCo report
  generation. No dependency substitution is committed.

In-game visual checks, reconnect, two-client observation/vehicle/safehouse behavior
and high-population performance measurements remain pending. MEM-02 needs a repeatable
incident correlated with diagnostics/process memory before assigning a responsible
component. None of these PRs has been deployed to the live server.

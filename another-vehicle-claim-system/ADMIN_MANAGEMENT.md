# ATF vehicle management

Imported from the existing AVCS contribution with author attribution retained.
Vehicle use permissions still honor configured faction/safehouse access. Only the
owner or admin may unclaim or edit public permissions. Unknown permission fields
or non-boolean values reject the whole request without partially changing the claim.

Clients keep their last usable cache during refresh. V3 delivers one snapshot;
the owner display index is rebuilt from the claim records. Automatic retries stop
after three attempts. Reopen a vehicle manager to retry after exhaustion. Server
authorization remains authoritative. Older paired responses remain supported.

The refresh limiter uses expiring, bounded username entries, never IsoPlayer keys.
Map caching/culling, admin untow, client index corrections and teleport cancellation
on disconnect or role loss are retained. Teleport uses the existing native update
path; no additional client warp helper or part-identity replication is installed.

Both vehicle managers remain resizable. Geometry is saved on close and restored on
reopen, clamped to screen bounds. The native layout.ini persists it across sessions.

Set gameDir in local.properties. Run ./gradlew :another-vehicle-claim-system:test
:another-vehicle-claim-system:luaKahluaTest :another-vehicle-claim-system:spotlessCheck.
Use gradlew.bat on Windows. Install Lua 5.1 or supply -PluaExecutable.

Before merging, test owner/guest/faction/safehouse roles on two clients, public
permission toggles, reconnect and missing-snapshot recovery, map updates, untow,
teleport cancellation, and window resize/reopen at multiple UI scales. The local
test runtime differs from the destination repository's declared Storm version;
fallback build results are reported separately. No production acceptance is claimed.

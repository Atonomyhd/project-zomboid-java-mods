# Universal Admin Core

Project Zomboid Build 42 administration module. Requires compatible Storm on server
and client. This module does not require the ATF Economy module.

The Admin Panel provides searchable categories and native actions. The User List
provides selected-player administration, membership tools and observation. Ordinary
safehouse/faction invitation searches retain native permissions. Visible-row caching
limits repeated user-list work. The admin can also use Add Item on their own inventory.

Drag the visible bottom-right Inventory-style grip to resize supported windows.
Drag the header to move them. Closing a window stores
its geometry immediately; reopening restores it, clamped to the current screen.
The native layout.ini provides persistence across client sessions.

Press **Ctrl+A** while not typing to open the panel with search focused. Type to
search, use **Up/Down** to select and **Enter** to open; **Escape** closes it.
Ctrl+A keeps its normal select-all behavior inside text fields.

Click groups to expand or collapse their nested tools; up/down carets show their state.
Make Noise holds the radius choices under one entry. Search finds tools inside closed
groups. Unavailable debug entries and empty groups are hidden.

Search includes Brush Tool, tile picker, loot tools, animation tools, weather,
noise, ramps, animal tools, foraging and native debug panels. Original role permissions still apply; developer
panels also require the game's Debug mode. Actions such as global zombie removal
ask for confirmation. Tools needing a specific door, vehicle, tile or other world
object remain in the original right-click menus. Search for the object name to
find the targeting guidance.

Observe requires server-validated capabilities, invisibility, god mode and noclip.
It follows a streamed target from the admin's client; it is not the target's screen.
Stop/Esc and lease expiration return to the server-recorded origin. A camera-selection
exception disables observation locally and returns the original camera character.

## Extension contract

Register administration actions through AdminCore/Registry.lua. Keep authorization
on the server; UI visibility alone is insufficient. Java bridge membership methods
check capability before querying accounts. Production Java logs use SLF4J.

## Validation

Set gameDir in local.properties. Run ./gradlew :universal-admin-core:test
:universal-admin-core:luaKahluaTest :universal-admin-core:spotlessCheck.
On Windows use gradlew.bat. Lua 5.1 must be installed; override with
-PluaExecutable=/path/to/lua5.1. Tests and Lua formatting are integrated into Gradle.
For a Workbench environment, use My Mods -> Build, Check & Install. This packages
the selected module and updates the server and clients together.

## Integration limits

Workbench validates against its selected game and Storm runtime; this can differ
from the repository's default dependency versions.
UI hooks depend on the supported vanilla Lua layout and camera method signatures.
Fixtures and bytecode weaving checks do not replace an in-game two-client session.
Verify observe start/stop/error recovery, role revocation, membership updates, resize
dragging, long names, different UI scales and close/reopen before merging.

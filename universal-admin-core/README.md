# Universal Admin Core

Project Zomboid Build 42 administration tools. Requires compatible Storm on the
server and client. ATF Economy is optional.

## Administration

The panel uses Dashboard, World, Zones and Server. Search finds permitted tools
inside collapsed groups. Small windows reflow and paginate; the mouse wheel and
Previous/Next reach the remaining actions. Ctrl+A opens search when not typing.

Compact Admin provides personal pinned actions. Right-click an action to pin it.
Grouped Admin Powers support search and server-checked changes. Unavailable debug
tools are hidden; developer tools require Debug mode.

Safehouse and faction lists have name/owner search and alphabetical sorting.
Faction search includes tags. Safehouse placement previews overlap and nearby
claims; the server checks placement again. Membership actions keep native
permissions. Tickets is removed from the User Panel, search and Compact Admin.

Authorized admins can expand or redraw an existing safehouse from its detail
window, with overlap feedback and recent audit history. Owner, members and
existing respawn settings stay attached to the same claim. See
[boundary editing and integration requirements](docs/SAFEHOUSE-BOUNDARY.md).

Drag a header to move a window and its bottom-right grip to resize it. Geometry
and pins persist in the native client layout file.

## Extension contract

Register actions through AdminCore/Registry.lua. Check authorization on the server.
Action metadata controls navigation, search and order without granting permission.
Server layout editing remains future work.

## Validation

Run :universal-admin-core:devCheck with the supported PZ installation. Lua fixtures
run in Lua 5.1 and the game's Kahlua interpreter; Java tests check native contracts.

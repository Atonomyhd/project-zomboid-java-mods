--
-- SurvivorSkillObeliskInventionSync.lua
-- Mirrors the Lifestyles mod's invention-research table (invData) from the
-- client to the server-side player modData.
--
-- Lifestyles finishes research and production in client Lua (LSIWAction.lua)
-- and only pushes its client->server modData mirror (LS:SavePlayerData) on an
-- Inventing level-up. Everything researched since then doesn't exist on the
-- server, so the obelisk death snapshot never sees it. This script watches
-- invData and pushes it to SyncInventionsHandler within a game minute of any
-- change.
--

local MODULE = "SurvivorSkillObelisk"
local SYNC_COMMAND = "syncInventions"
local MAX_DEPTH = 12

-- State lives on a global so a hot-reload of this file keeps the signature and
-- doesn't stack a second set of event handlers.
SurvivorSkillObeliskInventionSync = SurvivorSkillObeliskInventionSync or {}
local InventionSync = SurvivorSkillObeliskInventionSync

-- The workbench handle and cached material costs churn on every menu open
-- without any progress changing, so they stay out of the signature.
local function isTransientKey(key, depth)
    return (depth == 0 and key == "lsWkID") or (depth == 1 and key == "workCost")
end

local function sortKeys(a, b)
    return tostring(a) < tostring(b)
end

local function serialize(value, depth, parts)
    if type(value) ~= "table" then
        parts[#parts + 1] = tostring(value)
        return
    end
    if depth > MAX_DEPTH then
        parts[#parts + 1] = "..."
        return
    end
    local keys = {}
    for key in pairs(value) do
        if not isTransientKey(key, depth) then
            keys[#keys + 1] = key
        end
    end
    table.sort(keys, sortKeys)
    parts[#parts + 1] = "{"
    for i = 1, #keys do
        local key = keys[i]
        parts[#parts + 1] = tostring(key) .. "="
        serialize(value[key], depth + 1, parts)
        parts[#parts + 1] = ","
    end
    parts[#parts + 1] = "}"
end

local function computeSignature(invData)
    local parts = {}
    serialize(invData, 0, parts)
    return table.concat(parts)
end

function InventionSync.sync()
    if not isClient() then
        return
    end
    local player = getPlayer()
    if player == nil or player:isDead() then
        return
    end
    local modData = player:getModData()
    if modData == nil or type(modData.invData) ~= "table" then
        return
    end
    local signature = computeSignature(modData.invData)
    if signature == InventionSync.lastSignature then
        return
    end
    sendClientCommand(player, MODULE, SYNC_COMMAND, { inventions = modData.invData })
    InventionSync.lastSignature = signature
end

if not InventionSync.registered then
    InventionSync.registered = true
    Events.EveryOneMinute.Add(function()
        InventionSync.sync()
    end)
    -- Push afresh after every world join: the server may have restarted (or its
    -- mirror rolled back) while this client session kept its last signature.
    Events.OnGameStart.Add(function()
        InventionSync.lastSignature = nil
    end)
end

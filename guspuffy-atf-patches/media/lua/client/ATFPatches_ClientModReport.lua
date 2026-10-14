if not isClient() then
    return
end

local MODULE = "ATFPatchesClientMods"

-- The Project Remnants NPC agent (NPCFW) injects its globals a few seconds after the
-- Lua environment appears, so the report waits this many ticks for something to show up.
local MAX_TICKS = 600

local function joinJavaList(list)
    local ids = {}
    if list then
        for i = 0, list:size() - 1 do
            table.insert(ids, tostring(list:get(i)))
        end
    end
    return table.concat(ids, ",")
end

local function loadedMods()
    local ok, mods = pcall(getActivatedMods)
    return ok and joinJavaList(mods) or ""
end

local function menuMods()
    local ok, mods = pcall(function()
        return ActiveMods.getById("default"):getMods()
    end)
    return ok and joinJavaList(mods) or ""
end

local function zombieBuddyMods(ids)
    if type(ZombieBuddy) ~= "table" or not ZombieBuddy.getActiveJavaMods then
        return
    end
    local ok, mods = pcall(ZombieBuddy.getActiveJavaMods)
    if not ok or mods == nil then
        return
    end
    for id in pairs(mods) do
        table.insert(ids, tostring(id))
    end
end

-- NPCFW registers every npcfw* bridge method as a Lua global, in multiplayer too.
local function projectRemnantsPresent()
    return npcfwIsPossessing ~= nil or npcfwGetNPC ~= nil or npcfwShutdown ~= nil
end

local function javaMods()
    local ids = {}
    zombieBuddyMods(ids)
    if projectRemnantsPresent() then
        table.insert(ids, "ProjectRemnants")
    end
    return table.concat(ids, ",")
end

local function zombieBuddyVersion()
    if type(ZombieBuddy) ~= "table" then
        return ""
    end
    if not ZombieBuddy.getFullVersionString then
        return "unknown"
    end
    local ok, version = pcall(ZombieBuddy.getFullVersionString)
    return ok and tostring(version) or "unknown"
end

-- pz3d ships its own Java loader. The loader sets pz3dLoaderAgent and the engine sets PZ3D.
local function pz3dState()
    local state = {}
    if pz3dLoaderAgent ~= nil then
        table.insert(state, "loader")
    end
    if PZ3D ~= nil then
        table.insert(state, "engine")
        local ok, ready = pcall(function()
            return PZ3D.ready()
        end)
        if ok and ready then
            table.insert(state, "ready")
        end
    end
    return table.concat(state, ",")
end

local function anythingFound()
    return ZombieBuddy ~= nil or pz3dLoaderAgent ~= nil or PZ3D ~= nil or projectRemnantsPresent()
end

local ticks = 0

local function onTick()
    ticks = ticks + 1
    if not anythingFound() then
        if ticks >= MAX_TICKS then
            Events.OnTick.Remove(onTick)
        end
        return
    end
    Events.OnTick.Remove(onTick)
    sendClientCommand(MODULE, "report", {
        pz3d = pz3dState(),
        loadedMods = loadedMods(),
        menuMods = menuMods(),
        javaMods = javaMods(),
        zombieBuddy = zombieBuddyVersion(),
    })
end

Events.OnTick.Add(onTick)

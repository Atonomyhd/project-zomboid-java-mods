--
-- SurvivorSkillObeliskProximity.lua
-- When another player stands within SkillObelisk.ProximityAlertDistance tiles
-- of a placed obelisk, that obelisk's glow pulses in time with a heartbeat
-- played at its square. Everything runs on the client off the lights that
-- UnpoweredGlow already tracks, so only obelisks streamed in on this client
-- are ever scanned, and an obelisk that streams out stops on its own.
--
-- Local players never trigger their own alerts, and neither do members of a
-- local player's faction or safehouses, nor staff: the pulse warns the viewer
-- that an outsider is at the stone. Silver obelisks have no glow light, so
-- they get no alert.
--
-- Reload story mirrors UnpoweredGlow: the OnTick hook is stashed on a global
-- so a re-execution drops the previous hook and un-tints any pulsed light.
--

require("UnpoweredGlow")
require("SurvivorSkillObeliskLighting")

SurvivorSkillObeliskProximity = SurvivorSkillObeliskProximity or {}

local CONFIG_PREFIX = "survivor_skill_obelisk_"
local SOUND = "SurvivorSkillObeliskHeartbeat"
local BEAT_MS = 900
local SCAN_EVERY_TICKS = 10

-- Fraction of normal brightness the glow sinks to just before the next beat.
local DIM_FLOOR = 0.2

-- key "x,y,z" -> { x, y, z, beatAt } for obelisks with a player in range.
local alerts = {}
local hasAlerts = false
-- key -> true for lights whose color currently differs from the base color.
local pulsed = {}
local hasPulsed = false
local tickCounter = 0

local function alertDistance()
    local sv = SandboxVars and SandboxVars.SkillObelisk
    local d = sv and sv.ProximityAlertDistance
    if type(d) ~= "number" or d <= 0 then
        return 0
    end
    return d
end

local function keyOf(x, y, z)
    return x .. "," .. y .. "," .. z
end

-- LightingJNI hands the renderer min(1, 2 * channel), so a 0.8 base channel
-- already renders at full. Scale the rendered value rather than the raw one,
-- or the bright half of the pulse would clip flat.
local function pulsedChannel(base, brightness)
    local rendered = base * 2
    if rendered > 1 then
        rendered = 1
    end
    return rendered * brightness / 2
end

local function setBrightness(cfg, light, brightness)
    light:setR(pulsedChannel(cfg.r, brightness))
    light:setG(pulsedChannel(cfg.g, brightness))
    light:setB(pulsedChannel(cfg.b, brightness))
end

local function restoreBase(cfg, light)
    light:setR(cfg.r)
    light:setG(cfg.g)
    light:setB(cfg.b)
end

local function addNames(ignored, owner, names)
    if owner ~= nil then
        ignored[owner] = true
    end
    if names ~= nil then
        for i = 0, names:size() - 1 do
            ignored[names:get(i)] = true
        end
    end
end

-- Observer and every rank above it can toggle their own invisibility; user
-- and priority cannot, so the capability marks staff without naming roles.
-- Roles reach other clients through ExtraInfoPacket, as does the invisible
-- flag, so both are readable on remote players.
local function isStaff(p)
    if p:isInvisible() then
        return true
    end
    local role = p:getRole()
    return role ~= nil and role:hasCapability(Capability.ToggleInvisibleHimself)
end

-- Usernames that can never trigger an alert: local players, staff, and anyone
-- sharing a faction or a safehouse with a local player. The string overload
-- of playerAllowed is deliberate: the IsoPlayer overload also admits the
-- admin capability, which would make every admin an ally of every safehouse.
--
-- Membership changes are rare, so this is rebuilt every IGNORED_REFRESH_MS
-- rather than every scan; it is the only place that walks the safehouse list.
local function buildIgnored(players)
    local ignored = {}
    local safehouses = SafeHouse.getSafehouseList()
    for i = 0, players:size() - 1 do
        local p = players:get(i)
        if p ~= nil then
            local username = p:getUsername()
            if p:isLocalPlayer() then
                ignored[username] = true
                local faction = Faction.getPlayerFaction(p)
                if faction ~= nil then
                    addNames(ignored, faction:getOwner(), faction:getPlayers())
                end
                if safehouses ~= nil then
                    for j = 0, safehouses:size() - 1 do
                        local safehouse = safehouses:get(j)
                        if safehouse:playerAllowed(username) then
                            addNames(ignored, safehouse:getOwner(), safehouse:getPlayers())
                        end
                    end
                end
            elseif isStaff(p) then
                ignored[username] = true
            end
        end
    end
    return ignored
end

-- Positions of every player who could trigger an alert this scan. One pass
-- over the online list, so the per-player Java calls never multiply by the
-- number of loaded obelisks.
local function collectCandidates(players, ignored)
    local candidates = {}
    for i = 0, players:size() - 1 do
        local p = players:get(i)
        if p ~= nil and not ignored[p:getUsername()] and not p:isDead() then
            candidates[#candidates + 1] = p:getX()
            candidates[#candidates + 1] = p:getY()
        end
    end
    return candidates
end

local function anyCandidateWithin(candidates, x, y, distance)
    local cx, cy = x + 0.5, y + 0.5
    local limitSq = distance * distance
    for i = 1, #candidates, 2 do
        local dx = candidates[i] - cx
        local dy = candidates[i + 1] - cy
        if dx * dx + dy * dy <= limitSq then
            return true
        end
    end
    return false
end

local function playBeat(alert)
    local cell = getCell()
    if cell == nil then
        return
    end
    local square = cell:getGridSquare(alert.x, alert.y, alert.z)
    if square == nil then
        return
    end
    -- playSoundImpl (not playSound) keeps the beat local; every client runs
    -- its own scan, so nothing needs to go over the wire.
    local emitter = getWorld():getFreeEmitter(alert.x + 0.5, alert.y + 0.5, alert.z)
    emitter:playSoundImpl(SOUND, square)
end

local IGNORED_REFRESH_MS = 2000
local ignored = {}
local ignoredBuiltAt = -IGNORED_REFRESH_MS

-- Loaded obelisk positions as a flat {x, y, z, ...} array, gathered in Lua
-- only, so a client with no obelisk nearby pays nothing beyond this walk.
local lightPositions = {}

local function collectLightPosition(cfg, x, y, z)
    lightPositions[#lightPositions + 1] = x
    lightPositions[#lightPositions + 1] = y
    lightPositions[#lightPositions + 1] = z
end

local function dropEndedAlerts(seen)
    local ended = {}
    for k in pairs(alerts) do
        if not seen[k] then
            ended[#ended + 1] = k
        end
    end
    for i = 1, #ended do
        alerts[ended[i]] = nil
    end
    hasAlerts = false
    for _ in pairs(alerts) do
        hasAlerts = true
        break
    end
end

local function scan()
    local seen = {}
    local distance = alertDistance()
    lightPositions = {}
    if distance > 0 then
        UnpoweredGlow.forEachLight(CONFIG_PREFIX, collectLightPosition)
    end
    if #lightPositions > 0 then
        local players = getOnlinePlayers()
        if players ~= nil then
            local now = getTimestampMs()
            if now - ignoredBuiltAt >= IGNORED_REFRESH_MS then
                ignored = buildIgnored(players)
                ignoredBuiltAt = now
            end
            local candidates = collectCandidates(players, ignored)
            if #candidates > 0 then
                for i = 1, #lightPositions, 3 do
                    local x, y, z = lightPositions[i], lightPositions[i + 1], lightPositions[i + 2]
                    local k = keyOf(x, y, z)
                    if not seen[k] and anyCandidateWithin(candidates, x, y, distance) then
                        seen[k] = true
                        if alerts[k] == nil then
                            -- beatAt a full period back so the first beat lands immediately.
                            alerts[k] = { x = x, y = y, z = z, beatAt = now - BEAT_MS }
                        end
                    end
                end
            end
        end
    end
    dropEndedAlerts(seen)
end

local passNow = 0

local function passLight(cfg, x, y, z, light)
    local k = keyOf(x, y, z)
    local alert = alerts[k]
    if alert == nil then
        if pulsed[k] then
            pulsed[k] = nil
            restoreBase(cfg, light)
        end
        return
    end
    if passNow - alert.beatAt >= BEAT_MS then
        alert.beatAt = passNow
        playBeat(alert)
    end
    local phase = (passNow - alert.beatAt) / BEAT_MS
    if phase > 1 then
        phase = 1
    end
    local fall = 1 - phase
    setBrightness(cfg, light, DIM_FLOOR + (1 - DIM_FLOOR) * fall * fall)
    pulsed[k] = true
    hasPulsed = true
end

local function onTick()
    tickCounter = tickCounter + 1
    if tickCounter >= SCAN_EVERY_TICKS then
        tickCounter = 0
        scan()
    end
    if not hasAlerts and not hasPulsed then
        return
    end
    passNow = getTimestampMs()
    hasPulsed = false
    UnpoweredGlow.forEachLight(CONFIG_PREFIX, passLight)
end

local function restoreAll()
    UnpoweredGlow.forEachLight(CONFIG_PREFIX, function(cfg, _, _, _, light)
        restoreBase(cfg, light)
    end)
end

local previous = SurvivorSkillObeliskProximity._state
if previous ~= nil and previous.onTick ~= nil then
    Events.OnTick.Remove(previous.onTick)
    restoreAll()
end

SurvivorSkillObeliskProximity._state = { onTick = onTick }
Events.OnTick.Add(onTick)

return SurvivorSkillObeliskProximity

--
-- SurvivorSkillObeliskBlessing.lua
-- Client view of the Obelisk Blessing: a timed all-skill XP bonus granted at a
-- Blessing-type obelisk. The server owns the timer and boosts every perk XP
-- gain itself (ObeliskBlessingXpBoost), so this file only has to (1) mirror
-- the blessing state for menus and halo text, and (2) boost the one XP path
-- the server never sees: the Lifestyles mod's client-only hidden skills
-- (Yoga, Inventing, PingPong) in HiddenSkills.addXP.
--

local MODULE = "SurvivorSkillObelisk"
local GET_COMMAND = "getBlessing"
local STATE_REPLY = "blessingState"

-- State lives on a global so a hot-reload of this file keeps the blessing
-- status and doesn't stack a second set of event handlers.
SurvivorSkillObeliskBlessing = SurvivorSkillObeliskBlessing or {}
local Blessing = SurvivorSkillObeliskBlessing

Blessing.state = Blessing.state or { active = false }

local function worldAgeHours()
    local gameTime = getGameTime()
    if gameTime == nil then
        return 0
    end
    return gameTime:getWorldAgeHours()
end

function Blessing.bonusPercent()
    local vars = SandboxVars and SandboxVars.SkillObelisk
    local value = vars and vars.BlessingXpBonusPercent
    if type(value) == "number" then
        return value
    end
    return Blessing.state.bonusPercent or 50
end

function Blessing.durationHours()
    local vars = SandboxVars and SandboxVars.SkillObelisk
    local value = vars and vars.BlessingDurationHours
    if type(value) == "number" then
        return value
    end
    return Blessing.state.durationHours or 24
end

function Blessing.remainingHours()
    local expires = Blessing.state.expiresWorldHours
    if not Blessing.state.active or expires == nil then
        return 0
    end
    return math.max(0, expires - worldAgeHours())
end

function Blessing.isActive()
    return Blessing.remainingHours() > 0
end

-- Multiplier hidden-skill XP should be scaled by right now (1 when unblessed).
function Blessing.hiddenSkillMultiplier()
    if not Blessing.isActive() then
        return 1
    end
    return 1 + Blessing.bonusPercent() / 100
end

function Blessing.formatHours(hours)
    if hours >= 48 then
        return string.format("%d days %d hours", math.floor(hours / 24), math.floor(hours % 24))
    end
    if hours >= 1 then
        return string.format("%d hours", math.floor(hours))
    end
    return string.format("%d minutes", math.max(1, math.floor(hours * 60)))
end

function Blessing.tooltipText()
    local lines = {
        string.format(
            "Blessed characters earn +%d%% XP in every skill for %s.",
            Blessing.bonusPercent(),
            Blessing.formatHours(Blessing.durationHours())
        ),
        "Also boosts Lifestyles Yoga, Inventing, Ping Pong and Meditation.",
    }
    if Blessing.isActive() then
        table.insert(
            lines,
            string.format(
                "Your current blessing has %s left. Receiving it again restarts the timer.",
                Blessing.formatHours(Blessing.remainingHours())
            )
        )
    end
    return table.concat(lines, " <LINE> ")
end

---------------------------------------------------------------------------
-- Server replies
---------------------------------------------------------------------------

local function onBlessingState(args)
    if args == nil then
        return
    end
    local wasActive = Blessing.isActive()
    Blessing.state = {
        active = args.active == true,
        bonusPercent = args.bonusPercent,
        durationHours = args.durationHours,
        expiresWorldHours = args.expiresWorldHours,
        -- The server's clock may be a few seconds ahead of ours; anchor expiry
        -- to our own world age so the countdown never shows negative time.
        receivedWorldHours = worldAgeHours(),
    }
    if args.expiresWorldHours ~= nil and args.nowWorldHours ~= nil then
        Blessing.state.expiresWorldHours = worldAgeHours()
            + (args.expiresWorldHours - args.nowWorldHours)
    end

    local player = getPlayer()
    if player == nil then
        return
    end
    if args.granted == true then
        HaloTextHelper.addGoodText(
            player,
            string.format(
                "Obelisk Blessing: +%d%% XP for %s",
                Blessing.bonusPercent(),
                Blessing.formatHours(Blessing.remainingHours())
            )
        )
    elseif args.reason == "notBlessingObelisk" then
        HaloTextHelper.addBadText(player, "This obelisk grants no blessing")
    elseif args.reason == "error" then
        HaloTextHelper.addBadText(player, "The obelisk is silent")
    elseif args.reason == "expired" or (wasActive and not Blessing.state.active) then
        HaloTextHelper.addBadText(player, "Your Obelisk Blessing has faded")
    end
end

local function onServerCommand(module, command, args)
    if module ~= MODULE or command ~= STATE_REPLY then
        return
    end
    onBlessingState(args)
end

function Blessing.requestState()
    local player = getPlayer()
    if not isClient() or player == nil then
        return
    end
    sendClientCommand(player, MODULE, GET_COMMAND, {})
end

---------------------------------------------------------------------------
-- Lifestyles hidden skills (client-only XP path)
---------------------------------------------------------------------------

-- HiddenSkills.addLevel re-enters addXP with level overflow; the guard keeps
-- that inner call from being boosted a second time.
local inWrapper = false

local function wrappedHiddenAddXp(character, skill, amount, noHalo, transmit)
    local original = Blessing.originalHiddenAddXp
    if inWrapper or type(amount) ~= "number" or amount <= 0 or character ~= getPlayer() then
        return original(character, skill, amount, noHalo, transmit)
    end
    local multiplier = Blessing.hiddenSkillMultiplier()
    inWrapper = true
    local ok, err = pcall(original, character, skill, amount * multiplier, noHalo, transmit)
    inWrapper = false
    if not ok then
        error(err, 0)
    end
end

function Blessing.installHiddenSkillHook()
    if HiddenSkills == nil or type(HiddenSkills.addXP) ~= "function" then
        return false
    end
    if HiddenSkills.addXP == Blessing.wrapper then
        return true
    end
    -- Lifestyles may itself have been reloaded, so always capture the live
    -- function unless it is already our wrapper.
    Blessing.originalHiddenAddXp = HiddenSkills.addXP
    Blessing.wrapper = wrappedHiddenAddXp
    HiddenSkills.addXP = wrappedHiddenAddXp
    return true
end

local function onGameStart()
    Blessing.installHiddenSkillHook()
    Blessing.requestState()
end

local function onTick()
    -- Lifestyles creates HiddenSkills during its own load; retry until it is
    -- there, then stop polling.
    if Blessing.installHiddenSkillHook() or HiddenSkills == nil then
        Events.OnTick.Remove(onTick)
    end
end

if not Blessing.registered then
    Blessing.registered = true
    Events.OnServerCommand.Add(onServerCommand)
    Events.OnGameStart.Add(onGameStart)
    Events.OnTick.Add(onTick)
else
    Blessing.installHiddenSkillHook()
end

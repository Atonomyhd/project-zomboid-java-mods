require("TimedActions/ISBaseTimedAction")

---@class ObeliskBlessingAction : ISBaseTimedAction
---@field character IsoGameCharacter
---@field sound number
ObeliskBlessingAction = ISBaseTimedAction:derive("ObeliskBlessingAction")

function ObeliskBlessingAction:isValid()
    return self.obeliskX ~= nil and self.obeliskY ~= nil and self.obeliskZ ~= nil
end

function ObeliskBlessingAction:update()
    self.character:setMetabolicTarget(Metabolics.LightDomestic)
end

function ObeliskBlessingAction:start()
    self:setActionAnim("VehicleWorkOnMid")
    self.sound = getSoundManager():playUISound("SurvivorSkillObeliskRecover")
end

function ObeliskBlessingAction:stop()
    if self.sound ~= 0 then
        getSoundManager():stopUISound(self.sound)
        self.sound = 0
    end
    ISBaseTimedAction.stop(self)
end

function ObeliskBlessingAction:perform()
    if self.sound ~= 0 then
        getSoundManager():stopUISound(self.sound)
        self.sound = 0
    end

    sendClientCommand(self.character, "SurvivorSkillObelisk", "receiveBlessing", {
        x = self.obeliskX,
        y = self.obeliskY,
        z = self.obeliskZ,
    })

    ISBaseTimedAction.perform(self)
end

function ObeliskBlessingAction:new(character, obeliskX, obeliskY, obeliskZ)
    local o = {}
    setmetatable(o, self)
    self.__index = self
    o.stopOnWalk = true
    o.stopOnRun = true

    o.character = character
    o.obeliskX = obeliskX
    o.obeliskY = obeliskY
    o.obeliskZ = obeliskZ

    -- Same fixed real-time length as RecoverSkillsAction (see the note there).
    o.maxTime = 121 * 30
    if character:isTimedActionInstant() then
        o.maxTime = 1
    end

    return o
end

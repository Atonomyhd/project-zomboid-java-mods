--
-- SurvivorSkillObeliskBlessingMoodle.lua
-- A green moodle that sits under the vanilla moodle stack while the local
-- player holds an Obelisk Blessing. Vanilla MoodlesUI has no registration
-- API and the Moodle Framework is not a dependency, so this draws its own
-- slot with the vanilla background and outline textures tinted the "good"
-- colour, the obelisk icon from media/ui/SurvivorSkillObelisk, and the same
-- hover tooltip layout MoodlesUI uses.
--

require("ISUI/ISUIElement")
require("SurvivorSkillObeliskBlessing")

local ICON_PREFIX = "media/ui/SurvivorSkillObelisk/moodle_blessing_"
local VANILLA_DIR = "media/ui/Moodles/"
local SLOT_GAP = 10
local OFFSCREEN = 500

-- Mirrors MoodleTextureSet; FoodEaten only occupies a slot at high levels.
local MOODLE_TYPE_NAMES = {
    "ENDURANCE",
    "TIRED",
    "HUNGRY",
    "PANIC",
    "SICK",
    "BORED",
    "UNHAPPY",
    "BLEEDING",
    "WET",
    "HAS_A_COLD",
    "ANGRY",
    "STRESS",
    "THIRST",
    "INJURED",
    "PAIN",
    "HEAVY_LOAD",
    "DRUNK",
    "DEAD",
    "ZOMBIE",
    "HYPERTHERMIA",
    "HYPOTHERMIA",
    "WINDCHILL",
    "CANT_SPRINT",
    "UNCOMFORTABLE",
    "NOXIOUS_SMELL",
    "FOOD_EATEN",
}
local FOOD_EATEN_MIN_LEVEL = 3

SurvivorSkillObeliskBlessingMoodle = ISUIElement:derive("SurvivorSkillObeliskBlessingMoodle")
local BlessingMoodle = SurvivorSkillObeliskBlessingMoodle

local function countVanillaSlots(character)
    local moodles = character:getMoodles()
    if moodles == nil then
        return 0
    end
    local count = 0
    for _, name in ipairs(MOODLE_TYPE_NAMES) do
        local moodleType = MoodleType[name]
        if moodleType ~= nil then
            local level = moodles:getMoodleLevel(moodleType)
            if name == "FOOD_EATEN" then
                if level >= FOOD_EATEN_MIN_LEVEL then
                    count = count + 1
                end
            elseif level > 0 then
                count = count + 1
            end
        end
    end
    return count
end

function BlessingMoodle:new(playerNum)
    local o = ISUIElement.new(self, 0, 0, 32, 32)
    o.playerNum = playerNum or 0
    o.size = 32
    o.slideOffset = OFFSCREEN
    o.textures = nil
    o:setVisible(false)
    return o
end

function BlessingMoodle:loadTextures(size)
    if self.textures ~= nil and self.size == size then
        return
    end
    self.size = size
    self.textures = {
        background = getTexture(VANILLA_DIR .. size .. "/_Moodles_BGsolid.png"),
        border = getTexture(VANILLA_DIR .. size .. "/_Moodles_BGoutline.png"),
        icon = getTexture(ICON_PREFIX .. size .. ".png"),
    }
    self:setWidth(size)
    self:setHeight(size)
end

function BlessingMoodle:update()
    ISUIElement.update(self)
    local character = getSpecificPlayer(self.playerNum)
    local moodleUI = UIManager.getMoodleUI(self.playerNum)
    local active = character ~= nil
        and not character:isDead()
        and moodleUI ~= nil
        and moodleUI:isVisible()
        and SurvivorSkillObeliskBlessing.isActive()
    if not active then
        self.slideOffset = OFFSCREEN
        self:setVisible(false)
        return
    end

    local size = math.floor(moodleUI:getWidth())
    if size <= 0 then
        size = 32
    end
    self:loadTextures(size)

    -- Vanilla slides a new moodle in from 500px below its slot.
    if self.slideOffset > 0.8 then
        self.slideOffset = self.slideOffset - self.slideOffset * 0.15
    else
        self.slideOffset = 0
    end
    local slot = countVanillaSlots(character)
    self:setX(moodleUI:getX())
    self:setY(moodleUI:getY() + slot * (SLOT_GAP + size) + self.slideOffset)
    self:setVisible(true)
end

function BlessingMoodle:render()
    if self.textures == nil then
        return
    end
    local good = getCore():getGoodHighlitedColor()
    local r, g, b = 0.5, 0.5, 0.5
    if good ~= nil then
        r, g, b = good:getR(), good:getG(), good:getB()
    end
    if self.textures.background ~= nil then
        self:drawTexture(self.textures.background, 0, 0, 1, r, g, b)
    end
    if self.textures.border ~= nil then
        self:drawTexture(self.textures.border, 0, 0, 1, 1, 1, 1)
    end
    if self.textures.icon ~= nil then
        self:drawTexture(self.textures.icon, 0, 0, 1, 1, 1, 1)
    end
    if self:isMouseOver() then
        self:renderTooltip()
    end
end

function BlessingMoodle:renderTooltip()
    local title = "Obelisk Blessing"
    local Blessing = SurvivorSkillObeliskBlessing
    local desc = string.format(
        "+%d%% XP in every skill, %s left",
        Blessing.bonusPercent(),
        Blessing.formatHours(Blessing.remainingHours())
    )
    local textManager = getTextManager()
    local fontHgt = textManager:getFontHeight(UIFont.Small)
    local width = math.max(
        textManager:MeasureStringX(UIFont.Small, title),
        textManager:MeasureStringX(UIFont.Small, desc)
    )
    local h = (2 + fontHgt) * 2
    local y = 1
    if self.size > h then
        y = y + math.floor((self.size - h) / 2)
    end
    self:drawRect(-10 - width - 6, y - 2, width + 12, h, 0.6, 0, 0, 0)
    self:drawTextRight(title, -10, y, 1, 1, 1, 1, UIFont.Small)
    self:drawTextRight(desc, -10, y + fontHgt, 0.8, 0.8, 0.8, 1, UIFont.Small)
end

---------------------------------------------------------------------------
-- Lifecycle
---------------------------------------------------------------------------

BlessingMoodle.instances = BlessingMoodle.instances or {}

function BlessingMoodle.ensure(playerNum)
    playerNum = playerNum or 0
    local existing = BlessingMoodle.instances[playerNum]
    if existing ~= nil then
        existing:removeFromUIManager()
    end
    local element = BlessingMoodle:new(playerNum)
    element:initialise()
    element:addToUIManager()
    BlessingMoodle.instances[playerNum] = element
    return element
end

local function onCreatePlayer(playerNum)
    if playerNum ~= 0 and getSpecificPlayer(playerNum) == nil then
        return
    end
    BlessingMoodle.ensure(playerNum)
end

if not BlessingMoodle.registered then
    BlessingMoodle.registered = true
    Events.OnCreatePlayer.Add(onCreatePlayer)
elseif getPlayer() ~= nil then
    BlessingMoodle.ensure(0)
end

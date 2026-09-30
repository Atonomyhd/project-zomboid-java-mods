--
-- Copyright (c) 2024 outdead.
-- Use of this source code is governed by the Apache 2.0 license.
--
-- BrushToolLogger adds BrushTool logs to the Logs directory the Project Zomboid game.
--

local BrushToolServerLogger = {
    Original = {
        ISBrushToolTileCursor_create = ISBrushToolTileCursor.create,
        sendAddObjectToMap = sendAddObjectToMap,
    },
}

function BrushToolServerLogger.logPlacement(character, x, y, z, sprite)
    if not SandboxVars.LogExtender.BrushToolLogs or not character then
        return
    end

    local location = logutils.GetLocation(character)
    local objLocation = tostring(x) .. "," .. tostring(y) .. "," .. tostring(z)
    local objName = "IsoThumpable"

    local message = logutils.GetLogLinePrefix(character, "added " .. objName)
        .. " ("
        .. tostring(sprite)
        .. ") at "
        .. objLocation
        .. " ("
        .. location
        .. ")"
    logutils.WriteLog(logutils.filemask.brushtool, message)
end

-- Multiplayer clients never place brush tiles through create(). The placer sends
-- AddObjectToMap, and every client in range replays create() on the class table from
-- OnTileObjectAdded, so only a real cursor instance with a character is a placement.
function BrushToolServerLogger.createBrushToolTileCursor(self, x, y, z, north, sprite)
    BrushToolServerLogger.Original.ISBrushToolTileCursor_create(self, x, y, z, north, sprite)

    if isClient() or self == ISBrushToolTileCursor then
        return
    end
    BrushToolServerLogger.logPlacement(self.character, x, y, z, sprite)
end

function BrushToolServerLogger.sendAddObjectToMap(square, sprite)
    BrushToolServerLogger.Original.sendAddObjectToMap(square, sprite)

    if square then
        BrushToolServerLogger.logPlacement(
            getPlayer(),
            square:getX(),
            square:getY(),
            square:getZ(),
            sprite
        )
    end
end

ISBrushToolTileCursor.create = BrushToolServerLogger.createBrushToolTileCursor
if BrushToolServerLogger.Original.sendAddObjectToMap then
    sendAddObjectToMap = BrushToolServerLogger.sendAddObjectToMap
end

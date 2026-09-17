-- Keep the last usable cache while recovering. Authorization remains server-side.
local S = { requestId = 0, nextRequest = 0, waiting = false, attempts = 0 }
AVCS.Sync = S
AVCS.cacheRevision = 0

function S.changed()
    AVCS.cacheRevision = AVCS.cacheRevision + 1
end
function S.ready()
    return AVCS.dbByVehicleSQLID ~= nil and AVCS.dbByPlayerID ~= nil
end
local function stop()
    if S.hook then
        Events.OnTick.Remove(S.hook)
    end
    S.hook = nil
end

function S.request(retry)
    if S.failed and not retry then
        return
    end
    if S.hook then
        return
    end
    S.failed, S.waiting, S.attempts = false, true, 0
    S.hook = function()
        local player, now = getPlayer(), getTimestampMs()
        if not player or now < S.nextRequest then
            return
        end
        if S.attempts >= 3 then
            S.failed, S.waiting = true, false
            S.vehicle, S.player = nil, nil
            stop()
            print(
                "[AVCS] Claim refresh unavailable. Existing cache retained; reopen a vehicle manager to retry."
            )
            return
        end
        S.attempts = S.attempts + 1
        S.requestId = S.requestId + 1
        S.vehicle, S.player = nil, nil
        S.nextRequest = now + 5000 * S.attempts
        sendClientCommand(
            player,
            "AVCS",
            "requestFullSync",
            { requestId = S.requestId, protocol = 3 }
        )
    end
    Events.OnTick.Add(S.hook)
    S.hook()
end

local function publish(vehicles, players)
    if type(vehicles) ~= "table" or type(players) ~= "table" then
        return false
    end
    -- Owner indexes are derived display data. A missing back-reference must not
    -- permanently lock all vehicles or trigger endless full-table downloads.
    local index = {}
    for id, claim in pairs(vehicles) do
        if
            type(id) ~= "number"
            or type(claim) ~= "table"
            or type(claim.OwnerPlayerID) ~= "string"
        then
            return false
        end
        local owner = claim.OwnerPlayerID
        if not index[owner] then
            local previous = players[owner]
            index[owner] = {
                LastKnownLogonTime = type(previous) == "table" and tonumber(
                    previous.LastKnownLogonTime
                ) or 0,
            }
        end
        index[owner][id] = true
    end
    AVCS.dbByVehicleSQLID, AVCS.dbByPlayerID = vehicles, index
    S.vehicle, S.player, S.waiting, S.failed = nil, nil, false, false
    stop()
    S.changed()
    return true
end

function S.receiveSnapshot(args)
    if type(args) ~= "table" or args.requestId ~= S.requestId or not S.waiting then
        return
    end
    publish(args.vehicles or {}, args.players or {})
end

function S.receive(kind, data, requestId)
    if kind ~= "vehicle" and kind ~= "player" then
        return
    end
    if requestId and requestId ~= S.requestId then
        return
    end
    if type(data or {}) ~= "table" then
        return
    end
    S[kind] = data or {}
    if S.vehicle and S.player then
        publish(S.vehicle, S.player)
    end
end

function S.deltaReady()
    if S.ready() and not S.waiting then
        return true
    end
    S.vehicle, S.player = nil, nil
    S.request()
    return false
end

Events.OnDisconnect.Add(function()
    stop()
    S.vehicle, S.player = nil, nil
    S.waiting, S.failed, S.attempts, S.nextRequest = false, false, 0, 0
    AVCS.dbByVehicleSQLID, AVCS.dbByPlayerID = nil, nil
    S.changed()
end)
return S

name = "Native World Observer"
version = "1.0.0"
description = "Observe native world notifications with an owned, automatically detached Lua listener."

function modInit()
    local attachment
    local notifications = 0
    local ticks = 0

    betamoon.events:on("world_tick", function(ctx)
        local world = ctx.native:world()
        if not world then
            return
        end

        if not attachment or attachment:describe().state ~= "active" then
            local WorldAccess = betamoon.native:type("net.minecraft.src.IWorldAccess", {
                loaderId = world:describe().type.loaderId
            })
            attachment = ctx.native:observeWorld({
                methods = {{
                    method = WorldAccess:method("markBlockAndNeighborsNeedsUpdate", "(III)V"),
                    callback = function(callbackCtx, x, y, z)
                        -- These are read-only observations, not an authoritative block-change journal.
                        -- Do not retain callbackCtx or native roots from this callback.
                        notifications = notifications + 1
                    end
                }}
            })
        end

        ticks = ticks + 1
        if ticks % 200 == 0 and notifications > 0 then
            betamoon.log:info("Native world notifications in this interval: " .. notifications)
            notifications = 0
        end
    end)

    -- Owner reload/unload and world replacement invalidate and detach this exact listener automatically.
    -- Calling attachment:close() also stops callbacks immediately; physical removal occurs at a safe boundary.
end

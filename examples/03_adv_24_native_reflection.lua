-- Use the world/chunk APIs for ordinary gameplay. Native access fills gaps in those APIs.
name = "Native Reflection"
version = "1.0.0"
description = "Inspect a native type and call an exact method using a fresh world receiver."

function modInit()
    local ticks = 0
    betamoon.events:on("world_tick", function(ctx)
        ticks = ticks + 1
        if ticks % 200 ~= 1 then
            return
        end

        local world = ctx.native:world()
        if not world then
            return
        end

        -- The receiver identifies its actual loader. This also handles launchers with several loaders.
        local World = betamoon.native:type("net.minecraft.src.World", {
            loaderId = world:describe().type.loaderId
        })
        local getBlockId = World:method("getBlockId", "(III)I")
        local methodInfo = getBlockId:describe()
        if not methodInfo then
            -- Discovery can still be running; the exact symbolic reference remains callable.
            betamoon.log:debug("Native member metadata is not ready yet.")
        end

        -- A type reference selects the member; the separate receiver supplies the current world.
        local id = getBlockId:call(world, 0, 64, 0)
        betamoon.log:info("Native block ID at 0,64,0: " .. id)
        -- Do not store world: it is borrowed for this callback only.
    end)
end

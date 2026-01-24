name = "Events Example"
version = "0.0.1"
description = "Example outlining the usage of events."


function modInit()
    betamoon.events.onKeyInput(function(ctx)
        if ctx.isKey() and ctx.isPressed() then
            local ch = ctx.getChar()
            if ch ~= nil then
                betamoon.chat("Key: %s", ch)
                print("Key: " .. ch)
            end
        end
    end)
end

-- Craft sugar with redstone to obtain four signal bells, then hold one and right-click.
-- A normal item can define its own action without becoming food, a tool, or a projectile.
-- This example keeps the action small before later lessons introduce targets and inventory ticks.

name = "First Item Use Example"
version = "1.0.0"
description = "Adds Signal Bell, a consumable item with a sound, chat message, and cooldown. Combine one " ..
    "sugar and one redstone dust in any arrangement to craft four bells.\n\n" ..
    "Select a bell and right-click to hear a pop and read 'The signal bell rings' in chat. Each " ..
    "successful use consumes one bell and starts a 20-tick cooldown, about one second at normal " ..
    "game speed. Watch the stack count decrease and try clicking again during the cooldown. " ..
    "Compare the callback's visible effects with the separate consumption and cooldown settings."

function modInit()
  local signalBell = betamoon.items:add {
    id = 5028,
    key = "first_signal_bell",
    displayName = "Signal Bell",
    icon = { x = 10, y = 3 },
    maxStackSize = 16,
    -- consume is the number removed after a handled use. Minecraft runs at about
    -- 20 ticks per second, so a cooldown of 20 prevents use for about one second.
    use = {
      consume = 1,
      cooldown = 20
    },
    onUse = {
      action = function(ctx)
        -- sounds.random contains named vanilla sounds. The matching raw string also works.
        ctx.world:playSound(betamoon.mc.sounds.random.pop, 0.6, 1.2)
        betamoon.chat:send("The signal bell rings")
        return "handled"
      end
    }
  }

  betamoon.recipes:add {
    type = "shapeless",
    output = betamoon.stack(signalBell, 4),
    ingredients = { betamoon.items:getRequired(353), betamoon.items:getRequired(331) }
  }
end

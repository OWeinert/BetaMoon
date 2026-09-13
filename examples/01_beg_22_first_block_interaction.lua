-- Craft cobblestone with a stick to obtain this block, then place and right-click it.
-- onActivate belongs to the block and runs only when that registered block is activated.
-- The callback context identifies the exact block position involved in the interaction.

name = "First Block Interaction Example"
version = "1.0.0"
description = "Creates a block that responds when the player activates it."

function modInit()
  local interactiveBlock = betamoon.blocks:add {
    id = 228,
    key = "first_interactive_block",
    displayName = "Greeting Block",
    -- blockMaterials contains the built-in material choices accepted by blocks.
    -- The equivalent string "rock" can still be supplied directly.
    material = betamoon.mc.blockMaterials.rock,
    hardness = 1.5,
    texture = 1,
    onActivate = {
      action = function(ctx)
        betamoon.chat:send("Activated at %i, %i, %i", ctx.x, ctx.y, ctx.z)
        -- sounds.random contains named vanilla sounds. Raw sound names remain valid too.
        ctx.world:playSound(betamoon.mc.sounds.random.click, 0.5, 1)
        -- handled tells Minecraft that this interaction has been dealt with.
        return "handled"
      end
    }
  }

  betamoon.recipes:add {
    type = "shapeless",
    output = betamoon.stack(interactiveBlock),
    ingredients = { betamoon.blocks:getRequired(4), betamoon.items:getRequired(280) }
  }
end

-- Craft cobblestone with a stick to obtain this block, then place and right-click it.
-- onActivate belongs to the block and runs only when that registered block is activated.
-- The callback context identifies the exact block position involved in the interaction.

name = "First Block Interaction Example"
version = "1.0.0"
description = "Adds Greeting Block, a first example of a block with a right-click action. Combine one " ..
    "cobblestone and one stick in any arrangement to craft it, then place it in the world.\n\n" ..
    "Right-click the block to hear a click and receive a chat message containing its coordinates. " ..
    "It has no inventory, mode switch, or saved interaction state; the callback simply responds to " ..
    "activation. Compare the message with the block position and the onActivate function. " ..
    "Intermediate example 08 uses the same crafting ingredients, so test these two examples " ..
    "separately if crafting selects the other block."

function modInit()
  local interactiveBlock = betamoon.blocks:add {
    id = 228,
    key = "example:block/first_interactive_block",
    displayName = "Greeting Block",
    -- blockMaterials contains the built-in material choices accepted by blocks.
    -- The equivalent string "rock" can still be supplied directly.
    material = betamoon.mc.blockMaterials.rock,
    harvest = { pickaxe = 0 },
    hardness = 1.5,
    texture = 1,
    onActivate = {
      action = function(ctx)
        betamoon.chat:send("Activated at %i, %i, %i", ctx.x, ctx.y, ctx.z)
        -- sounds.random contains named vanilla sounds. Raw sound names remain valid too.
        ctx.world:playSound(betamoon.mc.sounds.random.click, 0.5, 1)
        -- handled tells Minecraft that this interaction has been dealt with.
        return betamoon.callbackResults.handled
      end
    }
  }

  betamoon.recipes:add {
    type = "shapeless",
    output = betamoon.stack(interactiveBlock),
    ingredients = { betamoon.blocks:getRequired(4), betamoon.items:getRequired(280) }
  }
end

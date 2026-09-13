-- Each entry in drops is a separate possible stack, not a choice between entries.
-- This block gives diamonds AND coal when harvested successfully with a suitable tool.
-- To try it, obtain block 205, place it, and mine it with an iron-or-better pickaxe.
-- These generous drops are for demonstration; choose balanced quantities for your mod.

name = "Custom Block Drop Example"
version = "2.0.0"
description = "Declares fixed and ranged custom block drops."

function modInit()
  -- getRequired finds an existing item. The script shows an error if it cannot be found.
  -- You can also use an item ID directly, as shown by the coal below.
  -- blockMaterials avoids having to remember the exact spelling; "rock" also works.
  betamoon.blocks:add {
    id = 205,
    material = betamoon.mc.blockMaterials.rock,
    key = "example_block2",
    displayName = "Example Block 2",
    hardness = 3,
    texture = 50,
    -- Harvest levels are minimum tool tiers; level 2 corresponds to iron.
    -- Drop rules do not bypass the requirement to harvest with a suitable tool.
    harvest = { pickaxe = 2 },
    drops = {
      -- Drop between one and four diamonds.
      {
        item = betamoon.items:getRequired(264),
        min = 1,
        max = 4
      },
      -- Without min and max, exactly one coal drops.
      { item = 263 }
    }
  }
end

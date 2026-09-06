name = "Custom Block Drop Example"
version = "2.0.0"
description = "Declares fixed and ranged custom block drops."

function modInit()
  -- getRequired finds an existing item. The script shows an error if it cannot be found.
  -- You can also use an item ID directly, as shown by the coal below.
  betamoon.blocks:add {
    id = 205,
    material = "rock",
    key = "example_block2",
    displayName = "Example Block 2",
    hardness = 3,
    texture = 50,
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

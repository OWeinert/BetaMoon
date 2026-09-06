name = "Custom Block Example"
version = "2.0.0"
description = "Declares a block with harvesting and custom drops."

function modInit()
  -- blocks:add creates a block from the settings inside this table.
  betamoon.blocks:add {
    -- Pick an unused block ID between 0 and 255.
    id = 200,
    -- The material gives the block stone-like behavior.
    material = "rock",
    -- The key is the block's name inside your scripts. Keep it unique.
    key = "example_block",
    -- This is the name players see in the game.
    displayName = "Example Block",
    -- These settings control mining speed, explosions, sound, and appearance.
    hardness = 1.5,
    resistance = 10,
    stepSound = "stone",
    texture = 1,
    -- This block can be mined with any pickaxe level.
    harvest = {
      pickaxe = 0
    },
    -- The block drops one or two cobblestone when broken.
    drops = {
      {
        item = 4,
        min = 1,
        max = 2
      }
    }
  }
end

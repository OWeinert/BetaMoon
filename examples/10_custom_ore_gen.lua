name = "Ore Gen Example"
version = "2.0.0"
description = "Declares an ore block and its world generation."

function modInit()
  local ore = betamoon.blocks:add {
    id = 206,
    material = "rock",
    key = "example_ore",
    displayName = "Example Ore",
    hardness = 3,
    resistance = 5,
    texture = 50,
    harvest = { pickaxe = 2 }
  }

  -- This adds the ore to newly generated chunks.
  -- It controls how often it appears, vein size, height, and which block it replaces.
  betamoon.worldgen.ores:add {
    block = ore,
    veinsPerChunk = 10,
    veinSize = 8,
    height = { min = 0, max = 60 },
    dimension = "overworld",
    replace = betamoon.blocks:getRequired(1),
    -- An empty biome list lets the ore appear in every biome.
    biomes = {}
  }
end

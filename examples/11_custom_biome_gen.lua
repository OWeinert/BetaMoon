name = "Custom Biome Gen Example"
version = "2.0.0"
description = "Declares a biome from scratch."

function modInit()
  -- biomes:add creates a new biome.
  betamoon.worldgen.biomes:add {
    name = "Example Biome",
    color = 0x55cc88,
    foliageColor = 0x33aa77,
    surface = {
      top = 3,
      filler = 13
    },
    -- Temperature and humidity decide where the biome can appear.
    range = {
      temperature = { min = 0.6, max = 0.8 },
      humidity = { min = 0.7, max = 0.9 }
    },
    -- Choose the trees, weather, and animals for the biome.
    trees = {
      type = "default",
      bigTreeChance = 6
    },
    weather = {
      rain = true
    },
    -- This creature list allows sheep to spawn here.
    spawns = {
      creatures = {
        {
          entity = "Sheep",
          weight = 12
        }
      }
    }
  }
end

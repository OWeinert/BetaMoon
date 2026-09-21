-- This declares surface, climate, vegetation and spawn settings for a new biome.
-- Explore new chunks to see it. Defining a biome does not repaint existing terrain.
-- Temperature/humidity ranges are normalized climate values, not degrees or world coordinates.
-- Keep the range reasonably broad while testing, then narrow it for your intended distribution.

name = "Custom Biome Gen Example"
version = "2.0.0"
description = "Adds Example Biome using a complete biome declaration rather than inheriting from a vanilla " ..
    "biome. It has a dirt surface over gravel, custom landscape colors, rain, tree generation, and " ..
    "sheep in its creature-spawn list.\n\n" ..
    "Create a new world or explore newly generated terrain to find it in the configured warm, " ..
    "humid climate range. Existing chunks are unchanged, and neither this biome nor sheep are " ..
    "guaranteed at a particular location. Look at the ground layers, foliage, trees, and weather, " ..
    "then compare them with the biome settings. The sheep weight controls relative spawn selection " ..
    "rather than an exact number of animals."

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
    -- treeModes is the complete set of generation policies accepted here.
    trees = {
      type = betamoon.worldgen.treeModes.default,
      bigTreeChance = 6
    },
    weather = {
      rain = true
    },
    -- spawnGroups and entities expose canonical spawn-list keys and registry names.
    spawns = {
      [betamoon.mc.world.spawnGroups.creatures] = {
        {
          entity = betamoon.mc.entities.sheep,
          -- A spawn weight is a relative selection weight among eligible entries,
          -- not twelve sheep guaranteed per chunk or a percentage.
          weight = 12
        }
      }
    }
  }
end

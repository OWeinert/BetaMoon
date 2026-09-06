name = "Custom Biome From Default Example"
version = "2.0.0"
description = "Copies and adjusts a vanilla biome."

function modInit()
  -- basedOn starts with a copy of an existing Minecraft biome.
  betamoon.worldgen.biomes:add {
    name = "Example Desert Copy",
    basedOn = "Desert",
    -- You only need to write the settings you want to change.
    surface = {
      top = 13
    },
    range = {
      temperature = { min = 0.95, max = 1 },
      humidity = { min = 0, max = 0.2 }
    },
    trees = {
      type = "none"
    },
    weather = {
      rain = false,
      snow = false
    }
  }
end

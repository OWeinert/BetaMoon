name = "Custom Biome From Default Example"
version = "1.0.0"
description = "Shows creating a biome from vanilla defaults."


function modInit()
  betamoon.startWorldGen()
    -- Start from a vanilla biome and tweak its settings.
    :addBiomeGenFromDefault("Desert", "Example Desert Copy")

      -- Calling setters here is optional because this starts with the
      -- vanilla Desert configuration; only override what you need.
      -- See example "14_custom_biome_gen.lua" for a list of available functions.

      -- Setting top block to gravel to make biome easier to spot.
      :setTopBlock(13)
      
      -- The following settings cannot derived from the vanilla biome because of Minecraft restrictions and
      -- must be set explicitly for custom biome placement/behavior.
      :setTemperatureRange(0.95, 1.0)
      :setHumidityRange(0.0, 0.2)
      :setTreeGenerator("none")

      -- finish the biome gen configuration for this biome.
      :finishBiomeGen()
      
    :finishWorldGen()
end

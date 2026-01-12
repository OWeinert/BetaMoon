name = "Custom Biome From Default Example"

dependencies = {}

function modInit()
  betamoon.startWorldGen()
    -- Start from a vanilla biome and tweak its settings.
    :addBiomeGenFromDefault("Desert")
      :setName("Example Desert Copy")

      -- Calling setters here is optional because this starts with the
      -- vanilla Desert configuration; only override what you need.
      -- See example "10_custom_biome_gen.lua" for a list of available functions.

      -- finish the biome gen configuration for this biome.
      :finishBiomeGen()
      
    :finishWorldGen()
end

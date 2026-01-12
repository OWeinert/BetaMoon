name = "Custom Biome Gen Example"

dependencies = {}

function modInit()
  -- World gen registration uses a shared handle for batching entries.
  betamoon.startWorldGen()
    -- Create a new biome from scratch.
    :addBiomeGen("Example Biome")
      -- This sets the display name for the biome.
      :setName("Example Biome")

      -- This sets the map/grass tint color.
      :setColor(0x55cc88)

      -- This sets the foliage tint color.
      :setFoliageColor(0x33aa77)

      -- These set the surface and filler blocks.
      :setTopBlock(2)
      :setFillerBlock(3)

      -- These define the climate range for generation.
      :setTemperatureRange(0.6, 0.8)
      :setHumidityRange(0.7, 0.9)

      -- This sets tree generation behavior.
      :setTreeGenerator("big")
      :setBigTreeChance(6)

      -- These configure mob spawns.
      :clearSpawns("monsters")
      :addSpawn("creatures", "Sheep", 12)

      -- finish the biome gen configuration for this biome.
      :finishBiomeGen()

    -- Commit all pending world-gen entries.
    :finishWorldGen()
end

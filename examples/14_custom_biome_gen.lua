name = "Custom Biome Gen Example"
version = "1.0.0"
description = "Shows custom biome generation."


function modInit()
  -- World gen registration uses a shared handle for batching entries.
  betamoon.startWorldGen()
    -- Create a new biome from scratch.
    :addBiomeGen("Example Biome")

      -- This sets the map/grass tint color.
      :setColor(0x55cc88)

      -- This sets the foliage tint color.
      :setFoliageColor(0x33aa77)

      -- These set the surface and filler blocks.
      -- 3 = dirt
      :setTopBlock(3)
      -- 13 = gravel
      :setFillerBlock(13)

      -- These define the climate range for generation.
      :setTemperatureRange(0.6, 0.8)
      :setHumidityRange(0.7, 0.9)

      -- This sets tree generation behavior. Those are both optional.
      --
      -- Following tree generator modes are available:
      -- "default": default vanilla weighted big-tree generation.
      -- "big": only big trees spawn.
      -- "normal": only normal trees spawn.
      -- "none": no trees spawn.
      --
      -- Defaults to "default"
      :setTreeGenerator("default")
      -- Sets the chance for big trees to spawn when tree generator "default" is selected.
      -- Has no impact when any other tree generator is selected.  
      :setBigTreeChance(6)

      -- These configure mob spawns.
      :clearSpawns("monsters")
      :addSpawn("creatures", "Sheep", 12)

      -- finish the biome gen configuration for this biome.
      :finishBiomeGen()

    -- Commit all pending world-gen entries.
    :finishWorldGen()
end

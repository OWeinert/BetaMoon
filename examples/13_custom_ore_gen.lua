name = "Ore Gen Example"


function modInit()
  -- Create a block the same as before. 
  local oreBlock = betamoon.createBlock(203, "rock", "example_ore")
    :setHardness(3.0)
    :setResistance(5.0)
    :setTextureId(50)
    :setBlockHarvestLevel("pickaxe", 2)
    :setStepSound("stone")
    -- Deprecated legacy way (kept for reference).
    -- :addOreGen(10, 8, 0, 60)
    --   :setDimension("overworld")
    --   :setSpawnBlock(1)
    --   :setBiomes({})
    --   :finishOreGen()
    :register("Example Ore")

  -- World gen registration now uses a shared world-gen handle.
  betamoon.startWorldGen()
    -- The arguments are "block", "veinsPerChunk", "veinSize", "minY" and "maxY".
    :addOreGen(oreBlock, 10, 8, 0, 60)

      -- This sets the dimension where the block generates. [Optional]
      -- Defaults to "overworld".
      -- Choose "nether" or "hell" to let the ore generate in the nether.
      -- Choose "both" or "all" to let the ore generate in both overworld and nether.
      :setDimension("overworld")

      -- This sets which block the custom block is allowed to generate in. [Optional]
      :setSpawnBlock(1)

      -- This sets which biomes the block is allowed to generate in. [Optional]
      -- Providing an empty table/array is equal to not calling this method, meaning that all biomes are allowed.
      :setBiomes({})

      -- This will finish the ore gen configuration and return back to the world-gen handle.
      :finishOreGen()

    -- This commits all pending world-gen entries.
    :finishWorldGen()

  
end

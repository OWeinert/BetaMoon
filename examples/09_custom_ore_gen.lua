name = "Ore Gen Example"

dependencies = {}

function modInit()
  -- Create a block the same as before. 
  betamoon.createBlock(203, "rock")
    :setBlockName("example_ore")
    :setHardness(3.0)
    :setResistance(5.0)
    :setTextureId(50)
    :setBlockHarvestLevel("pickaxe", 2)
    :setStepSound("stone")
    
    -- This starts the ore gen configuration
    -- The arguments are "veinsPerChunk", "veinSize", "minY" and "maxY".
    -- 
    :addOreGen(10, 8, 0, 60)
      
      -- This sets the dimension where the block generates. [Optional]
      -- Defaults to "overworld"
      :setDimension("overworld")
      
      -- This sets which block the custom block is allowed to generate in. [Optional]
      :setSpawnBlock(1)
      
      -- This sets which biomes the block is allowed to generate in. [Optional]
      -- Providing an empty table/array is equal to not calling this method.
      :setBiomes({})

      -- This will finish the ore gen configuration and return back to the block configuration.
      :finishOreGen()
    :register("Example Ore")
end

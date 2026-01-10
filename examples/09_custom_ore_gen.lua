-- The name of your mod
name = "Mod Name"

-- A list of names of other Lua mods that have to be loaded before your mod.
-- This is needed in the case you want to add further functionality or content depending on the other mod's content.
dependencies = {}

-- This is the entrypoint of your mod.
-- Here you create and register any functionality or content your mod provides.
function modInit()
  -- Create a block the same as before. 
  betamoon.createBlock(201, "rock")
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

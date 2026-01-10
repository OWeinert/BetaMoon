-- The name of your mod
name = "Mod Name"

-- A list of names of other Lua mods that have to be loaded before your mod.
-- This is needed in the case you want to add further functionality or content depending on the other mod's content.
dependencies = {}

-- This is the entrypoint of your mod.
-- Here you create and register any functionality or content your mod provides.
function modInit()

  -- Create a block or item the same as before.  
  betamoon.createBlock(200, "rock")
    :setBlockName("example_block")
    
    -- This registers a custom texture for the block using the given relative path.
    -- The path is relative to your "./luamods" folder.
    -- This function is the same for blocks, items and tools.
    :addTexture("example_block.png")

    -- From here on just configure and register your block/item as normal.
    :setHardness(1.5)
    :setResistance(10.0)
    :setBlockHarvestLevel("pickaxe", 0)
    :setStepSound("stone")
    :register("Example Block")
end

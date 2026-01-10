name = "Custom Textures Example"

dependencies = {}

function modInit()

  -- Create a block or item the same as before.  
  betamoon.createBlock(201, "rock", "example_block")
    
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

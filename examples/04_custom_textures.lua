name = "Custom Textures Example"


function modInit()

  -- Create a block or item the same as before.  
  betamoon.createBlock(201, "rock", "example_block")
    
    -- This registers a custom texture for the block using the given relative path.
    -- The path is relative to your "./luamods" folder.
    -- This function is the same for blocks, items, tools and armor.
    :addTexture("example_block.png")

    -- From here on just configure and register your block/item/etc as normal.
    :setHardness(1.5)
    :setResistance(10.0)
    :setBlockHarvestLevel("pickaxe", 0)
    :setStepSound("stone")
    :register("Example Block")

  -- Per-side textures (top/bottom vanilla, sides custom).
  betamoon.createBlock(202, "rock", "example_block_sides")
    :setTextureMap({
      top = 0,
      bottom = 2,
      sides = "example_block.png"
    })
    :setHardness(1.5)
    :setResistance(10.0)
    :setBlockHarvestLevel("pickaxe", 0)
    :setStepSound("stone")
    :register("Example Block (Sides)")

  -- Per-side textures (front custom, top/bottom unique vanilla, remaining sides vanilla).
  betamoon.createBlock(203, "rock", "example_block_front")
    :setTextureMap({
      top = 1,
      bottom = 2,
      front = "example_block.png",
      sides = 3
    })
    :setHardness(1.5)
    :setResistance(10.0)
    :setBlockHarvestLevel("pickaxe", 0)
    :setStepSound("stone")
    :register("Example Block (Front)")
end

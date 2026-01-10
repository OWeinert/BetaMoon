name = "Custom Block Drop Example"

dependencies = {}

function modInit()
  -- Create a block the same as before. 
  betamoon.createBlock(202, "rock")
    :setBlockName("example_block2")
    :setHardness(3.0)
    :setResistance(5.0)
    :setTextureId(50)
    :setBlockHarvestLevel("pickaxe", 2)
    :setStepSound("stone")

    -- With this function we can add custom item/block drops.
    -- The function needs an Id for a block or item that will get dropped.
    -- minAmount and maxAmount are optional and when omitted the block always drops 1 of the given Id.
    :addCustomDrop(5000, 1, 4) -- This drops between 1-4 of item with Id 5000.
    -- :addCustomDrop(5000) also works and always drops 1 item of Id 5000.

    :register("Example Block 2")
end

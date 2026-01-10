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

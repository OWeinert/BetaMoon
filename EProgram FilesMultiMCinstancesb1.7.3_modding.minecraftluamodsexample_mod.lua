name = "Example Lua Mod"

dependencies = {}

function modInit()
  -- Example block
  local block = betamoon.createBlock(200, 1, "stone")
    :setBlockName("example_block")
    :setHardness(1.5)
    :setResistance(5.0)
    :setStepSound("stone")
    :register("Example Block")

  -- Example item
  local item = betamoon.createItem(5000, 2, 3)
    :setItemName("example_item")
    :setMaxStackSize(16)
    :setFull3D()
    :register("Example Item")
end

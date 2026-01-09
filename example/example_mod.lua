name = "Example Mod"

-- List of lua mod names which this mod depends on to be loaded before.
dependencies = {}

function modInit()
  -- Example block
  betamoon.createBlock(200, "rock")
    :setBlockName("example_block")
    :setHardness(1.5)
    :setResistance(10.0)
    :setBlockHarvestLevel("pickaxe", 0)
    :setStepSound("stone")
    :register("Example Block")

  -- Example ore that drops between 1-4 of "Example Item"
  betamoon.createBlock(201, "rock")
    :setBlockName("example_ore")
    :setHardness(3.0)
    :setResistance(5.0)
    :setTextureId(50)
    :setBlockHarvestLevel("pickaxe", 2)
    :setStepSound("stone")
    :addCustomDrop(5000, 1, 4)
    :register("Example Ore")

  -- Example item
  betamoon.createItem(5000)
    :setItemName("example_item")
    :setIconCoord(7, 3)
    :setMaxStackSize(16)
    :setFull3D()
    :register("Example Item")

  -- Example food item
  betamoon.createItem(5001)
    :setFood(4, false)
    :setItemName("example_food")
    :setIconCoord(11, 0)
    :setMaxStackSize(1)
    :setFull3D()
    :register("Example Food")

  -- Example Axe
  betamoon.createTool(5002, "diamond")
    :axe()
    :setItemName("example_axe")
    :setIconCoord(3, 7)
    :setMaxStackSize(1)
    :setFull3D()
    :register("Example Axe")

  -- Example Tool Material
  local example_tool_material = betamoon.createToolMaterial("EXAMPLE", 3, 2048, 7.0, 3)

  -- Example Pickaxe using custom Tool Material
  betamoon.createTool(5003, example_tool_material)
    :pickaxe()
    :setItemName("example_pickaxe")
    :setIconCoord(4, 6)
    :setMaxStackSize(1)
    :setFull3D()
    :register("Example Pickaxe")
end

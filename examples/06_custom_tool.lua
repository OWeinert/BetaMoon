name = "Custom Tool Example"

dependencies = {}

function modInit()

  -- Instead of createItem, you need to use createTool.
  -- In addition to the itemId you need to provide either the name of a tool material 
  -- or a ToolMaterial instance like created in 07_custom_tool_material.lua
  betamoon.createTool(5002, "diamond")
    
    -- You can call either pickaxe(), axe(), shovel(), hoe() or sword() to define which kind of tool you want to create.
    -- In this case we create an axe.
    :axe()

    -- Afterwards just configure your tool like any other item.
    :setItemName("example_axe")
    :setIconCoord(3, 7)
    :setMaxStackSize(1)
    :setFull3D()
    :register("Example Axe")
end

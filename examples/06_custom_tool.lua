-- The name of your mod
name = "Mod Name"

-- A list of names of other Lua mods that have to be loaded before your mod.
-- This is needed in the case you want to add further functionality or content depending on the other mod's content.
dependencies = {}

-- This is the entrypoint of your mod.
-- Here you create and register any functionality or content your mod provides.
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

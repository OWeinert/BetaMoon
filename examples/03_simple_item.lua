-- The name of your mod
name = "Mod Name"

-- A list of names of other Lua mods that have to be loaded before your mod.
-- This is needed in the case you want to add further functionality or content depending on the other mod's content.
dependencies = {}

-- This is the entrypoint of your mod.
-- Here you create and register any functionality or content your mod provides.
function modInit()

  -- Creates an ItemHandle which is used to configure your item.
  betamoon.createItem(5000)

    -- Sets the internal name of your item
    :setItemName("example_item")

    -- Sets the x/y coordinates of the item texture in the item texture atlas [Optional]
    :setIconCoord(7, 3)

    -- Sets the maximum stack size of the item [Optional]
    :setMaxStackSize(16)
    
    -- Enables the item to be rendered fully 3D when hold in hand [Optional]
    :setFull3D()
    
    -- Finally register the item and give it it's ingame name.
    :register("Example Item")
end

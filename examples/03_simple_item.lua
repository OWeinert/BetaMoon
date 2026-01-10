name = "Custom Item Example"

dependencies = {}

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

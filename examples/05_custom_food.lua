-- The name of your mod
name = "Mod Name"

-- A list of names of other Lua mods that have to be loaded before your mod.
-- This is needed in the case you want to add further functionality or content depending on the other mod's content.
dependencies = {}

-- This is the entrypoint of your mod.
-- Here you create and register any functionality or content your mod provides.
function modInit()

  -- Create a custom item like before  
  betamoon.createItem(5001)
    
    -- This sets the item to a food item. 
    -- The first argument is how many health it heals and the second is a boolean that defines if this food is suitable for wolfs.
    :setFood(4, false)
    
    -- From here on it's the same process as configuring and registering any other item.
    :setItemName("example_food")
    :setIconCoord(11, 0)
    :setMaxStackSize(1)
    :setFull3D()
    :register("Example Food")
end

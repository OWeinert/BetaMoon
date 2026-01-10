name = "Custom Food Example"

dependencies = {}

function modInit()

  -- Create a custom item like before  
  betamoon.createItem(5001, "example_food")
    
    -- This sets the item to a food item. 
    -- The first argument is how many health it heals and the second is a boolean that defines if this food is suitable for wolfs.
    :setFood(4, false)
    
    -- From here on it's the same process as configuring and registering any other item.
    :setIconCoord(11, 0)

    -- :setMaxStackSize(1) doesn't need to be called since :setFood(...) already sets the max stack size to 1.
    
    :register("Example Food")
end

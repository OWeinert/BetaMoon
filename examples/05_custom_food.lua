name = "Custom Food Example"
version = "2.0.0"
description = "Declares a food item and its food-specific properties."

function modInit()
  -- Set type to "food" to make an edible item.
  betamoon.items:add {
    id = 5001,
    type = "food",
    key = "example_food",
    displayName = "Example Food",
    icon = { x = 11, y = 0 },
    -- This food heals four points and cannot be fed to wolves.
    food = {
      healing = 4,
      wolfFood = false
    }
  }
end

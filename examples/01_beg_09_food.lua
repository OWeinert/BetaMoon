-- A food item uses the vanilla eating implementation, so no onUse callback is needed.
-- Beta 1.7.3 food restores health directly: two health points make one heart.
-- Obtain item 5001 for testing and right-click while injured to see the healing.
-- As in the preceding examples, this file registers an item but does not add a recipe.

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
      -- Four health points restore two hearts. wolfFood controls whether
      -- this food is accepted for feeding wolves.
      healing = 4,
      wolfFood = false
    }
  }
end

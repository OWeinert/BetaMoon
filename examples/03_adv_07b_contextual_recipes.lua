-- Load 07a with this file, and 07c to use these recipes in-game.
-- The same input/output shape can describe very different operating conditions.

name = "Contextual Recipes Example"
version = "1.0.0"
description = "Adds cold and hot recipes with explicit heat and power conditions."
dependencies = { "Contextual Recipe Type Example" }

function modInit()
  local recipeType = betamoon.modules:import("contextual_recipe_types").thermalProcessing

  -- Dirt becomes clay only while the machine is cool and unpowered.
  betamoon.recipes:add {
    key = "example:cold_clay_forming",
    type = recipeType,
    ingredients = { input = betamoon.blocks:getRequired(3) },
    output = betamoon.stack(betamoon.items:getRequired(337), 4),
    data = { duration = 60 },
    conditions = {
      heat = { max = 100 },
      powered = false
    }
  }

  -- Sand needs both sustained heat and an active redstone input.
  betamoon.recipes:add {
    key = "example:powered_glass_fusing",
    type = recipeType,
    ingredients = { input = betamoon.blocks:getRequired(12) },
    output = betamoon.stack(betamoon.blocks:getRequired(20), 2),
    data = { duration = 100 },
    conditions = {
      heat = { min = 400 },
      powered = true
    }
  }
end

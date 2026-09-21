-- Load 07a with this file, and 07c to use these recipes in-game.
-- The same input/output shape can describe very different operating conditions.

name = "Contextual Recipes Example"
version = "1.0.0"
description = "Adds two Thermal Processing recipes with different heat and power requirements. Load advanced " ..
    "example 07a for the recipe type and 07c for the Contextual Processor that runs them.\n\n" ..
    "With no redstone power and heat at 100 or below, one dirt produces four clay balls after 60 " ..
    "ticks, about three seconds. No fuel is needed for this cold recipe. With redstone power on " ..
    "and heat at 400 or above, one sand produces two glass after 100 ticks, about five seconds.\n\n" ..
    "Use the processor's fuel slot to heat it for glass, and watch its heat and Powered " ..
    "indicators. Removing power immediately invalidates the glass recipe. For clay after a hot " ..
    "run, let existing fuel finish burning and wait for heat to fall to 100 or below. Compare the " ..
    "exact thresholds and durations here with the machine's visible state."
dependencies = { "Contextual Recipe Type Example" }

function modInit()
  local recipeType = betamoon.modules:import("example:module/contextual_recipe_types").thermalProcessing

  -- Dirt becomes clay only while the machine is cool and unpowered.
  betamoon.recipes:add {
    key = "example:recipe/cold_clay_forming",
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
    key = "example:recipe/powered_glass_fusing",
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

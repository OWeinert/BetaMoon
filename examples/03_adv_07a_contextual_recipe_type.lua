-- A contextual recipe type separates inventory requirements from machine state.
-- Recipes declare accepted heat/power ranges; a machine supplies current values
-- when matching and supplies fresh values again when applying the transaction.

name = "Contextual Recipe Type Example"
version = "1.0.0"
description = "Declares a thermal recipe type whose matches depend on heat and redstone power."

function modInit()
  local public = {}

  public.thermalProcessing = betamoon.recipeTypes:add {
    name = "example:thermal_processing",
    displayName = "Thermal Processing",
    ingredients = {
      input = { type = "item" }
    },
    outputs = {
      result = { type = "item" }
    },
    data = {
      duration = { type = "integer", default = 100, min = 1, max = 1200 }
    },
    context = {
      heat = { type = "integer", min = 0, max = 1000 },
      powered = { type = "boolean" }
    }
  }

  betamoon.modules:export("contextual_recipe_types", public)
end

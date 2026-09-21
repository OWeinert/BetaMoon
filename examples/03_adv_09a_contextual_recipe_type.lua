-- A contextual recipe type separates inventory requirements from machine state.
-- Recipes declare accepted heat/power ranges; a machine supplies current values
-- when matching and supplies fresh values again when applying the transaction.

name = "Contextual Recipe Type Example"
version = "1.0.0"
description = "Defines Thermal Processing, a recipe type whose requirements include the machine's current " ..
    "heat and redstone power as well as its input item. It exports the type for the other two " ..
    "contextual-recipe examples.\n\n" ..
    "Load advanced examples 07a, 07b, and 07c together. This file alone adds no machine or " ..
    "recipes. In the companion Contextual Processor, dirt becomes clay while cool and unpowered, " ..
    "whereas sand becomes glass only while hot and powered.\n\n" ..
    "Compare the heat and powered context fields here with the conditions declared in 07b and the " ..
    "current values supplied by 07c. The machine checks those values again before consuming inputs " ..
    "and producing the result, so finding a recipe earlier does not bypass its operating " ..
    "conditions."

function modInit()
  local public = {}

  public.thermalProcessing = betamoon.recipeTypes:add {
    name = "example:recipe_type/thermal_processing",
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

  betamoon.modules:export("example:module/contextual_recipe_types", public)
end

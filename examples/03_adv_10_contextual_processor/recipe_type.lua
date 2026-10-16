-- Private registration module for the Contextual Processor recipe type.
-- Returning a function keeps registration inside the package modInit lifecycle.

return function()
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

  return public
end

-- Private recipe module for the Contextual Processor.
-- The entrypoint supplies the handles registered by its private type module.

return function(recipeType)

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

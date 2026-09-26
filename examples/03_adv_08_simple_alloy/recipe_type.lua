-- Private registration module for the Simple Alloy recipe type.
-- Returning a function keeps registration inside the package modInit lifecycle.

return function()
  local public = {}

  -- Logical roles are independent of a machine's physical inventory slots.
  -- Equivalent declarations reuse the same type after reload. Schema changes
  -- require a restart, as does this package's structural machine content.
  public.alloying = betamoon.recipeTypes:add {
    name = "example:recipe_type/alloying",
    displayName = "Alloying",
    ingredients = {
      base = { type = "item" },
      additive = { type = "item" },
      -- optional lets a recipe omit this role. If a recipe DOES specify a mold,
      -- the mold must be present; consume = false keeps it after processing.
      mold = { type = "item", optional = true, consume = false }
    },
    outputs = {
      result = { type = "item" },
      slag = { type = "item", optional = true }
    },
    primaryOutput = "result",
    -- This validates a recipe property and supplies a default when omitted.
    -- It does not run a timer: the machine chooses to interpret duration as ticks.
    data = { duration = { type = "integer", default = 160, min = 1 } }
  }

  return public
end

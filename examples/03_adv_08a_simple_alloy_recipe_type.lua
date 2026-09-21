-- A recipe TYPE defines allowed ingredient/output roles and extra data fields.
-- A recipe INSTANCE, added by the companion recipe file, fills those roles with actual items.
-- This file creates no machine or processing loop; the companion furnace file supplies those.
-- Roles are semantic names such as base and result, not inventory indexes or GUI labels.
-- Keeping type definitions separate lets several machines agree on one recipe contract.

name = "Simple Alloy Recipe Type Example"
version = "1.0.0"
description = "Defines the Alloying recipe type used by the three-part Simple Alloy example. It separates a " ..
    "base ingredient, additive, optional reusable mold, main result, optional slag, and processing " ..
    "duration into named roles.\n\n" ..
    "Load advanced examples 06a, 06b, and 06c together to try the Alloy Furnace. This file alone " ..
    "adds no machine or processable recipe. In the companion machine, gold ore plus coal or " ..
    "charcoal and a retained stick mold produces three gold ingots and cobblestone; a separate " ..
    "recipe compresses four dirt or cobblestone with coal into stone.\n\n" ..
    "Compare these roles with the concrete recipes in 06b and their physical inventory-slot " ..
    "bindings in 06c. A mold is optional for the type, but a recipe that declares one still " ..
    "requires it. Duration is recipe data that the machine interprets as ticks."

function modInit()
  local public = {}

  -- Logical roles are independent of a machine's physical inventory slots.
  -- Equivalent declarations reuse the same type after reload. Schema changes
  -- require a restart, but this callback-free script remains reloadable.
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

  betamoon.modules:export("example:module/custom_recipe_types", public)
end

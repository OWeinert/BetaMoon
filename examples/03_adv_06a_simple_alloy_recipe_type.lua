-- A recipe TYPE defines allowed ingredient/output roles and extra data fields.
-- A recipe INSTANCE, added by the companion recipe file, fills those roles with actual items.
-- This file creates no machine or processing loop; the companion furnace file supplies those.
-- Roles are semantic names such as base and result, not inventory indexes or GUI labels.
-- Keeping type definitions separate lets several machines agree on one recipe contract.

name = "Simple Alloy Recipe Type Example"
version = "1.0.0"
description = "Declares a simple named-slot alloy recipe type; advanced example 06c supplies the machine."

function modInit()
  local public = {}

  -- Logical roles are independent of a machine's physical inventory slots.
  -- Equivalent declarations reuse the same type after reload. Schema changes
  -- require a restart, but this callback-free script remains reloadable.
  public.alloying = betamoon.recipeTypes:add {
    name = "example:alloying",
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

  betamoon.modules:export("custom_recipe_types", public)
end

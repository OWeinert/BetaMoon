-- Load the companion type file too. To process alloy recipes in-game, also load the furnace file.
-- The imported type handles let the existing recipes:add API validate custom recipes.
-- Each key identifies one recipe; changing its output/duration does not require inventing a new type.
-- Read ingredient values as matching requirements, and output values as produced stacks.
-- This file can reload independently of the alloy machine's persistent inventory.

name = "Simple Alloy Recipes Example"
version = "1.0.0"
description = "Recipes for the custom recipe type \"Alloying\""
dependencies = { "Simple Alloy Recipe Type Example" }

function modInit()
  -- bm is only a short local alias for betamoon; it is not a different API.
  local bm = betamoon
  local types = bm.modules:import("custom_recipe_types")

  -- Recipe declarations stay in this separate file so machine callbacks and
  -- persistent inventories remain loaded while these recipes are reloaded.

  -- Gold processing uses a stick as a reusable mold.
  -- Coal and charcoal both match because this ingredient explicitly ignores damage.
  bm.recipes:add {
    key = "example:alloy_gold",
    type = types.alloying,
    ingredients = {
      -- A stack is accepted directly as an exact ingredient. The additive uses
      -- an ingredient table instead, because it needs the damage = "any" rule.
      -- For coal, damage distinguishes ordinary coal from charcoal.
      base = bm.stack(bm.blocks:getRequired(14)),
      additive = { item = bm.items:getRequired(263), damage = "any" },
      mold = bm.items:getRequired(280)
    },
    outputs = { result = bm.stack(bm.items:getRequired(266), 3), slag = bm.blocks:getRequired(4) },
    data = { duration = 200 }
  }

  -- Alternatives belong to one role; their quantity is declared once.
  bm.recipes:add {
    key = "example:compress_building_material",
    type = types.alloying,
    ingredients = {
      base = { anyOf = { bm.blocks:getRequired(3), bm.blocks:getRequired(4) }, count = 4 },
      additive = bm.items:getRequired(263)
    },
    -- output is shorthand for the type's primary result role. Slag and mold
    -- are omitted in this recipe because their roles were declared optional.
    output = bm.blocks:getRequired(1),
    data = { duration = 80 }
  }

end

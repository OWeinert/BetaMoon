-- Private recipe module for Simple Alloy.
-- The entrypoint supplies the handles registered by its private type module.

return function(types)
  -- bm is only a short local alias for betamoon; it is not a different API.
  local bm = betamoon

  -- Recipe declarations stay separate from machine callbacks and inventory
  -- layout even though all files share one package lifecycle and owner.

  -- Gold processing uses a stick as a reusable mold.
  -- Coal and charcoal both match because this ingredient explicitly ignores damage.
  bm.recipes:add {
    key = "example:recipe/alloy_gold",
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
    key = "example:recipe/compress_building_material",
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

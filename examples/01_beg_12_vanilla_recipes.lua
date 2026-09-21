-- This example makes its own item, then keeps the returned reference in local dust.
-- A reference identifies registered content; betamoon.stack(reference, count, damage)
-- describes a quantity for a recipe. Creating that description does not spawn items.
-- Try a 2x2 square of iron ingots, diamond + coal, or smelting dirt in a vanilla furnace.
-- No earlier custom-content example is required: the ingredients are vanilla resources.

name = "Custom Recipe Examples"
version = "2.0.0"
description = "Adds Example Dust and three ways to produce it using vanilla crafting and smelting. This " ..
    "example is self-contained and demonstrates shaped, shapeless, and furnace recipes.\n\n" ..
    "Arrange four iron ingots in a 2x2 square to craft four dust. Combine one diamond and one coal " ..
    "in any arrangement to craft two dust. Alternatively, put dirt into a furnace with normal fuel " ..
    "to smelt one dust per dirt block. Compare the ingredient arrangement, output quantity, and " ..
    "recipe type with each declaration. The dust itself has no special use action."

function modInit()
  local dust = betamoon.items:add {
    id = 5014,
    key = "example:item/example_dust",
    displayName = "Example Dust",
    icon = { x = 8, y = 3 }
  }

  -- stack says that this recipe makes four dust.
  -- In a shaped recipe, each character in the pattern stands for an ingredient.
  betamoon.recipes:add {
    type = "shaped",
    output = betamoon.stack(dust, 4),
    pattern = {
      "##",
      "##"
    },
    -- The # entry assigns an ingredient to the # cells in the pattern.
    ingredients = {
      ["#"] = betamoon.items:getRequired(265)
    }
  }

  -- A shapeless recipe works no matter where the ingredients are placed.
  -- Every list entry is one required item unless a stack gives a larger count.
  betamoon.recipes:add {
    type = "shapeless",
    output = betamoon.stack(dust, 2),
    ingredients = {
      betamoon.items:getRequired(264),
      betamoon.items:getRequired(263)
    }
  }

  -- A smelting recipe turns one item or block into another in a furnace.
  betamoon.recipes:add {
    type = "smelting",
    -- Vanilla ID 3 is dirt. This adds a furnace recipe, not a crafting recipe;
    -- the normal furnace still supplies its own fuel and processing time.
    input = betamoon.blocks:getRequired(3),
    output = betamoon.stack(dust)
  }
end

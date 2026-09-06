name = "Custom Recipe Examples"
version = "2.0.0"
description = "Declares shaped, shapeless, and smelting recipes."

function modInit()
  local dust = betamoon.items:add {
    id = 5014,
    key = "example_dust",
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
    ingredients = {
      ["#"] = betamoon.items:getRequired(265)
    }
  }

  -- A shapeless recipe works no matter where the ingredients are placed.
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
    input = betamoon.blocks:getRequired(3),
    output = betamoon.stack(dust)
  }
end

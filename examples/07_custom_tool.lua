name = "Custom Tool Example"
version = "2.0.0"
description = "Declares every supported tool type."

function modInit()
  -- BetaMoon can create axes, pickaxes, shovels, hoes, and swords.
  local toolTypes = {
    "axe",
    "pickaxe",
    "shovel",
    "hoe",
    "sword"
  }

  for index, toolType in ipairs(toolTypes) do
    -- This loop creates one diamond tool of every type in the list.
    betamoon.tools:add {
      id = 5001 + index,
      type = toolType,
      material = "diamond",
      key = "example_" .. toolType,
      displayName = "Example " .. toolType,
      -- full3D makes the item look like a normal tool when held.
      full3D = true,
      icon = { x = index - 1, y = 7 }
    }
  end
end

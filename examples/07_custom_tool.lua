name = "Custom Tool Example"
version = "2.1.0"
description = "Declares a custom tool material and every supported tool type."

function modInit()
  -- A tool material controls mining level, uses, speed, and attack damage.
  -- Create it once, then use the returned material for every tool in the set.
  local exampleMaterial = betamoon.materials.tools:add {
    key = "EXAMPLE_TOOLS",
    harvestLevel = 3,
    durability = 2048,
    efficiency = 7,
    damage = 3
  }

  -- require can find the same material later by its key.
  assert(betamoon.materials.tools:require("EXAMPLE_TOOLS") == exampleMaterial)

  -- BetaMoon can create axes, pickaxes, shovels, hoes, and swords.
  local toolTypes = {
    "sword",
    "shovel",
    "pickaxe",
    "axe",
    "hoe"
  }

  for index, toolType in ipairs(toolTypes) do
    -- This loop creates one tool of every type with the custom material.
    betamoon.tools:add {
      id = 5001 + index,
      type = toolType,
      material = exampleMaterial,
      key = "example_" .. toolType,
      displayName = "Example " .. toolType,
      -- full3D makes the item look like a normal tool when held.
      full3D = true,
      icon = { x = 3, y = 3 + index }
    }
  end
end

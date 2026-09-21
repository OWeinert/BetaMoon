-- A tool material is a reusable set of mining/durability settings, not a texture
-- or a block material. The tool type still chooses vanilla sword/pickaxe/etc. actions.
-- This example creates five tools, IDs 5002-5006, using one material definition.
-- Obtain them with an inventory tool or use the vanilla recipe example to make them craftable.

name = "Custom Tool Example"
version = "2.1.0"
description = "Adds an Example sword, shovel, pickaxe, axe, and hoe that share a custom tool material. The " ..
    "material supplies their harvesting level, durability, mining efficiency, and damage settings.\n\n" ..
    "No crafting recipes are included. Obtain item IDs 5002 through 5006 with an inventory editor " ..
    "or item-spawning tool. Try the sword in combat, the shovel on dirt, the pickaxe on stone and " ..
    "ores, the axe on wood, and the hoe on grass or dirt. Compare the shared material settings " ..
    "with the distinct behavior supplied by each tool type."

function modInit()
  -- Create one material, then use it for every tool in the set. harvestLevel
  -- chooses the strongest blocks it can harvest. durability is its number of
  -- uses, efficiency is suitable-block mining speed, and damage adds attack power.
  local exampleMaterial = betamoon.materials.tools:add {
    key = "example:tool_material/example_tools",
    harvestLevel = 3,
    durability = 2048,
    efficiency = 7,
    damage = 3
  }

  -- getRequired can find the same material later by its key.
  assert(betamoon.materials.tools:getRequired("example:tool_material/example_tools") == exampleMaterial)

  -- BetaMoon can create axes, pickaxes, shovels, hoes, and swords.
  local toolTypes = {
    "sword",
    "shovel",
    "pickaxe",
    "axe",
    "hoe"
  }

  -- The changing index gives each generated tool a distinct numeric ID and icon.
  for index, toolType in ipairs(toolTypes) do
    -- This loop creates one tool of every type with the custom material.
    betamoon.tools:add {
      id = 5001 + index,
      type = toolType,
      material = exampleMaterial,
      key = "example:item/example_" .. toolType,
      displayName = "Example " .. toolType,
      -- full3D makes the item look like a normal tool when held.
      full3D = true,
      -- x and y choose the inventory icon's column and row in the item atlas.
      icon = { x = 3, y = 3 + index }
    }
  end
end

-- Items, tools, and armor can be searched through their registries.
-- These lookups describe registered item types; they do not inspect a player's inventory.
-- Display-name searches are useful for exploration but depend on the visible name.
-- Prefer a known ID/key when another part of your script must find one exact resource.

name = "Item Registry Query Examples"
version = "2.0.0"
description = "Shows item, tool, and armor registry queries."

function modInit()
  local iron = betamoon.items:getRequired(265)
  -- You can search for items within a damage-value range.
  local matches = betamoon.items:find {
    displayName = "Iron Ingot",
    damage = { min = 0, max = 0 }
  }
  -- The tools and armor lists only search those kinds of items.
  local pickaxe = betamoon.tools:first {
    type = "pickaxe"
  }
  local helmet = betamoon.armor:first {}
  assert(matches:first().id == iron.id)
  -- or short-circuits: if there is no pickaxe, its category is not read.
  -- This is a common Lua pattern for checking optional query results.
  assert(not pickaxe or pickaxe.category == "tool")
  assert(not helmet or helmet.category == "armor")
end

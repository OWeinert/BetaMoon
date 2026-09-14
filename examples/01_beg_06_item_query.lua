-- Items, tools, and armor can be searched through their registries.
-- These lookups describe registered item types; they do not inspect a player's inventory.
-- Display-name searches are useful for exploration but depend on the visible name.
-- Prefer a known ID/key when another part of your script must find one exact resource.

name = "Item Registry Query Examples"
version = "2.0.0"
description = "Demonstrates looking up items and filtering the item registry by properties such as ID, " ..
    "display name, damage, and ownership. It includes searches for existing iron items, pickaxes, " ..
    "and helmets.\n\n" ..
    "No items, recipes, or interactions are added. The script checks its query results during " ..
    "initialization and stays silent when they are correct. Read the individual queries alongside " ..
    "the vanilla items they find to see how a single lookup differs from a filtered collection and " ..
    "its first or last result."

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

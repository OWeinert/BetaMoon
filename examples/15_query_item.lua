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
  assert(not pickaxe or pickaxe.category == "tool")
  assert(not helmet or helmet.category == "armor")
end

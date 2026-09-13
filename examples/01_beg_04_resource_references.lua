-- Registries return references to blocks and items that already exist.
-- References identify content; they do not create inventory stacks or world blocks.
-- Use get for optional content and getRequired when absence should stop the script.

name = "Resource References Example"
version = "1.0.0"
description = "Shows safe resource lookup, reference properties, and stack descriptions."

function modInit()
  -- Numeric IDs and registered names both work. The minecraft namespace makes
  -- ownership explicit, although an unqualified vanilla name is also accepted.
  local stone = betamoon.blocks:getRequired("minecraft:stone")
  local iron = betamoon.items:getRequired(265)

  -- get returns nil when optional content is absent. This lets scripts support
  -- another mod without failing when that mod is not installed.
  local optional = betamoon.items:get("example:optional_item")
  assert(optional == nil)

  -- Every reference exposes stable identity and display information.
  assert(stone.id == 1)
  assert(stone.damage == 0)
  assert(stone.owner == "minecraft")
  assert(stone.isVanilla and not stone.isBetaMoon and stone.exists)
  assert(type(stone.name) == "string")
  assert(type(stone.displayName) == "string")

  -- A newly registered resource is owned by this BetaMoon script. The returned
  -- token reference describes the registered item type.
  local token = betamoon.items:add {
    id = 5024,
    key = "reference_token",
    displayName = "Reference Token",
    icon = { x = 7, y = 3 }
  }
  assert(token.isBetaMoon and not token.isVanilla)

  -- A stack is a detached description containing an item, amount, and damage.
  -- Recipe and inventory APIs accept it; creating one does not add items.
  local stack = betamoon.stack(iron, 4)
  assert(stack.id == iron.id)
  assert(stack.item == iron)
  assert(stack.count == 4 and stack.damage == iron.damage)
end

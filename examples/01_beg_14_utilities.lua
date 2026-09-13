-- These helpers construct values that other APIs accept. They do not place blocks,
-- teleport players, or add items to an inventory by themselves.
-- The assertions below are executable checks: a failed condition reports a script error.
-- Successful assertions are silent, so this example intentionally has no visible effect.

name = "Utility API Examples"
version = "2.0.0"
description = "Shows stack and position helpers."

function modInit()
  -- stack describes an item or block, its amount, and its damage value.
  local stack = betamoon.stack(
    betamoon.blocks:getRequired(1),
    4,
    0
  )
  -- Use integer positions for blocks and float positions for exact locations.
  local blockPosition = betamoon.positions:integer(10, 64, 10)
  local precisePosition = betamoon.positions:float(10.5, 64, 10.5)

  -- A stack description uses count; the third stack argument above is damage.
  -- For some items damage selects a subtype, while tools use it as wear.
  assert(stack.count == 4)
  assert(blockPosition.x == 10)
  assert(precisePosition.x == 10.5)
end

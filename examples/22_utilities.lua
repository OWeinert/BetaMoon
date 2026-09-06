name = "Utility API Examples"
version = "2.0.0"
description = "Shows stack and position helpers."

function modInit()
  -- stack describes an item or block, its amount, and its damage value.
  local stack = betamoon.stack(
    betamoon.blocks:require(1),
    4,
    0
  )
  -- Use integer positions for blocks and float positions for exact locations.
  local blockPosition = betamoon.positions:integer(10, 64, 10)
  local precisePosition = betamoon.positions:float(10.5, 64, 10.5)

  assert(stack.count == 4)
  assert(blockPosition.x == 10)
  assert(precisePosition.x == 10.5)
end

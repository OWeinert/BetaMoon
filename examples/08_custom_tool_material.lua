name = "Custom Tool Material Example"
version = "2.0.0"
description = "Declares and retrieves a custom tool material."

function modInit()
  -- A tool material controls mining level, uses, speed, and attack damage.
  local material = betamoon.materials.tools:add {
    key = "EXAMPLE",
    harvestLevel = 3,
    durability = 2048,
    efficiency = 7,
    damage = 3
  }

  -- require finds the material again by its key.
  assert(betamoon.materials.tools:require("EXAMPLE") == material)
end

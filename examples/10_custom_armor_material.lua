name = "Custom Armor Material Example"
version = "2.0.0"
description = "Declares a custom armor material and uses it."

function modInit()
  -- An armor material controls how much protection armor gives.
  local material = betamoon.materials.armor:add {
    key = "EXAMPLE_MATERIAL",
    protection = 2
  }

  -- Use the new material when creating an armor piece.
  betamoon.armor:add {
    id = 5010,
    material = material,
    slot = "leggings",
    key = "example_leggings",
    displayName = "Example Leggings",
    renderIndex = "iron",
    icon = { x = 2, y = 2 }
  }
end

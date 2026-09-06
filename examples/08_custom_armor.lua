name = "Custom Armor Example"
version = "2.1.0"
description = "Declares a custom armor material and a complete armor set with custom textures."

function modInit()
  -- An armor material controls how much protection the armor gives.
  -- Create it once, then use the returned material for every piece in the set.
  local exampleMaterial = betamoon.materials.armor:add {
    key = "EXAMPLE_ARMOR",
    protection = 2
  }

  -- texture changes the small item picture. modelTexture changes the armor worn by a player.
  -- Item pictures are normally 16x16 pixels. Worn armor layers are 64x32 pixels.
  -- Helmets, chestplates, and boots use layer 1. Leggings use layer 2.
  -- modelTexture replaces renderIndex, so the two settings cannot be used together.
  betamoon.armor:add {
    id = 5008,
    material = exampleMaterial,
    slot = "helmet",
    key = "example_helmet",
    displayName = "Example Helmet",
    texture = "example_armor_helmet.png",
    modelTexture = "example_armor_layer_1.png"
  }

  betamoon.armor:add {
    id = 5009,
    material = exampleMaterial,
    slot = "chestplate",
    key = "example_chestplate",
    displayName = "Example Chestplate",
    texture = "example_armor_chestplate.png",
    modelTexture = "example_armor_layer_1.png"
  }

  betamoon.armor:add {
    id = 5011,
    material = exampleMaterial,
    slot = "leggings",
    key = "example_leggings",
    displayName = "Example Leggings",
    texture = "example_armor_leggings.png",
    modelTexture = "example_armor_layer_2.png"
  }

  betamoon.armor:add {
    id = 5012,
    material = exampleMaterial,
    slot = "boots",
    key = "example_boots",
    displayName = "Example Boots",
    texture = "example_armor_boots.png",
    modelTexture = "example_armor_layer_1.png"
  }
end

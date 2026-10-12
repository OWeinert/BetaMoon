-- Copy all six example_armor_*.png files alongside this script before loading it.
-- The four inventory icons and the two worn-model layers serve different purposes.
-- This script registers a full set; obtain its items or use the vanilla recipe example,
-- then equip them to compare the inventory pictures with the armor on the player.

name = "Custom Armor Example"
version = "2.1.0"
description = "Adds a complete set of Example armor with a custom armor material, separate inventory icons, " ..
    "and textures for the worn equipment. Copy the example_armor_helmet, chestplate, leggings, " ..
    "boots, layer_1, and layer_2 PNG files from the examples into your scripts folder, preserving " ..
    "their full filenames.\n\n" ..
    "No recipes are included. Obtain the helmet (5008), chestplate (5009), leggings (5011), and " ..
    "boots (5012) with an inventory editor or item-spawning tool. Equip them in their matching " ..
    "armor slots and inspect your character in the inventory or third-person view. Compare the " ..
    "item icons with the two armor-layer textures used on the player."

function modInit()
  -- An armor material controls how much protection each piece gives. A larger
  -- protection value reduces more incoming damage.
  -- Create it once, then use the returned material for every piece in the set.
  local exampleMaterial = betamoon.materials.armor:add {
    key = "example:armor_material/example_armor",
    protection = 2
  }

  -- texture changes the small item picture. modelTexture changes the armor worn by a player.
  -- Item pictures are normally 16x16 pixels. Worn armor layers are 64x32 pixels.
  -- Helmets, chestplates, and boots use layer 1. Leggings use layer 2.
  -- modelTexture replaces renderIndex, so the two settings cannot be used together.
  betamoon.armor:add {
    -- slot decides where the piece can be equipped.
    id = 5008,
    material = exampleMaterial,
    slot = "helmet",
    key = "example:item/example_helmet",
    displayName = "Example Helmet",
    texture = "example_armor_helmet.png",
    modelTexture = "example_armor_layer_1.png"
  }

  betamoon.armor:add {
    id = 5009,
    material = exampleMaterial,
    slot = "chestplate",
    key = "example:item/example_chestplate",
    displayName = "Example Chestplate",
    texture = "example_armor_chestplate.png",
    modelTexture = "example_armor_layer_1.png"
  }

  betamoon.armor:add {
    id = 5011,
    material = exampleMaterial,
    -- Leggings need layer 2 of the worn texture layout even though all pieces share one material.
    slot = "leggings",
    key = "example:item/example_leggings",
    displayName = "Example Leggings",
    texture = "example_armor_leggings.png",
    modelTexture = "example_armor_layer_2.png"
  }

  betamoon.armor:add {
    id = 5012,
    material = exampleMaterial,
    slot = "boots",
    key = "example:item/example_boots",
    displayName = "Example Boots",
    texture = "example_armor_boots.png",
    modelTexture = "example_armor_layer_1.png"
  }
end

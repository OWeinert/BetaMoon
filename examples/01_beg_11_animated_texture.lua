-- Copy example/textures/shared/animated_crystal.png with its folders beside this script.
-- This builds on 01_beg_10_registered_texture.lua: one registered texture is
-- shared by a block and an item, with animation added to the texture asset.
-- The PNG is 16 pixels wide and 64 pixels tall: four 16x16 pictures stacked
-- from top to bottom. Each picture is one animation frame.

name = "Animated Texture Example"
version = "1.0.0"
description = "Adds a pulsing crystal block (241) and item (5048). Copy the example " ..
    "folder beside this script, then find both in the creative inventory. " ..
    "The same four-frame texture animates in the world, inventory, and hand."

function modInit()
  -- With no path field, this key finds example/textures/shared/animated_crystal.png.
  local crystal = betamoon.assets.textures:add {
    key = "example:shared/animated_crystal",
    animation = {
      -- Read square frames from top to bottom. This is the default and the
      -- currently supported layout.
      layout = "vertical",
      -- Hold each sequence entry for 3 client ticks (about 0.15 seconds).
      frameTicks = 3,
      -- Frame numbers start at 1, so 1 is the top picture and 4 the bottom.
      -- Play forward, then backward, and loop back to 1. Without sequence,
      -- BetaMoon would play 1, 2, 3, 4, then repeat.
      sequence = { 1, 2, 3, 4, 3, 2 },
      -- Stop advancing while the game is paused. Use "client" instead to
      -- continue in menus and paused screens.
      clock = "unpaused"
    }
  }

  -- Both registrations use the same asset, so their animation stays in sync.
  betamoon.blocks:add {
    id = 241, key = "example:block/lesson_animated_crystal", displayName = "Pulsing Crystal",
    material = betamoon.mc.blockMaterials.rock, texture = crystal,
    harvest = { pickaxe = 0 }
  }

  betamoon.items:add {
    id = 5048, key = "example:item/lesson_animated_crystal", displayName = "Pulsing Crystal",
    texture = crystal
  }
end

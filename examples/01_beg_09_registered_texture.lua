-- Copy registered_texture/ beside this script in lua_scripts.
-- One registered PNG can be reused by both a block and an item.

name = "Registered Texture Example"
version = "1.0.0"
description = "Adds a blue mosaic block (232) and matching item (5030). Copy the " ..
    "registered_texture folder beside this script, then obtain both IDs through a creative " ..
    "inventory. The asset reference is shared; its override path tells texture-pack authors " ..
    "exactly where a replacement PNG belongs."

function modInit()
  local mosaic = betamoon.assets.textures:add {
    key = "mymod:lessons/mosaic",
    path = "registered_texture/mosaic.png"
  }

  betamoon.blocks:add {
    id = 232, key = "lesson_mosaic_block", displayName = "Mosaic Block",
    material = betamoon.mc.blockMaterials.rock, texture = mosaic
  }

  betamoon.items:add {
    id = 5030, key = "lesson_mosaic_token", displayName = "Mosaic Token",
    texture = mosaic
  }

  betamoon.chat:send("Mosaic texture pack path: %s", mosaic:getOverridePath())
end

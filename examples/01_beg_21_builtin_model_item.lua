-- Copy builtin_model_item/ beside this script in lua_scripts.
-- Built-in models need no model registration. They still need a texture binding.

name = "Built-in Model Item Example"
version = "1.0.0"
description = "Adds a small stone sample (5031) using BetaMoon's built-in Beta 1.7.3 " ..
    "slab model. Copy builtin_model_item/ beside this script, then obtain ID 5031 and compare " ..
    "the model in inventory, in hand, and after dropping it."

function modInit()
  local stone = betamoon.assets.textures:add {
    key = "example:item/slab_stone",
    path = "builtin_model_item/slab_stone.png"
  }

  betamoon.items:add {
    id = 5031, key = "example:item/lesson_slab_sample", displayName = "Slab Model Item",
    appearance = {
      model = "minecraft:block/slab",
      texture = stone,
      display = {
        gui = { scale = 0.85 },
        held = { scale = 0.75 },
        ground = { scale = 0.65 }
      }
    }
  }
end

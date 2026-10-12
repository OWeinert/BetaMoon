-- Copy entity_examples/ beside this script in lua_scripts.
-- Obtain the Pebble Launcher (5037); cobblestone is its ammunition.

name = "Custom Projectile Example"
version = "1.0.0"
description = "Launches a model-backed pebble with its own speed, gravity, impact, " ..
    "ammunition and cooldown. Copy entity_examples/ beside this script."

function modInit()
  local model = betamoon.assets.models:add {
    key = "example:entity/pebble",
    path = "entity_examples/pebble.json"
  }
  local texture = betamoon.assets.textures:add {
    key = "example:entity/pebble",
    path = "entity_examples/stone.png"
  }

  local pebble = betamoon.entities:add {
    key = "example:entity/pebble",
    kind = "projectile",
    width = 0.25,
    height = 0.25,
    appearance = { model = model, texture = texture },
    projectile = {
      speed = 1.8,
      gravity = 0.04,
      drag = 0.99,
      lifetimeTicks = 100,
      damage = 2
    },
    onImpact = function(ctx)
      ctx.world:playSound("random.pop", 0.5, 1.2)
      return "default"
    end
  }

  betamoon.items:add {
    id = 5037,
    key = "example:item/lesson_pebble_launcher",
    displayName = "Pebble Launcher",
    icon = { x = 1, y = 0 },
    use = { projectile = pebble, ammunition = 4, cooldown = 10 }
  }
end

-- Copy animated_model_item/ beside this script in lua_scripts.
-- Obtain the Clockwork Watcher Placer (5039) and place it on a block.

name = "Predefined Living AI Example"
version = "1.0.0"
description = "A clockwork watcher with health, native wandering and retaliation. " ..
    "Copy animated_model_item/ beside this script and obtain its model-preview " ..
    "placer (5039)."

function modInit()
  local model = betamoon.assets.models:add {
    key = "example:entity/watcher_model",
    path = "animated_model_item/clockwork_bird.json"
  }
  local animation = betamoon.assets.animations:add {
    key = "example:entity/watcher_animation",
    path = "animated_model_item/clockwork_bird.animation.json"
  }
  local texture = betamoon.assets.textures:add {
    key = "example:entity/watcher_texture",
    path = "animated_model_item/clockwork_bird.png"
  }

  local watcherAppearance = {
    model = model,
    texture = texture,
    animation = { asset = animation, clip = "animation.bird.idle" },
    display = { gui = { scale = 0.8 }, held = { scale = 0.8 } }
  }

  local watcher = betamoon.entities:add {
    key = "example:entity/watcher",
    kind = "living",
    displayName = "Clockwork Watcher",
    width = 0.7,
    height = 1.0,
    appearance = watcherAppearance,
    living = {
      maxHealth = 16,
      movementSpeed = 0.55,
      ai = "wander",
      aggression = "retaliate",
      attackDamage = 2,
      despawn = false
    },
    onInteract = function(ctx)
      betamoon.chat:send("Watcher health: %i", ctx.entity:getHealth())
      return betamoon.callbackResults.handled
    end
  }

  betamoon.items:add {
    id = 5039,
    key = "example:item/lesson_watcher_placer",
    displayName = "Clockwork Watcher Placer",
    appearance = watcherAppearance,
    onUseOnBlock = {
      action = function(ctx)
        if ctx.face ~= betamoon.mc.blockFaces.up then
          return betamoon.callbackResults.pass
        end
        local pos = ctx.position
        local spawned = ctx.world:spawnEntity(watcher, {
          position = { x = pos.x + 0.5, y = pos.y + 1, z = pos.z + 0.5 }
        })
        return spawned and betamoon.callbackResults.handled or betamoon.callbackResults.pass
      end
    }
  }
end

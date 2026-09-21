-- Copy animated_model_item/ beside this script in lua_scripts.
-- Obtain the Guardian Placer (5040). The head has a hitbox; wings are visual only.

name = "Manual Multipart AI Example"
version = "1.0.0"
description = "A multipart clockwork guardian whose Lua routine chooses targets, " ..
    "movement and attacks. Copy animated_model_item/ and obtain its model-preview " ..
    "placer (5040)."

function modInit()
  local model = betamoon.assets.models:add {
    key = "example:entity/guardian_model",
    path = "animated_model_item/clockwork_bird.json"
  }
  local animation = betamoon.assets.animations:add {
    key = "example:entity/guardian_animation",
    path = "animated_model_item/clockwork_bird.animation.json"
  }
  local texture = betamoon.assets.textures:add {
    key = "example:entity/guardian_texture",
    path = "animated_model_item/clockwork_bird.png"
  }

  local guardianAppearance = {
    model = model,
    texture = texture,
    animation = { asset = animation, clip = "animation.bird.idle" },
    display = { gui = { scale = 0.8 }, held = { scale = 0.8 } }
  }

  local guardian = betamoon.entities:add {
    key = "example:entity/manual_guardian",
    kind = "living",
    displayName = "Manual Clockwork Guardian",
    width = 0.8,
    height = 1.1,
    appearance = guardianAppearance,
    parts = {
      head = {
        hitbox = {
          offset = { x = 0, y = 0.75, z = -0.13 },
          size = { x = 0.45, y = 0.35, z = 0.45 }
        },
        onDamage = function(ctx)
          return math.min(32767, ctx.amount * 2)
        end
      },
      left_wing = {},
      right_wing = {}
    },
    data = {
      next_attack = { type = "integer", default = 0 }
    },
    living = {
      maxHealth = 30,
      movementSpeed = 0.65,
      ai = {
        mode = "manual",
        routine = function(ctx)
          local entity = ctx.entity
          local player = ctx.world:getClosestPlayer(12)
          if not player or not entity:canSee(player) then
            entity:setMovement(0, 0, false)
            return
          end

          entity:faceToward(player, 20, 20)
          local distance = entity:getDistanceTo(player)
          if distance > 1.7 then
            local hurt = entity:getHealth() < 8
            entity:setMovement(hurt and -0.35 or 0.8, 0, false)
            return
          end

          entity:setMovement(0, 0, false)
          if ctx.age >= entity.data:get("next_attack") then
            local night = ctx.world:getDayTime() >= 13000
            entity:attack(player, night and 4 or 2)
            entity.data:set("next_attack", ctx.age + 20)
          end
        end
      }
    },
    onInteract = function(ctx)
      betamoon.chat:send("Guardian health: %i", ctx.entity:getHealth())
      return betamoon.callbackResults.handled
    end
  }

  betamoon.items:add {
    id = 5040,
    key = "example:item/lesson_guardian_placer",
    displayName = "Guardian Placer",
    appearance = guardianAppearance,
    onUseOnBlock = {
      action = function(ctx)
        if ctx.face ~= betamoon.mc.blockFaces.up then
          return betamoon.callbackResults.pass
        end
        local pos = ctx.position
        local spawned = ctx.world:spawnEntity(guardian, {
          position = { x = pos.x + 0.5, y = pos.y + 1, z = pos.z + 0.5 }
        })
        return spawned and betamoon.callbackResults.handled or betamoon.callbackResults.pass
      end
    }
  }
end

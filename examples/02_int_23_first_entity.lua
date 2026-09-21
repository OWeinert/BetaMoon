-- Copy builtin_model_item/ beside this script in lua_scripts.
-- Obtain the Sample Placer item (5035) and right-click the top of a block.

name = "First Entity Example"
version = "1.0.0"
description = "Adds a stationary slab-shaped entity and a placer item (5035). Copy " ..
    "builtin_model_item/ beside this script. Right-click the top of a block " ..
    "with the item to spawn the entity in the space above it. The entity is " ..
    "selectable and persists when you save and reopen the world. The placer " ..
    "previews the entity model in inventories and while held. Punch the entity " ..
    "to kill it and recover the placer."

function modInit()
  local sampleAppearance = {
    model = "minecraft:block/slab",
    texture = "builtin_model_item/slab_stone.png",
    display = {
      gui = { scale = 0.85 },
      held = { scale = 0.75 },
      ground = { scale = 0.65 }
    }
  }

  local sample = betamoon.entities:add {
    key = "example:entity/first_entity",
    displayName = "Stone Sample Entity",
    width = 0.8,
    height = 0.5,
    appearance = sampleAppearance,
    drops = { { item = 5035 } },
    onBeforeDamage = function(ctx)
      if ctx.attacker and ctx.attacker:isPlayer() then
        ctx.entity:kill(ctx.attacker)
      end
      return 0
    end
  }

  betamoon.items:add {
    id = 5035,
    key = "example:item/lesson_sample_placer",
    displayName = "Sample Placer",
    appearance = sampleAppearance,
    onUseOnBlock = {
      action = function(ctx)
        if ctx.face ~= betamoon.mc.blockFaces.up then
          return betamoon.callbackResults.pass
        end

        local pos = ctx.position
        local entity, reason = ctx.world:spawnEntity(sample, {
          position = { x = pos.x + 0.5, y = pos.y + 1, z = pos.z + 0.5 }
        })
        if not entity then
          betamoon.chat:send("Could not place the sample: %s", reason)
          return betamoon.callbackResults.pass
        end
        return betamoon.callbackResults.handled
      end
    }
  }
end

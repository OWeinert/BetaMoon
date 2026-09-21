-- Copy builtin_model_item/ beside this script in lua_scripts.
-- Obtain the Data Totem Placer (5036), place a totem, and right-click it.

name = "Entity Data Example"
version = "1.0.0"
description = "Adds a model-backed data-totem entity and a placer item (5036). " ..
    "Copy builtin_model_item/ beside this script. Right-click the top of a " ..
    "block to place a totem, then right-click the totem to update its saved " ..
    "counter, last-user reference, and bounded interaction history. Reopen the " ..
    "world and click again to verify persistence. The placer reuses the same " ..
    "appearance as the entity. Punch the totem to kill it and recover the placer."

function modInit()
  local totemAppearance = {
    model = "minecraft:block/fence",
    texture = "builtin_model_item/slab_stone.png",
    display = {
      gui = { scale = 0.8 },
      held = { scale = 0.75 },
      ground = { scale = 0.65 }
    }
  }

  local totem = betamoon.entities:add {
    key = "example:entity/data_totem",
    displayName = "Data Totem",
    width = 0.5,
    height = 1.0,
    appearance = totemAppearance,
    drops = { { item = 5036 } },
    data = {
      clicks = { type = "integer", default = 0 },
      last_user = { type = "entity_reference" },
      history = {
        type = "list",
        maxLength = 5,
        element = {
          type = "record",
          fields = {
            player = { type = "string" },
            position = { type = "vector" }
          }
        }
      }
    },
    onInteract = function(ctx)
      local data = assert(ctx.entity.data, "BetaMoon entity data is unavailable")
      local currentCount = data:get("clicks")
      assert(type(currentCount) == "number")
      local nextCount = currentCount + 1
      local history = data:get("history")
      assert(type(history) == "table")

      table.insert(history, {
        player = ctx.player and ctx.player:getName() or "unknown",
        position = ctx.entity:getPosition()
      })
      if #history > 5 then
        table.remove(history, 1)
      end

      data:set("clicks", nextCount)
      data:set("history", history)
      if ctx.player then
        data:set("last_user", ctx.player)
      end

      local lastUser = data:get("last_user")
      ---@cast lastUser BetaMoonEntityDataReference?
      betamoon.chat:send("Totem clicks: %i; saved visits: %i; last user loaded: %s",
          nextCount, #history, tostring(lastUser and lastUser:isLoaded()))
      return betamoon.callbackResults.handled
    end,
    onBeforeDamage = function(ctx)
      if ctx.attacker and ctx.attacker:isPlayer() then
        ctx.entity:kill(ctx.attacker)
      end
      return 0
    end
  }

  betamoon.items:add {
    id = 5036,
    key = "example:item/lesson_data_totem_placer",
    displayName = "Data Totem Placer",
    appearance = totemAppearance,
    onUseOnBlock = {
      action = function(ctx)
        if ctx.face ~= betamoon.mc.blockFaces.up then
          return betamoon.callbackResults.pass
        end
        local pos = ctx.position
        local entity, reason = ctx.world:spawnEntity(totem, {
          position = { x = pos.x + 0.5, y = pos.y + 1, z = pos.z + 0.5 }
        })
        if not entity then
          betamoon.chat:send("Could not place the totem: %s", reason)
          return betamoon.callbackResults.pass
        end
        return betamoon.callbackResults.handled
      end
    }
  }
end

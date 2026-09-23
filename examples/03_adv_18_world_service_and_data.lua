-- A world service owns data once per saved world instead of once per block. Use
-- the inspector item to read its detached snapshot and current loaded-world views.

name = "World Service and Data Views Example"
version = "1.0.0"
description = "Adds a World Inspector backed by a persistent world service. Right-click it to show the service's " ..
    "saved load/tick counters together with current world, chunk, and loaded-player data."

function modInit()
  local diagnostics = betamoon.systems:add {
    key = "example:system/diagnostics",
    data = {
      loads = { type = "integer", default = 0 },
      seconds = { type = "integer", default = 0 },
      lastTimeOfDay = { type = "integer", default = 0 }
    },
    tick = { interval = 20 },
    onLoad = function(ctx)
      ctx.data:set("loads", ctx.data:get("loads") + 1)
    end,
    onTick = function(ctx)
      ctx.data:set("seconds", ctx.data:get("seconds") + 1)
      ctx.data:set("lastTimeOfDay", ctx.world:getInfo().timeOfDay)
    end
  }

  betamoon.items:add {
    id = 5041,
    key = "example:item/world_inspector",
    displayName = "World Inspector",
    icon = { x = 1, y = 4 },
    maxStackSize = 1,
    onUse = {
      action = function(ctx)
        if ctx.player == nil then return betamoon.callbackResults.pass end
        local position = ctx.player:getPosition()
        local info = ctx.world:getInfo()
        local chunk = ctx.world:getChunk(math.floor(position.x), math.floor(position.z))
        local service = ctx.world:getSystemData(diagnostics)
        if service == nil then return betamoon.callbackResults.deny end
        local players = ctx.world:getPlayers {
          x = position.x, y = position.y, z = position.z,
          radius = 128, limit = 128
        }

        betamoon.chat:send(
          "World day %i, time %i | service loads %i, seconds %i | chunk tiles %i | nearby players %i",
          info.day,
          info.timeOfDay,
          service.loads,
          service.seconds,
          chunk and chunk.tileCount or 0,
          #players
        )
        return betamoon.callbackResults.handled
      end
    }
  }
end

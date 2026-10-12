-- Obtain the Gem Dropper (5038) and right-click the top of a block.
-- The spawned pickup moves like a dropped item and uses the item's appearance.

name = "Pickup Entity Example"
version = "1.0.0"
description = "Creates a typed pickup with a collection delay and a once-only " ..
    "post-collection callback. Obtain Gem Dropper (5038)."

function modInit()
  local gem = betamoon.entities:add {
    key = "example:entity/gem_pickup",
    kind = "pickup",
    pickup = { item = 264, count = 2, delayTicks = 20 },
    onPickup = function(ctx)
      betamoon.chat:send("%s collected the gem pickup", ctx.player:getName())
    end
  }

  betamoon.items:add {
    id = 5038,
    key = "example:item/lesson_gem_dropper",
    displayName = "Gem Dropper",
    icon = { x = 4, y = 3 },
    onUseOnBlock = {
      action = function(ctx)
        if ctx.face ~= betamoon.mc.blockFaces.up then
          return betamoon.callbackResults.pass
        end
        local pos = ctx.position
        local spawned = ctx.world:spawnEntity(gem, {
          position = { x = pos.x + 0.5, y = pos.y + 1.2, z = pos.z + 0.5 }
        })
        return spawned and betamoon.callbackResults.handled or betamoon.callbackResults.pass
      end
    }
  }
end

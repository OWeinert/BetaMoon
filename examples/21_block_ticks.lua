name = "Block Ticks Example"
version = "2.0.0"
description = "Gives a block regular behavior and visual effects."

function modInit()
  betamoon.blocks:add {
    id = 203,
    material = "rock",
    key = "ticking_block",
    displayName = "Ticking Block",
    texture = 61,

    -- onTick changes the world. This example runs once every second because
    -- Minecraft normally runs 20 game ticks per second.
    onTick = {
      mode = "scheduled",
      schedule = {
        delay = 20,
        repeatEvery = 20
      },
      action = function(ctx)
        -- Switch between damage values 0 and 1 to show that the block updated.
        local nextDamage = ctx.damage == 0 and 1 or 0
        ctx.world:setBlock(ctx.x, ctx.y, ctx.z, ctx.id, nextDamage)
      end
    },

    -- onDisplayTick is only for things the player sees or hears. Minecraft
    -- calls it for nearby blocks without changing the world itself.
    onDisplayTick = {
      -- Each display update has a 25 percent chance to run this action.
      chance = 0.25,
      attempts = 1,
      action = function(ctx)
        ctx.world:spawnParticle("smoke", {
          x = ctx.x + 0.5,
          y = ctx.y + 1.05,
          z = ctx.z + 0.5,
          velocityY = 0.02
        })
      end
    }
  }
end

-- A block tick is an update for a placed block, not a GUI frame or a script reload.
-- Obtain block 203 and place it to test the scheduled update and nearby smoke effect.
-- This script does not add a crafting recipe; use the vanilla recipe example as a guide if needed.
-- The metadata toggle is a data change, not an automatic animation: this block does
-- not declare different textures for values 0 and 1. Smoke is the visible effect.

name = "Block Ticks Example"
version = "2.0.0"
description = "Gives a block regular behavior and visual effects."

function modInit()
  betamoon.blocks:add {
    id = 203,
    -- blockMaterials supplies the canonical native material name.
    material = betamoon.mc.blockMaterials.rock,
    key = "ticking_block",
    displayName = "Ticking Block",
    texture = 61,

    -- onTick changes the world. This example runs once every second because
    -- Minecraft normally runs 20 game ticks per second.
    onTick = {
      mode = "scheduled",
      -- delay starts the first update after placement; repeatEvery queues later
      -- updates. Neither value is measured in milliseconds or rendered frames.
      schedule = {
        delay = 20,
        repeatEvery = 20
      },
      action = function(ctx)
        -- Toggle metadata between 0 and 1; this value can be inspected as block data.
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
        -- particles supplies the canonical native particle identifier.
        ctx.world:spawnParticle(betamoon.mc.particles.smoke, {
          -- Block coordinates refer to its corner. Adding 0.5 centers the particle
          -- horizontally; y + 1.05 places it slightly above the block's top.
          x = ctx.x + 0.5,
          y = ctx.y + 1.05,
          z = ctx.z + 0.5,
          velocityY = 0.02
        })
      end
    }
  }
end

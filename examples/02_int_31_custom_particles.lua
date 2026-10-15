-- Copy this file and the custom_particles/ asset folder into betamoon/scripts.
-- Craft one stone + one redstone dust in any arrangement, then place the block.
-- Minecraft calls onDisplayTick for nearby blocks, just like vanilla particle effects.
-- Each spark then updates through Minecraft's particle lifecycle; its Lua onTick
-- changes presentation only. No tile entity or saved particle entity is created.

name = "Custom Particle Ring Example"
version = "1.0.0"
description = "Adds Particle Ring Block (242). Craft one stone with one redstone dust, place it nearby, " ..
    "and watch cyan sparks appear in a ring above its top. Copy the custom_particles asset folder " ..
    "alongside this script. The Java agent is required.\n\n" ..
    "The block emits twelve custom PNG particles on each nearby display tick. Each spark fades " ..
    "over its own lifetime while its onTick callback rotates and pulses its size. The ring stops " ..
    "emitting while the world is paused or the block's chunk is unloaded."

local spark
local particleCount = 12
local ringRadius = 0.95

---@param ctx BetaMoonBlockDisplayTickContext
local function spawnRing(ctx)
  for index = 0, particleCount - 1 do
    local angle = index * math.pi * 2 / particleCount
    ctx.world:spawnParticle(spark, {
      x = ctx.x + 0.5 + math.cos(angle) * ringRadius,
      y = ctx.y + 1.15,
      z = ctx.z + 0.5 + math.sin(angle) * ringRadius,
      velocityY = 0.003
    })
  end
end

function modInit()
  local texture = betamoon.assets.textures:add {
    key = "example:texture/particle_ring_spark",
    path = "custom_particles/spark.png"
  }

  spark = betamoon.particles:add {
    key = "example:particle/ring_spark",
    texture = texture,
    lifetimeTicks = 24,
    size = 0.12,
    color = {
      start = { r = 0.3, g = 1, b = 1, a = 1 },
      finish = { r = 0.1, g = 0.4, b = 1, a = 0 }
    },
    motion = { gravity = 0, drag = 1, collision = "none" },
    blend = "additive",
    light = "full",
    onTick = function(ctx)
      -- These values are independent for every sprite. Java interpolates the
      -- resulting tick poses during rendering; this callback never runs per frame.
      ctx.particle:setRotation(ctx.age * 8)
      ctx.particle:setSize(0.1 + math.sin(ctx.progress * math.pi) * 0.12)
    end
  }

  local block = betamoon.blocks:add {
    id = 242,
    key = "example:block/particle_ring",
    displayName = "Particle Ring Block",
    material = betamoon.mc.blockMaterials.rock,
    texture = 1,
    hardness = 1.5,
    resistance = 10,
    stepSound = betamoon.mc.stepSounds.stone,
    harvest = { pickaxe = 0 },
    drops = { { item = 242 } },
    onDisplayTick = {
      -- Display ticks are sampled by Minecraft and do not have a fixed interval.
      action = spawnRing
    }
  }

  betamoon.recipes:add {
    type = "shapeless",
    output = betamoon.stack(block),
    ingredients = {
      betamoon.blocks:getRequired(1),
      betamoon.items:getRequired(331)
    }
  }
end

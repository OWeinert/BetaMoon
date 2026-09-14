-- Craft stone + paper for the observer block. Place it on a non-air block,
-- left-click it, change a neighboring block, walk through it, and finally break it.
-- Each callback receives a short-lived context for that exact world position.

name = "Block Lifecycle Example"
version = "1.0.0"
description = "Adds Lifecycle Observer, a block for testing placement, clicking, neighboring changes, " ..
    "collisions, removal, harvesting, and explosions. Craft it from one stone block and one paper " ..
    "in any arrangement. Place it above a non-air block; unsupported placement is rejected.\n\n" ..
    "Placement plays a click and reports that the block was added. Left-click it for a metadata " ..
    "message, change an adjacent block for a neighbor message, and walk through it to receive an " ..
    "upward push: its full selection outline has no solid collision box. Mine it and compare the " ..
    "removal and harvest messages; you can also test an explosion in a suitable test area. Match " ..
    "each visible response to its named callback in the source."

function modInit()
  local observer = betamoon.blocks:add {
    id = 218,
    key = "lifecycle_observer",
    displayName = "Lifecycle Observer",
    -- blockMaterials supplies the canonical glass material name.
    material = betamoon.mc.blockMaterials.glass,
    hardness = 0.5,
    texture = 20,
    opaque = false,
    normalCube = false,
    lightOpacity = 0,
    -- Empty physical collision lets an entity enter the block and trigger
    -- onEntityCollide. The full selection box keeps it easy to target and break.
    collision = { boxes = {} },
    selection = { min = { 0, 0, 0 }, max = { 1, 1, 1 } },

    canPlace = {
      -- Query callbacks are read-only. Returning false rejects placement.
      action = function(ctx)
        return ctx.world:getBlock(ctx.x, ctx.y - 1, ctx.z).id ~= 0
      end
    },
    onPlaced = {
      action = function(ctx)
        -- sounds.random exposes stable names for vanilla sound effects.
        ctx.world:playSound(betamoon.mc.sounds.random.click, 0.5, 1.2)
      end
    },
    onAdded = {
      -- onAdded also covers blocks introduced by world changes, while onPlaced
      -- specifically represents placement by a living entity.
      action = function(ctx)
        betamoon.chat:send("Observer added at %i, %i, %i", ctx.x, ctx.y, ctx.z)
      end
    },
    onClick = {
      action = function(ctx)
        betamoon.chat:send("Observer clicked with metadata %i", ctx.damage)
      end
    },
    onNeighborChanged = {
      action = function(ctx)
        betamoon.chat:send("Observer noticed neighboring block %i", ctx.neighborId)
      end
    },
    onEntityCollide = {
      action = function(ctx)
        if ctx.entity then
          ctx.entity:setVelocity(0, 0.25, 0)
        end
      end
    },
    onRemoved = {
      action = function(ctx)
        betamoon.chat:send("Observer removed from %i, %i, %i", ctx.x, ctx.y, ctx.z)
      end
    },
    onBroken = {
      action = function(ctx)
        betamoon.chat:send("Observer was harvested")
      end
    },
    onExploded = {
      action = function(ctx)
        betamoon.chat:send("Observer was destroyed by an explosion")
      end
    }
  }

  betamoon.recipes:add {
    type = "shapeless",
    output = betamoon.stack(observer),
    ingredients = { betamoon.blocks:getRequired(1), betamoon.items:getRequired(339) }
  }
end

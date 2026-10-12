-- Scheduled placements run during world generation. The same compiled feature
-- can also be placed from an authoritative gameplay callback. Craft the Builder's
-- Rod from a stick and glowstone dust, then use it on a block's top face.
-- preview validates the complete atomic plan without mutating the world; place
-- plans again with the same deterministic seed and commits only if it remains valid.

name = "Direct Feature Placement Example"
version = "1.0.0"
description = "Adds Builder's Rod, an item that previews and directly places a glass column up to four blocks tall " ..
    "above a clicked block. Craft it from one stick and one glowstone dust in any arrangement.\n\n" ..
    "Use it on the top face of a block with four clear blocks above it. A successful placement reports the " ..
    "changed-block count and costs one durability. If validation fails, chat reports the stable rejection " ..
    "reason and nothing changes. This demonstrates reusable features, action-world handles, deterministic " ..
    "preview/place calls, result inspection, and atomic failure."

function modInit()
  local glassColumn = betamoon.worldgen.features:add {
    key = "example:feature/glass_column",
    type = "column",
    block = 20,
    height = 4,
    -- Columns stop before the first non-replaceable block. If the first block
    -- is occupied, the result is rejected and no change is committed.
    replace = 0
  }

  local buildersRod = betamoon.items:add {
    id = 5044,
    key = "example:item/builders_rod",
    displayName = "Builder's Rod",
    icon = { x = 5, y = 3 },
    maxStackSize = 1,
    maxDamage = 96,
    full3D = true,
    onUseOnBlock = {
      action = function(ctx)
        if ctx.face ~= "up" then
          betamoon.chat:send("Use the Builder's Rod on the top face of a block")
          return betamoon.callbackResults.handled
        end

        local x = ctx.position.x
        local y = ctx.position.y + 1
        local z = ctx.position.z
        if y < 0 or y > 127 then
          betamoon.chat:send("Column rejected: out_of_bounds")
          return betamoon.callbackResults.handled
        end

        -- The explicit seed is not random state; it is a stable extra salt.
        -- Reusing it makes preview and placement build exactly the same plan.
        local options = { seed = 2701 }
        local preview = glassColumn:preview(ctx.world, x, y, z, options)
        if not preview.placed then
          betamoon.chat:send("Column rejected: %s", preview.reason or "unknown")
          return betamoon.callbackResults.handled
        end

        local result = glassColumn:place(ctx.world, x, y, z, options)
        if not result.placed then
          betamoon.chat:send("World changed before commit: %s", result.reason or "unknown")
          return betamoon.callbackResults.handled
        end

        ctx.stack:damage(1)
        betamoon.chat:send("Placed %i glass blocks", result.blocksChanged)
        return betamoon.callbackResults.handled
      end
    }
  }

  betamoon.recipes:add {
    type = "shapeless",
    output = betamoon.stack(buildersRod),
    ingredients = {
      betamoon.items:getRequired(280),
      betamoon.items:getRequired(348)
    }
  }
end

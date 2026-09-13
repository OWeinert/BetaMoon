-- Craft an iron ingot with redstone for the surveyor. Right-click a block to
-- inspect the exact target; right-click in the air to trace up to 12 blocks.
-- Use it on an entity to push that entity upward. Successful actions cost durability.

name = "Targeted Item Actions Example"
version = "1.0.0"
description = "Uses block, air, and entity targets from item action contexts."

function modInit()
  local surveyor = betamoon.items:add {
    id = 5026,
    key = "example_surveyor",
    displayName = "Example Surveyor",
    icon = { x = 8, y = 3 },
    maxStackSize = 1,
    maxDamage = 96,
    full3D = true,
    -- The cooldown applies after a handled action and is shared per player for
    -- this item definition. Passing an action leaves it ready for another route.
    use = { cooldown = 5 },

    onUseOnBlock = {
      action = function(ctx)
        local target = ctx.target
        if target.kind ~= "block" or not target.block or not ctx.position then
          return "pass"
        end

        betamoon.chat:send(
          "Target block %i at %i, %i, %i on face %s",
          target.block.id,
          ctx.position.x,
          ctx.position.y,
          ctx.position.z,
          ctx.face or "unknown"
        )
        ctx.stack:damage(1)
        return "handled"
      end
    },
    onUse = {
      action = function(ctx)
        -- General use starts with a miss target. rayTrace performs an explicit
        -- block trace and can be configured to stop on liquids.
        local target = ctx:rayTrace { distance = 12, liquids = true }
        if target.kind == "block" and target.position then
          betamoon.chat:send(
            "Ray hit %i, %i, %i",
            target.position.x,
            target.position.y,
            target.position.z
          )
          ctx.stack:damage(1)
          return "handled"
        end

        betamoon.chat:send("No block within 12 blocks")
        return "pass"
      end
    },
    onUseOnEntity = {
      action = function(ctx)
        if not ctx.entity then
          return "pass"
        end
        -- setVelocity replaces all three motion components for the target entity.
        ctx.entity:setVelocity(0, 0.6, 0)
        ctx.stack:damage(2)
        return "handled"
      end
    }
  }

  betamoon.recipes:add {
    type = "shapeless",
    output = betamoon.stack(surveyor),
    ingredients = { betamoon.items:getRequired(265), betamoon.items:getRequired(331) }
  }
end

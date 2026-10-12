-- Craft gunpowder with redstone to obtain two configurable charges.
-- Right-click for a non-destructive concussive blast. Sneak-right-click for a
-- destructive blast that runs normal block drop and explosion callback logic.

name = "Configurable Explosion Example"
version = "1.0.0"
description = "Adds Configurable Charge, an item that demonstrates selective explosion effects and results. " ..
    "Right-click for a concussive blast that damages and pushes nearby entities without changing blocks. " ..
    "Sneak-right-click for a stronger demolition blast that destroys blocks and reports sampled positions."

function modInit()
  local charge = betamoon.items:add {
    id = 5042,
    key = "example:item/configurable_charge",
    displayName = "Configurable Charge",
    icon = { x = 8, y = 3 },
    maxStackSize = 16,
    use = { consume = 1, cooldown = 30 },
    onUse = {
      action = function(ctx)
        if not ctx.player then
          return betamoon.callbackResults.deny
        end

        local destructive = ctx.player:isSneaking()
        local result = ctx.world:createExplosion {
          position = ctx.player:getPosition(),
          strength = destructive and 3 or 2,
          -- An item context has no automatic entity source. Supplying the
          -- player attributes damage to them and excludes them from the blast.
          source = ctx.player,
          blocks = destructive and "destroy" or "none",
          entities = true,
          knockback = true,
          drops = destructive,
          dropChance = 0.3,
          fire = false,
          sound = true,
          particles = true,
          -- Position lists are opt-in because large explosions can reach many
          -- coordinates. The result remains useful without allocating the list.
          includeAffectedBlocks = destructive
        }

        local sampled = result.affectedBlocks and #result.affectedBlocks or 0
        betamoon.chat:send(string.format(
          "Explosion: %d blocks destroyed, %d entities hit, %d sampled positions",
          result.blocksDestroyed,
          result.entitiesDamaged,
          sampled
        ))
        return betamoon.callbackResults.handled
      end
    }
  }

  betamoon.recipes:add {
    type = "shapeless",
    output = betamoon.stack(charge, 2),
    ingredients = { betamoon.items:getRequired(289), betamoon.items:getRequired(331) }
  }
end

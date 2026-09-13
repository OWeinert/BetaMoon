-- Craft sugar + wheat for two powder items. Right-click while injured to heal three hearts.
-- This is a normal item with a custom action, unlike the declarative food example.
-- onUse handles general right-click use; onUseOnBlock and onUseOnEntity are separate hooks.
-- The cooldown is shared for this item definition per player, not stored in each stack.
-- Healing at full health still uses the powder in this simple demonstration.

name = "Item Interactions Example"
version = "1.0.0"
description = "Craft sugar with wheat for healing powder; right-click to heal three hearts."

function modInit()
  local powder = betamoon.items:add {
    id = 5020,
    key = "healing_powder",
    displayName = "Healing Powder",
    icon = { x = 13, y = 3 },
    maxStackSize = 16,
    render = { variants = { [0] = { color = 0x80FF90 } } },

    -- These settings apply to general right-click use. The callback below
    -- does not consume a second item: the declaration handles that cost.
    use = { consume = 1, cooldown = 20 },
    onUse = {
      action = function(ctx)
        -- Some contexts can lack a player. Check optional access before use.
        -- deny exits without performing the healing action below.
        if not ctx.player then
          return "deny"
        end
        ctx.player:heal(6)
        -- sounds.random supplies canonical vanilla sound names.
        ctx.world:playSound(betamoon.mc.sounds.random.pop, 0.4, 1.2)
        return "handled"
      end
    },
    onInventoryTick = {
      -- At 20 ticks per second this is about every two seconds. selected
      -- means the currently selected hotbar slot, not every carried stack.
      interval = 40,
      when = "selected",
      action = function(ctx)
        -- Inventory callbacks have a stack and optional player, not block state.
        -- The live context must not be retained for later callbacks.
        -- particles supplies the canonical names accepted by spawnParticle.
        ctx.world:spawnParticle(betamoon.mc.particles.redstone_dust)
      end
    }
  }

  betamoon.recipes:add {
    type = "shapeless",
    output = betamoon.stack(powder, 2),
    ingredients = { betamoon.items:getRequired(353), betamoon.items:getRequired(296) }
  }
end

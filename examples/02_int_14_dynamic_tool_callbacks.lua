-- Craft diamond + stick for an adaptive pick. It mines ores quickly, can harvest
-- obsidian despite its declared level, and reacts when crafted, used to mine, or
-- used to hit an entity. Mining queries must only calculate and return a value.

name = "Dynamic Tool Callbacks Example"
version = "1.0.0"
description = "Customizes harvesting and mining speed per target block."

function modInit()
  local oreIds = {
    [14] = true, -- Gold ore
    [15] = true, -- Iron ore
    [16] = true, -- Coal ore
    [21] = true, -- Lapis ore
    [56] = true, -- Diamond ore
    [73] = true, -- Redstone ore
    [74] = true  -- Glowing redstone ore
  }

  local pick = betamoon.items:add {
    id = 5027,
    key = "adaptive_pick",
    displayName = "Adaptive Pick",
    icon = { x = 2, y = 6 },
    maxStackSize = 1,
    maxDamage = 512,
    full3D = true,
    tool = {
      classes = { pickaxe = 2 },
      efficiency = 6,
      durabilityCost = { mine = 1, hit = 2 }
    },
    canHarvest = {
      -- Query callbacks are read-only. nil keeps the result from the declarative
      -- tool rules, while true grants this one deliberate exception.
      action = function(ctx)
        if ctx.block.id == 49 then
          return true
        end
        return nil
      end
    },
    getMiningSpeed = {
      -- This replaces speed only for the listed block IDs. Other blocks retain
      -- the class/efficiency result calculated above.
      action = function(ctx)
        if oreIds[ctx.block.id] then
          return 14
        end
        return nil
      end
    },
    onCrafted = {
      action = function(ctx)
        -- sounds.random supplies canonical names for native sound effects.
        ctx.world:playSound(betamoon.mc.sounds.random.orb, 0.5, 1.4)
      end
    },
    onBlockDestroyed = {
      action = function(ctx)
        -- Event callbacks can perform a follow-up effect after the normal tool action.
        -- particles supplies the native particle identifier without a magic string.
        ctx.world:spawnParticle(betamoon.mc.particles.redstone_dust)
      end
    },
    onHitEntity = {
      action = function(ctx)
        if ctx.entity then
          ctx.entity:setVelocity(0, 0.25, 0)
        end
      end
    }
  }

  betamoon.recipes:add {
    type = "shaped",
    output = betamoon.stack(pick),
    pattern = { " D ", " S ", " S " },
    ingredients = {
      D = betamoon.items:getRequired(264),
      S = betamoon.items:getRequired(280)
    }
  }
end

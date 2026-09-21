-- Craft cobblestone + stick, place the block, and right-click to toggle its green tint.
-- Sneak-right-click toggles the mining lock: unlock it again before trying to collect drops.
-- A declaration is read when the script loads; action functions run later for each placed block.
-- ctx means "context": it provides access to the particular block involved in that call.
-- Saved named state belongs to each placed block, unlike a shared Lua local variable.
-- The BetaMoon Java agent must be active for the survival break-permission check.

name = "Block Interactions Example"
version = "1.0.0"
description = "Adds Interaction Block with two saved states: active and locked. Craft it from one " ..
    "cobblestone and one stick in any arrangement, then place it. Normal right-click toggles " ..
    "active; sneak-right-click toggles locked. Each activation plays a click.\n\n" ..
    "Its tint identifies the combination: white is inactive and unlocked, green is active and " ..
    "unlocked, red is inactive and locked, and orange is active and locked. With BetaMoon's " ..
    "instrumentation agent enabled, a locked block cannot be broken through the survival break " ..
    "guard; unlock it before mining. An active block drops two cobblestone, otherwise one, and " ..
    "breaking it reports its ID and metadata in chat.\n\n" ..
    "Compare the interactions with the state, rendering, break-check, and drop callbacks. Beginner " ..
    "example 22 shares this crafting recipe, so test the two separately if crafting returns " ..
    "Greeting Block instead."

function modInit()
  local block = betamoon.blocks:add {
    id = 210,
    key = "example:block/interaction_block",
    displayName = "Interaction Block",
    -- blockMaterials avoids embedding Minecraft's internal material spelling.
    material = betamoon.mc.blockMaterials.rock,
    harvest = { pickaxe = 0 },
    hardness = 1,
    texture = 1,

    -- Two booleans use two of the four saved metadata bits.
    -- Field names are sorted, so active uses bit 0 and locked uses bit 1.
    state = {
      active = { type = "boolean", default = false },
      locked = { type = "boolean", default = false }
    },
    -- Variants use the packed metadata number: 0 = neither flag, 1 = active,
    -- 2 = locked, 3 = both. 0xRRGGBB colors tint the existing block texture.
    -- Keep state field names and enum orders stable in worlds that already use them.
    render = {
      variants = {
        [0] = { color = 0xFFFFFF },
        [1] = { color = 0x70FF70 },
        [2] = { color = 0xFF7070 },
        [3] = { color = 0xFFC060 }
      }
    },
    onActivate = {
      action = function(ctx)
        -- Lua evaluates and/or from left to right with short-circuiting.
        -- Here it chooses "locked" for a sneaking player and "active" otherwise.
        -- not flips the stored boolean, and set writes it back to this block.
        local field = ctx.player and ctx.player:isSneaking() and "locked" or "active"
        ctx.state:set(field, not ctx.state:get(field))
        -- sounds.random contains the named vanilla sounds used by action contexts.
        ctx.world:playSound(betamoon.mc.sounds.random.click, 0.5, 1)
        -- handled stops the normal activation path. pass (or nil) would let it
        -- continue; deny rejects the interaction and its general-use fallback.
        -- Queries such as canBreak use true/false instead of these strings.
        return betamoon.callbackResults.handled
      end
    },
    canBreak = {
      -- Queries can inspect state but cannot mutate it.
      action = function(ctx)
        return not ctx.state:get("locked")
      end
    },
    getDrops = {
      action = function(ctx)
        -- Return nil to keep normal drops, or {} to drop nothing.
        return { { item = 4, min = ctx.state:get("active") and 2 or 1 } }
      end
    },
    onBroken = {
      action = function(ctx)
        -- The controller preserves the old ID and metadata after removal.
        betamoon.chat:send("Broke interaction block %i, metadata %i", ctx.id, ctx.damage)
      end
    }
  }

  betamoon.recipes:add {
    type = "shapeless",
    output = betamoon.stack(block),
    ingredients = { betamoon.blocks:getRequired(4), betamoon.items:getRequired(280) }
  }
end

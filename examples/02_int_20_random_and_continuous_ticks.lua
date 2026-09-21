-- Obtain blocks 222 and 223 and place them beside each other. The pulse block
-- changes every game tick; the weathering block changes only when Minecraft
-- selects it for a random block tick. Both keep their state in metadata.

name = "Random and Continuous Ticks Example"
version = "1.0.0"
description = "Adds Continuous Pulse Block and Weathering Block to compare continuous game ticks with " ..
    "Minecraft's random block ticks. No recipes are included; obtain block IDs 222 and 223 with an " ..
    "inventory editor or item-spawning tool.\n\n" ..
    "Place them near you and watch their colors. Continuous Pulse Block alternates its lit state " ..
    "and tint every game tick, normally about twenty times per second. Weathering Block starts " ..
    "brown and has a one-in-four chance to turn green whenever it receives a random tick, so its " ..
    "change is unpredictable and does not repeat after weathering.\n\n" ..
    "The states are stored in block metadata. These changes affect appearance, not redstone output " ..
    "or emitted light. Compare the tick modes and state-dependent render variants in the source."

function modInit()
  betamoon.blocks:add {
    id = 222,
    key = "example:block/continuous_pulse_block",
    displayName = "Continuous Pulse Block",
    -- blockMaterials supplies the canonical native material name.
    material = betamoon.mc.blockMaterials.rock,
    harvest = { pickaxe = 0 },
    hardness = 1,
    texture = 1,
    state = {
      lit = { type = "boolean", default = false }
    },
    render = {
      variants = {
        [0] = { color = 0x7080A0 },
        [1] = { color = 0x80E0FF }
      }
    },
    onTick = {
      -- default starts one tick after placement and repeats every game tick.
      mode = "default",
      action = function(ctx)
        ctx.state:set("lit", not ctx.state:get("lit"))
      end
    }
  }

  betamoon.blocks:add {
    id = 223,
    key = "example:block/weathering_block",
    displayName = "Weathering Block",
    material = betamoon.mc.blockMaterials.rock,
    harvest = { pickaxe = 0 },
    hardness = 1,
    texture = 4,
    state = {
      weathered = { type = "boolean", default = false }
    },
    render = {
      variants = {
        [0] = { color = 0xC89A62 },
        [1] = { color = 0x70A878 }
      }
    },
    onTick = {
      -- random follows Minecraft's normal random-tick selection. It cannot
      -- schedule itself and may wait an unpredictable amount of time.
      mode = "random",
      action = function(ctx)
        if not ctx.state:get("weathered") and ctx:random() < 0.25 then
          ctx.state:set("weathered", true)
        end
      end
    }
  }
end

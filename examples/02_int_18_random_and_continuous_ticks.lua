-- Obtain blocks 222 and 223 and place them beside each other. The pulse block
-- changes every game tick; the weathering block changes only when Minecraft
-- selects it for a random block tick. Both keep their state in metadata.

name = "Random and Continuous Ticks Example"
version = "1.0.0"
description = "Compares continuous default ticks with vanilla random ticks."

function modInit()
  betamoon.blocks:add {
    id = 222,
    key = "continuous_pulse_block",
    displayName = "Continuous Pulse Block",
    -- blockMaterials supplies the canonical native material name.
    material = betamoon.mc.blockMaterials.rock,
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
    key = "weathering_block",
    displayName = "Weathering Block",
    material = betamoon.mc.blockMaterials.rock,
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

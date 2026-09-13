-- Craft cobblestone + redstone and apply a lever/wire input on the block's north side.
-- Connect an output wire on its south side. Directions are fixed world directions here.
-- A rising input starts a 20-tick pulse; holding the input on does not repeatedly retrigger it.
-- Turn the input off and on to test another pulse. A new trigger during a pending
-- pulse keeps the earliest scheduled deadline rather than extending the pulse.

name = "Redstone Timer Example"
version = "1.0.0"
description = "Craft cobblestone with redstone. Power its north side to produce a one-second pulse southward."

function modInit()
  local timer = betamoon.blocks:add {
    id = 213,
    key = "example_pulse_timer",
    displayName = "Pulse Timer",
    -- blockMaterials and blockFaces keep the native material and absolute output face canonical.
    material = betamoon.mc.blockMaterials.rock,
    hardness = 1,
    texture = 1,
    textures = { north = 61, south = 62 },
    state = { powered = { type = "boolean", default = false } },
    redstone = {
      weakPower = { state = "powered", sides = { betamoon.mc.blockFaces.south } },
      connections = { "north", "south" },
      onInputChanged = {
        action = function(ctx)
          -- Initial powered observations also trigger, allowing an already-on input.
          -- This tests a rising edge: north is powered now but was not before.
          -- The callback observes changes; it does not run once every game tick.
          -- Its first observation has no previous powered sides.
          if ctx.current.north and not ctx.previous.north then
            ctx.state:set("powered", true)
            -- This queues onTick for this placed block. It does not call onTick
            -- immediately and does not keep this ctx object alive for the later call.
            ctx:schedule(20)
          end
        end
      }
    },
    onTick = {
      mode = "scheduled",
      -- No automatic delay/repeat: the input callback schedules each pulse.
      action = function(ctx)
        ctx.state:set("powered", false)
      end
    }
  }

  betamoon.recipes:add {
    type = "shapeless", output = betamoon.stack(timer),
    ingredients = { betamoon.blocks:getRequired(4), betamoon.items:getRequired(331) }
  }
end

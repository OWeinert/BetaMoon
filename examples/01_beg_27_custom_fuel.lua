-- Register one item as a furnace fuel with a fixed duration.
-- Craft eight coal around one clay ball, then burn the compressed coal in a
-- normal furnace. Its 12,800 ticks equal the duration of the eight coal used.

name = "Custom Fuel Example"
version = "1.0.0"
description = "Adds Compressed Coal and registers it as fuel for vanilla furnaces. Craft it from eight " ..
    "coal surrounding one clay ball, then place it in a furnace fuel slot. It burns for 12,800 ticks, " ..
    "the same total duration as the eight coal used to make it. The example demonstrates the shortest " ..
    "fuel declaration; custom machine-specific fuel sets appear in the Fast Furnace example."

function modInit()
  local compressedCoal = betamoon.items:add {
    id = 5041,
    key = "example:item/compressed_coal",
    displayName = "Compressed Coal",
    maxStackSize = 64,
    icon = { x = 7, y = 0 }
  }

  -- Omitting set registers the item in the built-in furnace fuel set.
  -- burnTime is measured in game ticks; Minecraft normally runs 20 per second.
  betamoon.fuels:add {
    item = compressedCoal,
    burnTime = 12800
  }

  betamoon.recipes:add {
    type = "shaped",
    pattern = {
      "CCC",
      "CLC",
      "CCC"
    },
    ingredients = {
      C = betamoon.items:getRequired(263), -- Coal
      L = betamoon.items:getRequired(337)  -- Clay ball
    },
    output = betamoon.stack(compressedCoal)
  }
end

name = "Custom Item Example"
version = "2.0.0"
description = "Declares a normal item with durability and stack settings."

function modInit()
  -- items:add creates a normal item. Pick an unused item ID of 256 or higher.
  betamoon.items:add {
    id = 5000,
    key = "example_item",
    displayName = "Example Item",
    -- The item stacks up to 16 and can take 32 points of damage before breaking.
    maxStackSize = 16,
    maxDamage = 32,
    -- icon picks a picture from Minecraft's item texture sheet.
    icon = { x = 7, y = 3 }
  }
end

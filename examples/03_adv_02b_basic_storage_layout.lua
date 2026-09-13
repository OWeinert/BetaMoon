-- This companion file exports the screen-facing half of the storage example.
-- Slot names must match 02a, while their x/y positions are only GUI layout.
-- 02c creates the actual container and GUI handles from these plain tables.

name = "Basic Storage Layout Example"
version = "1.0.0"
description = "Defines the container slots and GUI layout used by the basic storage block."

function modInit()
  local public = {
    containerSlots = {
      { name = "Storage 1", slot = "storage_1", x = 62, y = 18 },
      { name = "Storage 2", slot = "storage_2", x = 80, y = 18 },
      { name = "Storage 3", slot = "storage_3", x = 98, y = 18 },
      { name = "Storage 4", slot = "storage_4", x = 62, y = 36 },
      { name = "Storage 5", slot = "storage_5", x = 80, y = 36 },
      { name = "Storage 6", slot = "storage_6", x = 98, y = 36 },
      { name = "Storage 7", slot = "storage_7", x = 62, y = 54 },
      { name = "Storage 8", slot = "storage_8", x = 80, y = 54 },
      { name = "Storage 9", slot = "storage_9", x = 98, y = 54 }
    },
    playerInventory = { x = 8, y = 90, includeHotbar = true },
    gui = {
      layout = {
        -- gui.backgrounds identifies BetaMoon's built-in Minecraft GUI layouts.
        preset = betamoon.mc.gui.backgrounds.container,
        height = 174,
        title = "Basic Storage",
        playerInventoryLabel = { text = "Inventory", x = 8, y = 78 }
      },
      background = { style = "minecraft", drawSlotFrames = true },
      elements = {
        {
          type = "text",
          value = "occupied",
          format = "Occupied slots: %d / 9",
          x = 8,
          y = 66,
          color = "dark_gray"
        }
      }
    }
  }

  betamoon.modules:export("basic_storage_layout", public)
end

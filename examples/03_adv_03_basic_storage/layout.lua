-- Private module for the Basic Storage layout declaration; loaded only by this package entrypoint.

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

  return public

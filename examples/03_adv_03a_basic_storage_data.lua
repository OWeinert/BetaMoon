-- This companion file exports the persistent part of a small storage block.
-- It registers no structural content itself; 02c imports this declaration while
-- creating the tile entity. Keeping persistent slot indexes in one table makes
-- later compatibility reviews much easier.

name = "Basic Storage Data Example"
version = "1.0.0"
description = "Provides the saved inventory and data definition for the three-part Basic Storage example. It " ..
    "defines nine named storage slots and an occupied-slot counter, then exports that definition " ..
    "for the other scripts.\n\n" ..
    "Load advanced examples 02a, 02b, and 02c together. This file alone adds no placeable block or " ..
    "crafting recipe. With all three loaded, craft a chest surrounded by eight wooden plank " ..
    "blocks, place the resulting Basic Storage block, and right-click to store items. Add and " ..
    "remove stacks to see the occupied-slot count update.\n\n" ..
    "Compare the named slots and saved counter here with the screen layout in 02b and the tile " ..
    "entity and inventory-change callback in 02c. Restart Minecraft after changing the resulting " ..
    "machine's inventory structure."

function modInit()
  local public = {
    inventory = {
      name = "Basic Storage",
      slots = {
        storage_1 = { index = 0 },
        storage_2 = { index = 1 },
        storage_3 = { index = 2 },
        storage_4 = { index = 3 },
        storage_5 = { index = 4 },
        storage_6 = { index = 5 },
        storage_7 = { index = 6 },
        storage_8 = { index = 7 },
        storage_9 = { index = 8 }
      }
    },
    data = {
      occupied = { type = "integer", default = 0, sync = true }
    }
  }

  betamoon.modules:export("example:module/basic_storage_data", public)
end

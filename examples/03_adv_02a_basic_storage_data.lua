-- This companion file exports the persistent part of a small storage block.
-- It registers no structural content itself; 02c imports this declaration while
-- creating the tile entity. Keeping persistent slot indexes in one table makes
-- later compatibility reviews much easier.

name = "Basic Storage Data Example"
version = "1.0.0"
description = "Defines the persistent inventory and data schema used by the basic storage block."

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

  betamoon.modules:export("basic_storage_data", public)
end

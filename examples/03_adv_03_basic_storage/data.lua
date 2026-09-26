-- Private module for the Basic Storage data declaration; loaded only by this package entrypoint.

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

  return public

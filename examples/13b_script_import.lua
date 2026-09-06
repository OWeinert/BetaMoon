name = "Script Import Example"
version = "2.0.0"
description = "Imports a resource reference from another script."
-- This makes sure the export example loads first.
dependencies = {
  "Script Export Example"
}

function modInit()
  -- modules:getRequired gets the table shared by the other script.
  local imported = betamoon.modules:getRequired("13a_script_export")

  betamoon.worldgen.ores:add {
    block = imported.block,
    veinsPerChunk = 12,
    veinSize = 6,
    height = { min = 0, max = 64 },
    dimension = "overworld",
    replace = 1
  }
end

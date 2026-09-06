name = "Script Export Example"
version = "2.0.0"
description = "Exports a resource reference for another script."

-- modules:export creates a table that other scripts can use.
local export = betamoon.modules:export("16a_script_export")

function modInit()
  -- Save the block in that table so other scripts can find it.
  export.block = betamoon.blocks:add {
    id = 207,
    material = "rock",
    key = "exported_block",
    displayName = "Exported Block",
    hardness = 2,
    resistance = 5,
    texture = 51,
    harvest = { pickaxe = 1 }
  }
end

return export

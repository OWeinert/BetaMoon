name = "Script Export Example"
version = "1.0.0"
description = "Creates a block and exports it as a Lua module."

-- Create a module table and register it under the require() name.
-- This keeps the export setup simple for consumers.
-- The name you give this module via this method is the name that consumers have to use.
-- So try to use the same or a similar name to your Mod's file or name.
local export = betamoon.exportModule("16a_script_export")

function modInit()
  -- Now you define exported variables as "export." table entries instead of local variables.
  -- If you define a variable via "local" it won't be visible to scripts that depend on your script.
  export.block = betamoon.createBlock(207, "rock", "exported_block")
    :setHardness(2.0)
    :setResistance(5.0)
    :setTextureId(51)
    :setBlockHarvestLevel("pickaxe", 1)
    :setStepSound("stone")
    :register("Exported Block")
end

-- Returning the module is optional when using betamoon.exportModule,
-- but it's a nice pattern for plain Lua tooling too.
return export
-- Copy 01_beg_18a_module_export.lua alongside this file. Its declared script name is the dependency.
-- The dependency ensures initialization order; import then retrieves its exported table.
-- Importing does not run the other file again or create a second copy of its block.
-- This example uses that block in ore generation, so inspect newly generated terrain.

name = "Script Import Example"
version = "2.0.0"
description = "Uses the block exported by beginner example 18a, Script Export Example, and adds natural " ..
    "generation for it. Both files must be loaded; this script imports the existing block rather " ..
    "than registering a second copy.\n\n" ..
    "Start a new world or enter newly generated Overworld chunks and search for Exported Block " ..
    "underground. Generation replaces stone, with twelve vein attempts per chunk, a configured " ..
    "vein size of six, and a height range from Y=0 through Y=64. Existing terrain is unchanged. " ..
    "Compare the dependency and module import with the generation rule to see how two scripts " ..
    "cooperate."
-- This makes sure the export example loads first.
dependencies = {
  "Script Export Example"
}

function modInit()
  -- modules:import gets the table shared by the other script.
  -- The argument matches modules:export's key exactly. It is a stable public
  -- name, not a path to a Lua file.
  local imported = betamoon.modules:import("script_export")

  -- The imported block reference can be passed anywhere a block ID or reference
  -- is accepted. This generation rule places it in newly created chunks.
  betamoon.worldgen.ores:add {
    -- veinsPerChunk is the number of generation attempts; veinSize is the
    -- maximum group size. height limits the block Y levels used for attempts.
    block = imported.block,
    veinsPerChunk = 12,
    veinSize = 6,
    height = { min = 0, max = 64 },
    -- dimensions contains overworld, nether, and both. A matching raw string
    -- can still be used when a script receives its value from configuration.
    dimension = betamoon.mc.world.dimensions.overworld,
    -- Stone is the only block this rule may replace.
    replace = 1
  }
end

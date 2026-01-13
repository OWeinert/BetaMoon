name = "Script Import Example"
version = "1.0.0"
description = "Requires the exported block and adds ore generation."

-- Ensure the export script loads first so its block exists.
dependencies = {"Script Export Example"}

function modInit()
  -- Load the exported module by the name used in exportModule.
  local import = betamoon.requireModule("16a_script_export")

  -- Use the exported block handle when defining ore generation.
  betamoon.startWorldGen()
    :addOreGen(import.block, 12, 6, 0, 64)
      :setDimension("overworld")
      :setSpawnBlock(1)
      :setBiomes({})
      :finishOreGen()
    :finishWorldGen()
end

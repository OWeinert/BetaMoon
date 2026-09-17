-- Load this together with 01_beg_19b_module_import.lua to share a block reference between scripts.
-- The export table is created, populated and registered during modInit.
-- The public export key below is a chosen string; it need not match this file's number.

name = "Script Export Example"
version = "2.0.0"
description = "Adds Exported Block and shares its resource handle through the script_export module. This is " ..
    "the exporting half of a two-script example that keeps block registration separate from world " ..
    "generation.\n\n" ..
    "Load this file together with beginner example 18b, Script Import Example, to generate the " ..
    "block in new Overworld terrain. This file alone registers block ID 207 but supplies neither a " ..
    "recipe nor ore generation; you can obtain it with an inventory editor or item-spawning tool. " ..
    "Place it and mine it with a stone pickaxe or better, then compare the exported table with the " ..
    "companion script's import."

function modInit()
  -- Only values placed in this table become available to importing scripts.
  local public = {}

  -- Save the block in the public table so other scripts can find it after exporting.
  public.block = betamoon.blocks:add {
    id = 207,
    -- blockMaterials contains checked names for Minecraft's built-in materials.
    -- Its values are ordinary strings, so writing "rock" directly remains valid.
    material = betamoon.mc.blockMaterials.rock,
    key = "exported_block",
    displayName = "Exported Block",
    hardness = 2,
    resistance = 5,
    texture = 51,
    harvest = { pickaxe = 1 }
  }

  -- Export only after the public table is complete. export accepts the table but
  -- returns nothing. The key remains stable even when this filename changes.
  betamoon.modules:export("script_export", public)
end

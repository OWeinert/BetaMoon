-- Load this together with 01_beg_18b_module_import.lua to share a block reference between scripts.
-- The export table is created, populated and registered during modInit.
-- The public export key below is a chosen string; it need not match this file's number.

name = "Script Export Example"
version = "2.0.0"
description = "Exports a resource reference for another script."

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

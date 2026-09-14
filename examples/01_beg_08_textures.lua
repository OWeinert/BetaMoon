-- Copy example_block.png alongside this script in lua_scripts before loading it.
-- Numeric texture values select cells in the vanilla atlas; quoted filenames load PNGs.
-- The same image is reused here so you can compare a whole block, separate faces,
-- and an inventory item. Texture settings change appearance, not block functionality.
-- These declarations add content only; add recipes using the vanilla recipe example as a guide.

name = "Custom Textures Example"
version = "2.0.0"
description = "Adds Textured Block, Sided Block, and Textured Item to compare a custom PNG with Minecraft's " ..
    "built-in texture atlas. Copy example_block.png into the scripts folder alongside this script " ..
    "before starting the game.\n\n" ..
    "No recipes are included. Obtain block IDs 201 and 202 and item ID 5015 with an inventory " ..
    "editor or item-spawning tool. Place both blocks where you can view their tops, bottoms, and " ..
    "sides: Textured Block uses the PNG on every face, while Sided Block combines custom sides " ..
    "with vanilla top and bottom textures. Select Textured Item to compare its icon with the same " ..
    "source image."

function modInit()
  -- A file name loads a custom image from the lua_scripts folder.
  -- blockMaterials contains the built-in material names accepted by blocks.
  -- These constants are strings, so the matching quoted name also works.
  betamoon.blocks:add {
    id = 201,
    material = betamoon.mc.blockMaterials.rock,
    key = "textured_block",
    displayName = "Textured Block",
    texture = "example_block.png"
  }

  -- textures can give the top, bottom, and sides different pictures.
  -- "sides" means all four walls of the block.
  betamoon.blocks:add {
    id = 202,
    material = betamoon.mc.blockMaterials.rock,
    key = "sided_block",
    displayName = "Sided Block",
    textures = {
      -- Numbers select vanilla block-atlas cells; the file supplies a custom side.
      top = 0,
      bottom = 2,
      sides = "example_block.png"
    }
  }

  -- Custom images work for items too.
  betamoon.items:add {
    id = 5015,
    key = "textured_item",
    displayName = "Textured Item",
    texture = "example_block.png"
  }
end

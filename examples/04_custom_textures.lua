name = "Custom Textures Example"
version = "2.0.0"
description = "Uses custom textures for blocks, block sides, and items."

function modInit()
  -- A file name loads a custom image from the lua_scripts folder.
  betamoon.blocks:add {
    id = 201,
    material = "rock",
    key = "textured_block",
    displayName = "Textured Block",
    texture = "example_block.png"
  }

  -- textures can give the top, bottom, and sides different pictures.
  -- "sides" means all four walls of the block.
  betamoon.blocks:add {
    id = 202,
    material = "rock",
    key = "sided_block",
    displayName = "Sided Block",
    textures = {
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

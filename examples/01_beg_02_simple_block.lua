-- A block declaration gives BetaMoon every setting needed to create one block type.
-- id is Minecraft's numeric identity, key is its stable script-facing identifier,
-- and displayName is player-facing text. Change IDs only before using a saved world.
-- This script registers block 200 but does not give it to the player or add a recipe.
-- Use an inventory tool to obtain it, or add a recipe as shown in the vanilla recipe example.

name = "Custom Block Example"
version = "2.0.0"
description = "Declares a block with harvesting and custom drops."

function modInit()
  -- blocks:add creates a block from the settings inside this table.
  betamoon.blocks:add {
    -- Pick an unused block ID between 1 and 255; 0 is reserved for air.
    id = 200,
    -- The material gives the block stone-like behavior, including the tools
    -- Minecraft normally associates with it. Pick another built-in value from
    -- betamoon.mc.blockMaterials; writing the same string directly also works.
    material = betamoon.mc.blockMaterials.rock,
    -- The key is the block's name inside your scripts. Keep it unique.
    key = "example_block",
    -- This is the name players see in the game.
    displayName = "Example Block",
    -- hardness controls mining time. resistance controls explosion strength.
    -- stepSound chooses the sound heard when walking on or breaking the block.
    hardness = 1.5,
    resistance = 10,
    -- betamoon.mc.stepSounds lists every built-in footstep sound accepted here.
    stepSound = betamoon.mc.stepSounds.stone,
    -- This is an atlas cell number, not block ID 1. Texture and resource IDs
    -- are different concepts even when their numeric values happen to match.
    texture = 1,
    -- pickaxe selects the required tool class; level 0 accepts a wooden pickaxe.
    harvest = {
      pickaxe = 0
    },
    -- Each drop entry describes an item stack created when the block is broken.
    drops = {
      { item = 4 }
    }
  }
end

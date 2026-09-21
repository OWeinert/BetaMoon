-- A block declaration gives BetaMoon every setting needed to create one block type.
-- id is Minecraft's numeric identity, key is its stable script-facing identifier,
-- and displayName is player-facing text. Change IDs only before using a saved world.
-- This script registers block 200 but does not give it to the player or add a recipe.
-- Use an inventory tool to obtain it, or add a recipe as shown in the vanilla recipe example.

name = "Custom Block Example"
version = "2.0.0"
description = "Adds Example Block, a stone-like building block with its own name, hardness, blast " ..
    "resistance, sound, and mining requirement. It demonstrates the smallest complete custom block " ..
    "declaration.\n\n" ..
    "No crafting recipe is included. Obtain block ID 200 with an inventory editor or item-spawning " ..
    "tool, place it, and mine it with a wooden pickaxe or better. It drops one cobblestone rather " ..
    "than another Example Block. Compare the placed block and its drop with the material, harvest, " ..
    "and drops settings in the script."

function modInit()
  -- blocks:add creates a block from the settings inside this table.
  betamoon.blocks:add {
    -- Pick an unused block ID between 1 and 255; 0 is reserved for air.
    id = 200,
    -- The material gives the block stone-like behavior, including the tools
    -- Minecraft normally associates with it. Pick another built-in value from
    -- betamoon.mc.blockMaterials; writing the same string directly also works.
    material = betamoon.mc.blockMaterials.rock,
    -- The key is the block's unique identifier inside your scripts.
    -- There are will be more use-cases for keys in the future.
    -- A key always has the structure "namespace:type/name"
    -- Where: - "namespace" is an identifier for the mod it belongs to, like "mymod" or in this case "example"
    --        - "type" is the type of content that the key is. In this case "block".
    --        - "name" is the internal name of your block. That one is completely up to you.
    key = "example:block/example_block",
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

-- This combines a block declaration with a world-generation rule using its reference.
-- Explore newly generated terrain to test it; existing chunks are not filled retroactively.
-- If testing changes repeatedly, generate new terrain so old and new rules are not confused.
-- Generation attempts are not a guarantee that every vein will contain exactly eight blocks.

name = "Ore Gen Example"
version = "2.0.0"
description = "Adds Example Ore and generates it in newly created Overworld terrain. It replaces stone in " ..
    "veins, with ten placement attempts per chunk, a configured vein size of eight, and a height " ..
    "range from Y=0 through Y=60.\n\n" ..
    "Start a new world or explore beyond previously generated terrain, then search underground. " ..
    "Existing chunks are not filled with the new ore, and generation attempts do not guarantee a " ..
    "vein in every location. Mine it with an iron pickaxe or better. The ore uses block ID 206; " ..
    "compare its block declaration with the separate generation rule and its biome and height " ..
    "settings."

function modInit()
  local ore = betamoon.blocks:add {
    id = 206,
    -- blockMaterials lists the built-in materials a block can use; "rock" also works.
    material = betamoon.mc.blockMaterials.rock,
    key = "example_ore",
    displayName = "Example Ore",
    hardness = 3,
    resistance = 5,
    texture = 50,
    harvest = { pickaxe = 2 }
  }

  -- This adds the ore to newly generated chunks.
  -- It controls how often it appears, vein size, height, and which block it replaces.
  betamoon.worldgen.ores:add {
    -- block is what the generator places. The returned reference avoids repeating its ID.
    block = ore,
    -- These are placement attempts per chunk and the maximum blocks in one vein.
    veinsPerChunk = 10,
    veinSize = 8,
    -- min and max are vertical block coordinates, inclusive.
    height = { min = 0, max = 60 },
    -- dimensions offers overworld, nether, and both. The equivalent quoted names
    -- are also accepted. This choice keeps the rule out of newly generated Nether chunks.
    dimension = betamoon.mc.world.dimensions.overworld,
    -- Only matching stone is replaced, leaving air and other blocks alone.
    -- The block reference above tells the generator what to place instead.
    replace = betamoon.blocks:getRequired(1),
    -- An empty biome list applies the rule to every biome in this dimension.
    biomes = {}
  }
end

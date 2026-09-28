-- This capstone keeps declarations in domain-focused private modules. main.lua
-- owns registration order and passes references between those modules explicitly.
-- Copy the complete folder so require paths and structure gameplay data remain intact.

name = "Complete Worldgen Pack Example"
version = "1.0.0"
description = "Adds Amber Grove, a complete modular world-generation pack with a layered surface, procedural " ..
    "trees, ground accents, weathered stone circles, biome decorators, tags, spawns, and an active climate " ..
    "source. Copy the complete 03_adv_20_complete_worldgen_pack folder into lua_scripts.\n\n" ..
    "Create a new world or explore new chunks in warm, moderately humid climates. Terrain blocks and " ..
    "decorations only appear in newly generated chunks. Study main.lua first for dependency order, then " ..
    "features.lua, structures.lua, and biomes.lua for focused declarations."

function modInit()
  local features = require("features")()
  local structures = require("structures")()
  require("biomes")(features, structures)
end

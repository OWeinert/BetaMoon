-- This builds a modern keyed biome without inheriting a vanilla biome.
-- Surface layers, a procedural tree, its decorator placement, presentation,
-- weather, spawn lists, and climate selection are separate reusable resources.
-- Terrain blocks and decorators affect new chunks only. Beta 1.7.3 recalculates
-- biome selection, so colors, weather, and spawn filtering can change over old terrain.

name = "Custom Biome Gen Example"
version = "3.0.0"
description = "Adds Example Meadow with the modern keyed world-generation API. It uses a dirt-over-gravel " ..
    "surface, custom colors, rain, procedural trees, and a sheep spawn entry. An explicit vanilla-climate " ..
    "source selects the biome in warm, humid climate cells.\n\n" ..
    "Create a new world or explore newly generated terrain to find it. Existing terrain blocks are " ..
    "unchanged, and neither the biome nor sheep are guaranteed at a particular location. The sheep " ..
    "weight is relative to other eligible creature entries; it is not a percentage or a fixed count. " ..
    "Follow the references from surface and tree, through the placement, into the biome and source."

function modInit()
  local meadowSurface = betamoon.worldgen.surfaces:add {
    key = "example:surface/meadow",
    replace = { 1, 2, 3, 12, 13 },
    layers = {
      { block = 3, depth = 1 },
      { block = 13, depth = { min = 2, max = 4 } }
    },
    underwaterBlock = 12
  }

  -- This is a procedural tree rather than the shorter vanilla-tree adapter.
  -- The tree itself says how to build; its placement below says where and how often.
  local meadowTree = betamoon.worldgen.trees:add {
    key = "example:tree/meadow",
    trunk = {
      block = 17,
      height = { min = 4, max = 6 },
      bend = 0.1
    },
    canopy = {
      block = 18,
      shape = "layered_disk",
      radius = { min = 2, max = 3 },
      density = 0.85
    },
    ground = { 2, 3 },
    replace = { 0, 18 }
  }

  -- This placement is authored as a reusable template. Attaching its reference
  -- to a biome below scopes a copy to that biome instead of generating it globally.
  local meadowTrees = betamoon.worldgen.placements:add {
    key = "example:placement/meadow_trees",
    feature = meadowTree,
    stage = "surface_features",
    attempts = { perChunk = 2, extraChance = 0.35 },
    position = { height = { type = "surface" } },
    conditions = {
      ground = { 2, 3 },
      air = true,
      requireSky = true
    },
    salt = 50205
  }

  local meadow = betamoon.worldgen.biomes:add {
    key = "example:biome/meadow",
    name = "Example Meadow",
    color = 0x55cc88,
    foliageColor = 0x33aa77,
    tags = { "example:meadow", "example:temperate" },
    surface = meadowSurface,
    decorators = { meadowTrees },
    weather = { rain = true, snow = false },
    spawns = {
      [betamoon.mc.world.spawnGroups.creatures] = {
        {
          entity = betamoon.mc.entities.sheep,
          -- Spawn weights are relative choices among eligible entries.
          weight = 12
        }
      }
    }
  }

  betamoon.worldgen.biomeSources:add {
    key = "example:biome_source/meadow_climate",
    type = "vanilla_climate",
    active = true,
    -- If several scripts activate sources, only the highest source priority wins.
    priority = 20,
    entries = {
      {
        biome = meadow,
        temperature = { min = 0.6, max = 0.8 },
        humidity = { min = 0.7, max = 0.9 },
        priority = 10
      }
    }
  }
end

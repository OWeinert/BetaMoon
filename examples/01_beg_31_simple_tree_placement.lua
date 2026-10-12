-- A tree declaration is a reusable feature. This beginner lesson uses the
-- vanilla-tree adapter, while later examples build procedural and structure trees.
-- The placement checks for open sky and valid ground at the first air block.

name = "Simple Tree Placement Example"
version = "1.0.0"
description = "Adds occasional normal oak trees to newly generated Overworld terrain. The tree uses " ..
    "BetaMoon's vanilla-tree adapter, while a separate placement controls surface sampling, frequency, " ..
    "ground checks, and sky access.\n\n" ..
    "Create a new world or explore new chunks. Look for extra oak trees outside deserts. Existing " ..
    "chunks are unchanged. Compare this short adapter declaration with the procedural tree in " ..
    "intermediate biome example 05."

function modInit()
  local oakTree = betamoon.worldgen.trees:add {
    key = "example:tree/tutorial_oak",
    generator = "normal",
    -- These defaults are already used by the adapter, but spelling them out
    -- shows where custom logs, leaves, and valid planting blocks would go.
    trunk = 17,
    leaves = 18,
    ground = { 2, 3 },
    replace = { 0, 18 }
  }

  betamoon.worldgen.placements:add {
    key = "example:placement/tutorial_oak",
    feature = oakTree,
    stage = "surface_features",
    dimensions = { "overworld" },
    attempts = { perChunk = 1, extraChance = 0.2 },
    position = { height = { type = "surface" } },
    conditions = {
      ground = { 2, 3 },
      air = true,
      requireSky = true
    },
    biomes = { exclude = { betamoon.mc.world.biomes.desert } },
    salt = 10130
  }
end

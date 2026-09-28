-- Features describe reusable block changes; placements decide when and where
-- those changes are attempted during chunk generation. Keeping them separate
-- lets one feature be reused by several placements, biomes, or direct actions.
-- This lesson creates small gravel patches on newly generated Overworld terrain.

name = "Simple Feature Placement Example"
version = "1.0.0"
description = "Adds small gravel patches to newly generated Overworld terrain with one reusable feature " ..
    "and one placement rule. The feature describes the patch shape and changed block; the placement " ..
    "chooses surface positions, frequency, dimension, and biome exclusions.\n\n" ..
    "Create a new world or explore new chunks and look for scattered gravel patches. Existing terrain " ..
    "is unchanged. Read the feature first, then the placement that schedules it."

function modInit()
  local gravelPatch = betamoon.worldgen.features:add {
    key = "example:feature/surface_gravel_patch",
    type = "block_patch",
    block = 13,
    radius = 3,
    tries = 24,
    -- Every sampled position is changed only when it currently contains grass
    -- or dirt. Failed samples are harmless and simply leave that block alone.
    replace = { 2, 3 }
  }

  betamoon.worldgen.placements:add {
    key = "example:placement/surface_gravel_patch",
    feature = gravelPatch,
    stage = "surface_features",
    dimensions = { "overworld" },
    attempts = { perChunk = 1, extraChance = 0.35 },
    -- Minecraft reports surface height as the first air block. Offset -1
    -- therefore targets the exposed terrain block beneath that air.
    position = {
      height = { type = "surface", offset = -1 },
      horizontal = "chunk"
    },
    -- Full biome names, keyed biome IDs, and tags are accepted filters.
    biomes = { exclude = { betamoon.mc.world.biomes.desert } },
    salt = 10129
  }
end

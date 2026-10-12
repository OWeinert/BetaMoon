-- Composite features keep selection and multi-step plans reusable. Placement
-- dependencies order rules in the same generation stage without relying on file
-- order or priority ties. Explore new Overworld chunks to see the result.

name = "Composite and Ordered Features Example"
version = "1.0.0"
description = "Builds two composite world features and schedules them with an explicit dependency. A " ..
    "weighted feature chooses a gravel or clay surface patch. A sequence feature plans a two-material " ..
    "subsurface deposit as one atomic feature. The deposit placement declares that it runs after the " ..
    "surface placement in the same stage.\n\n" ..
    "Explore newly generated Overworld terrain. Each placement still samples its own positions; after " ..
    "controls deterministic execution order inside a chunk, not a shared origin."

function modInit()
  local gravelPatch = betamoon.worldgen.features:add {
    key = "example:feature/ordered_gravel_patch",
    type = "block_patch",
    block = 13,
    radius = 3,
    tries = 22,
    replace = { 2, 3 }
  }

  local clayPatch = betamoon.worldgen.features:add {
    key = "example:feature/ordered_clay_patch",
    type = "block_patch",
    block = 82,
    radius = 3,
    tries = 22,
    replace = { 2, 3 }
  }

  local weightedGround = betamoon.worldgen.features:add {
    key = "example:feature/weighted_ground_patch",
    type = "weighted",
    features = {
      { feature = gravelPatch, weight = 3 },
      { feature = clayPatch, weight = 1 }
    },
    maxBlocks = 32,
    maxRadius = 4
  }

  local clayLayer = betamoon.worldgen.features:add {
    key = "example:feature/deposit_clay_layer",
    type = "disk",
    block = 82,
    radius = 3,
    halfHeight = 1,
    replace = 3
  }

  local gravelLayer = betamoon.worldgen.features:add {
    key = "example:feature/deposit_gravel_layer",
    type = "disk",
    block = 13,
    radius = 3,
    halfHeight = 2,
    replace = 1
  }

  -- Both children inspect the same bounded volume but replace different terrain
  -- materials. A rejection aborts the complete plan; a no_changes child is allowed.
  local layeredDeposit = betamoon.worldgen.features:add {
    key = "example:feature/layered_deposit_sequence",
    type = "sequence",
    features = { clayLayer, gravelLayer },
    maxBlocks = 384,
    maxRadius = 4
  }

  local groundPlacement = betamoon.worldgen.placements:add {
    key = "example:placement/weighted_ground_patch",
    feature = weightedGround,
    stage = "surface_features",
    attempts = { perChunk = 1, extraChance = 0.25 },
    position = { height = { type = "surface", offset = -1 } },
    salt = 20281
  }

  betamoon.worldgen.placements:add {
    key = "example:placement/layered_deposit_sequence",
    feature = layeredDeposit,
    stage = "surface_features",
    attempts = { perChunk = 1, probability = 0.55 },
    -- Offset -2 centers the disks just below the exposed terrain surface.
    position = { height = { type = "surface", offset = -2 } },
    -- References are preferable to repeated string keys: renaming the key above
    -- cannot silently leave this dependency pointing at a stale declaration.
    after = { groundPlacement },
    salt = 20282
  }
end

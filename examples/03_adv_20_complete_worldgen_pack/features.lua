-- Registers reusable tree and ground-accent features and returns only the references that
-- the biome module needs. Keeping the table small makes module dependencies clear.

return function()
  local amberTree = betamoon.worldgen.trees:add {
    key = "example:tree/amber_grove",
    trunk = {
      block = 17,
      metadata = 2,
      height = { min = 6, max = 9 },
      bend = 0.18
    },
    branches = {
      start = 0.55,
      count = { min = 2, max = 5 },
      length = { min = 2, max = 4 },
      upwardBias = 0.35
    },
    canopy = {
      block = 18,
      metadata = 2,
      shape = "clustered_sphere",
      radius = { min = 2, max = 4 },
      density = 0.82
    },
    ground = { 2, 3 },
    replace = { 0, 18 }
  }

  local groundAccents = betamoon.worldgen.features:add {
    key = "example:feature/amber_grove_ground_accents",
    type = "weighted",
    features = {
      {
        feature = betamoon.worldgen.features:add {
          key = "example:feature/amber_grove_gravel_accents",
          type = "block_patch",
          block = 13,
          radius = 4,
          tries = 14,
          replace = { 2, 3 }
        },
        weight = 3
      },
      {
        feature = betamoon.worldgen.features:add {
          key = "example:feature/amber_grove_clay_accents",
          type = "block_patch",
          block = 82,
          radius = 4,
          tries = 10,
          replace = { 2, 3 }
        },
        weight = 1
      }
    },
    maxBlocks = 24,
    maxRadius = 5
  }

  return {
    tree = amberTree,
    groundAccents = groundAccents
  }
end

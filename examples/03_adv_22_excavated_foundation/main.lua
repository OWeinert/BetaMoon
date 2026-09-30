-- Copy this complete folder into lua_scripts. This example keeps the authored
-- two-dimensional foundation mask, exact clearance voxels, and reusable
-- three-dimensional excavation mask beside one another for comparison.

function modInit()
  local chamber = betamoon.worldgen.structures:add {
    key = "example:structure/excavated_foundation",
    rotation = "random_horizontal",
    mirror = "random",
    terrain = {
      mode = "foundation",
      surface = "solid_surface",
      anchor = "median",
      maxSlope = 2,
      maxStep = 1,
      verticalOffset = -3,

      masks = {
        plinth = {
          shape = "authored",
          cells = {
            { -2, -2 }, { -1, -2 }, { 0, -2 }, { 1, -2 }, { 2, -2 },
            { -2, -1 }, { -1, -1 }, { 0, -1 }, { 1, -1 }, { 2, -1 },
            { -2,  0 }, { -1,  0 }, { 0,  0 }, { 1,  0 }, { 2,  0 },
            { -2,  1 }, { -1,  1 }, { 0,  1 }, { 1,  1 }, { 2,  1 },
            { -2,  2 }, { -1,  2 }, { 0,  2 }, { 1,  2 }, { 2,  2 }
          }
        }
      },

      foundation = {
        footprint = { source = "named", name = "plinth" },
        edge = { type = "hard" },
        material = {
          layers = {
            { block = 4, weight = 4 },
            { block = 48, weight = 1 }
          }
        },
        maxDepth = 6,
        replace = "terrain_and_vegetation",
        fluids = "reject",
        maxBlocks = 256
      },

      excavation = {
        masks = {
          -- This tunnel reaches one block beyond the authored south wall. It
          -- rotates and mirrors with the rest of the structure.
          entrance = {
            { 0, 1, 2 }, { 0, 2, 2 },
            { 0, 1, 3 }, { 0, 2, 3 }
          }
        },
        volumes = {
          -- No name or cells means the exact terrain_clearance marker voxels
          -- from the JSON template.
          { shape = "authored" },
          { shape = "authored", name = "entrance" }
        },
        result = "air",
        replace = {
          preset = "ordinary_terrain",
          tags = { "#replaceable" },
          excludeBlocks = { 7 },
          match = "any"
        },
        fluids = "reject",
        maxBlocks = 96
      }
    }
  }

  betamoon.worldgen.placements:add {
    key = "example:placement/excavated_foundation",
    feature = chamber,
    stage = "surface_features",
    dimensions = { "overworld" },
    attempts = { perChunk = 4, rarity = 128 },
    successLimit = 1,
    position = {
      height = { type = "solid_surface" },
      horizontal = "grid",
      gridSpacing = 8
    },
    conditions = {
      ground = { 1, 2, 3, 12, 13, 24 },
      air = true,
      requireSky = true,
      site = { type = "land_surface", scope = "support_footprint" }
    },
    salt = 302201
  }
end

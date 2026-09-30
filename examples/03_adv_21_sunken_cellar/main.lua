-- Copy this complete folder into lua_scripts. The structure is deliberately
-- simple so the relationship between fitting, verticalOffset, and excavation
-- remains visible.

function modInit()
  local cellar = betamoon.worldgen.structures:add {
    key = "example:structure/sunken_cellar",
    rotation = "random_horizontal",
    terrain = {
      mode = "fit",
      surface = "solid_surface",
      anchor = "median",
      maxSlope = 1,
      maxStep = 1,

      -- The fitted anchor starts at the first air cell above solid terrain.
      -- Move the five-block-high cellar down so its roof meets that surface.
      verticalOffset = -4,

      -- verticalOffset never removes world blocks. This separate policy clears
      -- the transformed base footprint from the cellar floor through the
      -- original sampled surface before structure blocks are written.
      excavation = {
        footprint = { source = "base_bounds", shape = "bounds" },
        vertical = { from = 0, to = "surface" },
        result = "air",
        replace = "ordinary_terrain",
        fluids = "reject",
        maxBlocks = 160
      }
    }
  }

  betamoon.worldgen.placements:add {
    key = "example:placement/sunken_cellar",
    feature = cellar,
    stage = "surface_features",
    dimensions = { "overworld" },
    attempts = { perChunk = 4, rarity = 96 },
    successLimit = 1,
    position = {
      height = { type = "solid_surface" },
      horizontal = "grid",
      gridSpacing = 8
    },
    conditions = {
      ground = { 1, 2, 3, 12, 13, 24 },
      air = true,
      site = { type = "land_surface", scope = "support_footprint" }
    },
    salt = 302101
  }
end

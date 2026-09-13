-- Load the companion type file first and the fabricator file for an in-game machine.

name = "Advanced Fabrication Recipes Example"
version = "1.0.0"
description = "Adds recipes for the advanced pool, grid, and sequence-matched types."
dependencies = { "Advanced Fabrication Types Example" }

function modInit()
  local bm = betamoon
  local types = bm.modules:import("advanced_fabrication_types")

  bm.recipes:add {
    key = "example:coarse_ceramic_mix",
    type = types.bulkMixing,
    ingredients = {
      -- Pool requirements form a list, not named single slots. The exact
      -- allocator can split or combine matching quantities across bound slots.
      materials = {
        { item = bm.blocks:getRequired(12), count = 2 },
        { item = bm.blocks:getRequired(13), count = 2 }
      }
      -- This recipe omits the optional catalyst, so its bound slot must be empty.
    },
    outputs = {
      result = bm.stack(bm.items:getRequired(337), 4),
      byproducts = { bm.items:getRequired(318), bm.blocks:getRequired(4) }
    }
  }

  bm.recipes:add {
    key = "example:reinforced_frame",
    type = types.gridAssembly,
    ingredients = {
      work = {
        -- This 3x3 pattern may be mirrored or rotated because the type permits
        -- those transforms. Spaces require empty machine cells.
        pattern = { "I I", " S ", "I I" },
        key = {
          I = bm.items:getRequired(265),
          S = bm.items:getRequired(280)
        }
      }
    },
    output = bm.stack(bm.blocks:getRequired(42))
  }

  bm.recipes:add {
    key = "example:ordered_tempering",
    type = types.sequenceAssembly,
    -- Requirement order is meaningful to adjacentSequence: iron must occupy
    -- the cell immediately before coal. Reversing the cells does not match.
    ingredients = { lane = { bm.items:getRequired(265), bm.items:getRequired(263) } },
    output = bm.items:getRequired(266)
  }
end

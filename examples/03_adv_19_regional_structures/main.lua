-- Copy this complete folder into lua_scripts. Three local structure templates
-- become the root and weighted connector-pool pieces of one regional structure.
-- Regional starts are selected on a deterministic chunk grid and their complete
-- plans are saved before individual chunk slices commit.

name = "Regional Structures Example"
version = "1.0.0"
description = "Adds multi-piece Path Ruins assembled from a gate, road segments, and terminal courtyards. " ..
    "Copy the complete 03_adv_19_regional_structures folder into lua_scripts. The root opens a 'path' " ..
    "connector pool; compatible markers on weighted pieces let the planner grow a bounded chain.\n\n" ..
    "Craft a Ruin Surveyor from a compass and paper, then use it on any block. Chat reports the nearest " ..
    "candidate start and whether it has generated. Explore toward those coordinates through new terrain. " ..
    "The example separates reusable local templates, connector data, regional spacing, plan limits, and lookup."

local pathRuins

function modInit()
  local gate = betamoon.worldgen.structures:add {
    key = "example:structure/path_ruins_gate",
    rotation = "random_horizontal",
    processors = { decay = 0.08 }
  }

  local road = betamoon.worldgen.structures:add {
    key = "example:structure/path_ruins_road",
    processors = { decay = 0.06 }
  }

  local courtyard = betamoon.worldgen.structures:add {
    key = "example:structure/path_ruins_courtyard",
    processors = { decay = 0.1 }
  }

  pathRuins = betamoon.worldgen.structures:addRegional {
    key = "example:structure/path_ruins",
    start = gate,
    dimensions = "overworld",
    spacing = 24,
    separation = 8,
    salt = 301901,
    height = { type = "surface" },
    pieces = {
      -- Every choice in this pool must contain at least one connector marker
      -- whose value uses pool = "path". Weight is relative, not a percentage.
      { pool = "path", structure = road, weight = 4 },
      { pool = "path", structure = courtyard, weight = 1 }
    },
    maxDepth = 5,
    maxPieces = 8,
    maxDistance = 96,
    terminationChance = 0.25
  }

  local surveyor = betamoon.items:add {
    id = 5045,
    key = "example:item/ruin_surveyor",
    displayName = "Ruin Surveyor",
    icon = { x = 6, y = 3 },
    maxStackSize = 1,
    onUseOnBlock = {
      action = function(ctx)
        -- locate does not force chunks to load or generate. Before the candidate
        -- has generated, y is nil because its exact surface anchor is not known.
        local location = pathRuins:locate(ctx.world, ctx.position.x, ctx.position.z, 32)
        if not location then
          betamoon.chat:send("No Path Ruins candidate exists in this dimension")
          return betamoon.callbackResults.handled
        end

        local y = location.y and tostring(location.y) or "surface unknown"
        local state = location.generated and "generated" or "not generated yet"
        betamoon.chat:send(
          "Nearest Path Ruins: %i, %s, %i (%s)",
          location.x,
          y,
          location.z,
          state
        )
        return betamoon.callbackResults.handled
      end
    }
  }

  betamoon.recipes:add {
    type = "shapeless",
    output = betamoon.stack(surveyor),
    ingredients = {
      betamoon.items:getRequired(345),
      betamoon.items:getRequired(339)
    }
  }
end

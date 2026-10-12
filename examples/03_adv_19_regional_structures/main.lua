-- Copy this complete folder into lua_scripts. Four local structure templates
-- become the root and weighted connector-pool pieces of one regional structure.
-- Regional starts are selected on a deterministic chunk grid and their complete
-- plans are saved before individual chunk slices commit.

local pathRuins

function modInit()
  local gate = betamoon.worldgen.structures:add {
    key = "example:structure/path_ruins_gate",
    rotation = "random_horizontal",
    terrain = {
      mode = "foundation",
      surface = "solid_surface",
      -- Embed the authored floor in the sampled top terrain layer instead of
      -- placing it in the air block immediately above the surface.
      verticalOffset = -1,
      maxSlope = 4,
      foundation = {
        footprint = { source = "base_bounds", shape = "bounds" },
        edge = { type = "stepped", stepEvery = 2, maxExpansion = 1 },
        material = 4,
        maxDepth = 6,
        maxBlocks = 192
      }
    }
  }

  local road = betamoon.worldgen.structures:add {
    key = "example:structure/path_ruins_road",
    terrain = {
      -- Select the tagged road surface directly. Unlike the rigid ruin pieces,
      -- this lets the route rise and fall with solid ground without one terrain
      -- marker per column.
      mode = "conform",
      surface = "solid_surface",
      -- Each marked road-floor column replaces its local surface block.
      verticalOffset = -1,
      maxStep = 1,
      conform = {
        columns = {
          source = "support",
          select = { tags = { "road_surface" } }
        },
        maxDisplacement = 6,
        maxStep = 1
      }
    }
  }

  local courtyard = betamoon.worldgen.structures:add {
    key = "example:structure/path_ruins_courtyard",
    terrain = {
      mode = "foundation",
      surface = "solid_surface",
      verticalOffset = -1,
      maxSlope = 4,
      masks = {
        courtyard_base = {
          shape = "authored",
          cells = {
            { 0, -2 }, { 1, -2 }, { 2, -2 }, { 3, -2 }, { 4, -2 },
            { 0, -1 }, { 1, -1 }, { 2, -1 }, { 3, -1 }, { 4, -1 },
            { 0,  0 }, { 1,  0 }, { 2,  0 }, { 3,  0 }, { 4,  0 },
            { 0,  1 }, { 1,  1 }, { 2,  1 }, { 3,  1 }, { 4,  1 },
            { 0,  2 }, { 1,  2 }, { 2,  2 }, { 3,  2 }, { 4,  2 }
          }
        }
      },
      foundation = {
        footprint = { source = "named", name = "courtyard_base" },
        edge = { type = "hard" },
        material = 4,
        maxDepth = 6,
        maxBlocks = 256
      }
    }
  }

  local crossroads = betamoon.worldgen.structures:add {
    key = "example:structure/path_ruins_crossroads",
    terrain = {
      mode = "conform",
      surface = "solid_surface",
      verticalOffset = -1,
      maxSlope = 4,
      maxStep = 1,
      conform = {
        columns = {
          source = "support",
          select = { tags = { "road_surface" } }
        },
        maxDisplacement = 6,
        maxStep = 1
      }
    }
  }

  pathRuins = betamoon.worldgen.structures:addRegional {
    key = "example:structure/path_ruins",
    start = gate,
    dimensions = "overworld",
    spacing = 24,
    separation = 8,
    salt = 301901,
    height = { type = "solid_surface" },
    -- Ruins belong on dry land. If the chunk center is unsuitable, the planner
    -- tries a small deterministic set of positions in that same chunk only.
    site = { type = "land_surface", scope = "support_footprint" },
    siteSearch = { attempts = 8, radius = 7 },
    -- Keep connected endpoints aligned. Conforming road connectors move with
    -- their selected columns, so a mismatched endpoint rejects that candidate.
    connectorVerticalTolerance = 0,
    pieces = {
      -- Every choice in this pool must contain at least one connector marker
      -- whose value uses pool = "path". Weight is relative, not a percentage.
      { pool = "path", structure = road, weight = 6 },
      -- Crossroads add three new open routes after the incoming connector is
      -- consumed, allowing one regional plan to grow beyond a short chain.
      { pool = "path", structure = crossroads, weight = 1 },
      { pool = "path", structure = courtyard, weight = 1 }
    },
    maxDepth = 7,
    maxPieces = 12,
    maxDistance = 128,
    terminationChance = 0.08
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

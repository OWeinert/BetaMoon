-- Copy this complete folder into lua_scripts. The manifest makes main.lua the
-- entrypoint, while the structure key resolves its JSON under assets/example/.
-- Local structures are ordinary feature references: they can be scheduled,
-- composed, attached to a biome decorator, or placed directly from a callback.

function modInit()
  -- Reusable loot tables live outside structure geometry. With no path override,
  -- this key resolves assets/example/loot_tables/shrine_supplies.json.
  betamoon.lootTables:add {
    key = "example:shrine_supplies"
  }

  local shrine = betamoon.worldgen.structures:add {
    key = "example:structure/tutorial_shrine",
    -- With no path override, the key above resolves to:
    -- assets/example/worldgen/structures/tutorial_shrine.json
    rotation = "random_horizontal",
    mirror = "random",
    -- Keep the shrine rigid, choose a stable height from its whole footprint,
    -- and fill small gaps instead of leaving edge blocks floating.
    terrain = {
      mode = "foundation",
      surface = "solid_surface",
      anchor = "median",
      maxSlope = 1,
      maxStep = 0,
      -- Replace the sampled top terrain layer with the shrine floor.
      verticalOffset = -1,
      maxFoundationDepth = 4,
      foundationBlock = 4
    },
    processors = {
      -- Air is absent from the template and remains untouched. The structure
      -- JSON owns its seeded moss and decay processor pipeline.
      includeAir = false,
      -- tileCollision refers to placement collisions with TileEntities not normal blocks.
      -- This can occure, for example, when a template gets placed multiple times during structure generation and one
      -- placement would override a TileEntity block from a previous placement attempt.
      tileCollision = "reject",
      -- This will reject placement of the structure if there is an attempt to place a block with unknown metadata set.
      -- For example: A stone block can't have metadata, so the attempt to place a stone block with metadata 1 would be rejected.
      unknownMetadata = "reject"
    }
  }

  local shrinePlacement = betamoon.worldgen.placements:add {
    key = "example:placement/tutorial_shrine",
    feature = shrine,
    stage = "surface_features",
    dimensions = { "overworld" },
    -- Roughly one in sixty-four chunks is eligible. In an eligible chunk, try four
    -- footprint-safe grid positions and stop after placing the first shrine.
    -- This prevents one tree or patch of water from rejecting the whole chunk.
    attempts = { perChunk = 4, rarity = 64 },
    successLimit = 1,
    position = {
      height = { type = "solid_surface" },
      horizontal = "grid",
      -- Centers at 4 and 12 keep this five-block-wide template inside its chunk.
      gridSpacing = 8
    },
    conditions = {
      -- Natural exposed stone is valid too, which keeps hilly terrain from
      -- needlessly rejecting every candidate.
      ground = { 1, 2, 3, 12, 13, 24 },
      air = true,
      requireSky = true,
      -- This checks the complete transformed support footprint. A candidate on
      -- an ocean floor is rejected even though a foundation could reach it.
      site = { type = "land_surface", scope = "support_footprint" }
    },
    salt = 301801
  }

  local surveyor = betamoon.items:add {
    id = 5046,
    key = "example:item/shrine_surveyor",
    displayName = "Shrine Surveyor",
    icon = { x = 6, y = 3 },
    maxStackSize = 1,
    onUseOnBlock = {
      action = function(ctx)
        -- Local placements are not saved structure indexes. This finds the
        -- nearest chunk whose deterministic rarity roll allows an attempt;
        -- terrain conditions can still reject all four positions in that chunk.
        local location = shrinePlacement:locateCandidate(
          ctx.world,
          ctx.position.x,
          ctx.position.z,
          64
        )
        if not location then
          betamoon.chat:send("No eligible shrine chunk was found in range")
          return betamoon.callbackResults.handled
        end

        local state = location.loaded and "currently loaded" or "outside the loaded area"
        betamoon.chat:send(
          "Eligible shrine chunk: center %i, %i (%s)",
          location.x,
          location.z,
          state
        )
        betamoon.chat:send(
          "Search near X %i or %i and Z %i or %i; terrain may reject a candidate",
          location.x - 4,
          location.x + 4,
          location.z - 4,
          location.z + 4
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
      betamoon.items:getRequired(348)
    }
  }
end

-- Copy this complete folder into lua_scripts. The manifest makes main.lua the
-- entrypoint, while the structure key resolves its JSON under assets/example/.
-- Local structures are ordinary feature references: they can be scheduled,
-- composed, attached to a biome decorator, or placed directly from a callback.

function modInit()
  local shrine = betamoon.worldgen.structures:add {
    key = "example:structure/tutorial_shrine",
    -- With no path override, the key above resolves to:
    -- assets/example/worldgen/structures/tutorial_shrine.json
    rotation = "random_horizontal",
    mirror = "random",
    processors = {
      -- Air is absent from the template and remains untouched. Decay is seeded,
      -- so the same world seed and placement always produce the same ruin.
      includeAir = false,
      decay = 0.08,
      tileCollision = "reject",
      unknownMetadata = "reject"
    }
  }

  betamoon.worldgen.placements:add {
    key = "example:placement/tutorial_shrine",
    feature = shrine,
    stage = "surface_features",
    dimensions = { "overworld" },
    -- Roughly one in six chunks is eligible. In an eligible chunk, try four
    -- footprint-safe grid positions and stop after placing the first shrine.
    -- This prevents one tree or patch of water from rejecting the whole chunk.
    attempts = { perChunk = 4, rarity = 6 },
    successLimit = 1,
    position = {
      height = { type = "surface" },
      horizontal = "grid",
      -- Centers at 4 and 12 keep this five-block-wide template inside its chunk.
      gridSpacing = 8
    },
    conditions = {
      -- Natural exposed stone is valid too, which keeps hilly terrain from
      -- needlessly rejecting every candidate.
      ground = { 1, 2, 3, 12, 13, 24 },
      air = true,
      requireSky = true
    },
    salt = 301801
  }
end

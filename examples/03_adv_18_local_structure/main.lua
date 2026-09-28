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
    -- A tutorial structure should be uncommon without becoming frustrating to
    -- find. Conditions can still reject unsuitable surface positions.
    attempts = { perChunk = 1, rarity = 6 },
    position = {
      height = { type = "surface" },
      horizontal = "grid",
      gridSpacing = 4
    },
    conditions = {
      ground = { 2, 3, 12, 13 },
      air = true,
      requireSky = true
    },
    salt = 301801
  }
end

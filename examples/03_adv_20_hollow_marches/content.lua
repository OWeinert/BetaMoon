return function(worldgen)
  local redstone = worldgen.features:add {
    key = "example:feature/marches_redstone",
    displayName = "Mesa redstone vein",
    type = "ore_vein",
    block = 73,
    size = 7,
    replace = { 1 }
  }
  worldgen.placements:add {
    key = "example:placement/marches_redstone",
    displayName = "Redstone beneath mesa regions",
    feature = redstone,
    stage = "underground_features",
    dimensions = { "overworld" },
    attempts = 5,
    position = { height = { type = "uniform", min = 8, max = 45 } },
    spatial = { field = "example:field/marches_regions", min = 0.55 },
    salt = 2003
  }

  local lantern = worldgen.features:add {
    key = "example:feature/marches_lantern",
    displayName = "Floating sky lantern",
    type = "column",
    block = 89,
    height = 1,
    replace = { 0 }
  }
  worldgen.placements:add {
    key = "example:placement/marches_lanterns",
    displayName = "Sky lanterns at the authored height",
    feature = lantern,
    stage = "surface_features",
    dimensions = { "overworld" },
    attempts = { perChunk = 1, rarity = 6 },
    -- This samples a named height output, independently of the terrain surface.
    position = { height = { type = "authored", output = "lanterns" } },
    conditions = { air = true, requireSky = true },
    spatial = { field = "example:field/marches_regions", min = 0.65 },
    salt = 2004
  }
end

-- Composes surfaces and feature references into biome-scoped decorators, then
-- publishes an explicit climate source. Placement declarations here are templates:
-- referencing them from decorators prevents them from generating globally.

return function(features, structures)
  local surface = betamoon.worldgen.surfaces:add {
    key = "example:surface/amber_grove",
    replace = { 1, 2, 3, 12, 13 },
    layers = {
      { block = 2, depth = 1 },
      { block = 3, depth = { min = 3, max = 5 } },
      { block = 13, depth = 1 }
    },
    underwaterBlock = 12,
    seaLevel = 64
  }

  local trees = betamoon.worldgen.placements:add {
    key = "example:placement/amber_grove_trees",
    feature = features.tree,
    stage = "surface_features",
    attempts = { perChunk = { min = 2, max = 4 }, extraChance = 0.25 },
    position = { height = { type = "surface" } },
    conditions = { ground = { 2, 3 }, air = true, requireSky = true },
    salt = 302001
  }

  local groundAccents = betamoon.worldgen.placements:add {
    key = "example:placement/amber_grove_ground_accents",
    feature = features.groundAccents,
    stage = "surface_features",
    attempts = { perChunk = 2, extraChance = 0.4 },
    position = { height = { type = "surface", offset = -1 } },
    salt = 302002
  }

  local circles = betamoon.worldgen.placements:add {
    key = "example:placement/amber_stone_circles",
    feature = structures.stoneCircle,
    stage = "surface_features",
    attempts = { perChunk = 1, rarity = 28 },
    position = {
      height = { type = "solid_surface" },
      horizontal = "grid",
      gridSpacing = 4
    },
    conditions = {
      ground = { 2, 3 },
      air = true,
      requireSky = true,
      site = { type = "land_surface", scope = "support_footprint" }
    },
    salt = 302003
  }

  local amberGrove = betamoon.worldgen.biomes:add {
    key = "example:biome/amber_grove",
    name = "Amber Grove",
    basedOn = betamoon.mc.world.biomes.forest,
    color = 0xd89b46,
    foliageColor = 0xc47f32,
    tags = { "example:forest", "example:amber", "example:temperate" },
    surface = surface,
    decorator = {
      placements = { trees, groundAccents, circles }
    },
    weather = { rain = true, snow = false },
    spawns = {
      [betamoon.mc.world.spawnGroups.creatures] = {
        { entity = betamoon.mc.entities.sheep, weight = 10 },
        { entity = betamoon.mc.entities.pig, weight = 8 }
      }
    }
  }

  betamoon.worldgen.biomeSources:add {
    key = "example:biome_source/amber_grove_climate",
    type = "vanilla_climate",
    active = true,
    priority = 30,
    entries = {
      {
        biome = amberGrove,
        temperature = { min = 0.55, max = 0.85 },
        humidity = { min = 0.45, max = 0.8 },
        priority = 20
      }
    }
  }
end

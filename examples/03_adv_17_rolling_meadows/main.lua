-- JSON describes terrain; Lua connects the registries and ordinary content APIs.
-- Choose Rolling Meadows in Create World. Registration alone does not select it.
function modInit()
  local worldgen = betamoon.worldgen
  worldgen.biomes:add {
    key = "example:biome/rolling_meadow",
    displayName = "Rolling Meadow",
    basedOn = betamoon.mc.world.biomes.plains
  }

  -- Register reusable inputs before their consumers.
  worldgen.spatial.noises:add { path = "assets/worldgen/hills_noise.json" }
  worldgen.spatial.fields:add { path = "assets/worldgen/height.json" }
  worldgen.terrain.sources:add { path = "assets/worldgen/terrain.json" }
  worldgen.carvers:add { path = "assets/worldgen/caves.json" }

  worldgen.ores:add {
    key = "example:placement/meadow_coal",
    displayName = "Meadow coal veins",
    block = 16, -- Minecraft coal ore; no custom content IDs are required.
    veinsPerChunk = 6,
    veinSize = 10,
    height = { min = 6, max = 40 },
    dimension = "overworld",
    salt = 1702
  }
  local oak = worldgen.trees:add {
    key = "example:tree/meadow_oak",
    displayName = "Meadow oak",
    generator = "normal"
  }
  worldgen.placements:add {
    key = "example:placement/meadow_oaks",
    displayName = "Scattered meadow oaks",
    feature = oak,
    stage = "vegetation",
    dimensions = { "overworld" },
    attempts = { perChunk = 1, extraChance = 0.25 },
    position = { height = { type = "solid_surface" } },
    conditions = { ground = { 2, 3 }, air = true, requireSky = true },
    salt = 1703
  }
  worldgen.generatorProfiles:add { path = "assets/worldgen/generator.json" }
end

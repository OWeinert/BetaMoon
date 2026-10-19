return function(worldgen)
  worldgen.biomes:add {
    key = "example:biome/marches_lowlands",
    displayName = "Marches Lowlands",
    basedOn = betamoon.mc.world.biomes.forest,
    tags = { "example:marches_lowland" }
  }
  worldgen.biomes:add {
    key = "example:biome/marches_mesas",
    displayName = "Hollow Mesa",
    basedOn = betamoon.mc.world.biomes.desert,
    tags = { "example:marches_mesa" }
  }
end

-- This preset shares a spatial field between terrain, biomes and placements.
-- JSON owns the graphs and terrain data; private Lua modules wire the registries.
function modInit()
  local worldgen = betamoon.worldgen
  require("biomes")(worldgen)
  require("generation")(worldgen)
  require("content")(worldgen)
  worldgen.generatorProfiles:add { path = "assets/worldgen/generator.json" }
  require("surveyor")(worldgen)
end

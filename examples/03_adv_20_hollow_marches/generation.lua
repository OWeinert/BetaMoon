-- The file order makes each dependency visible before its consumers.
return function(worldgen)
  for _, file in ipairs { "region_noise", "cave_noise" } do
    worldgen.spatial.noises:add { path = "assets/worldgen/" .. file .. ".json" }
  end
  for _, file in ipairs { "warp", "regions", "cave_field", "lantern_height" } do
    worldgen.spatial.fields:add { path = "assets/worldgen/" .. file .. ".json" }
  end
  for _, file in ipairs { "lowlands", "mesas" } do
    worldgen.terrain.profiles:add { path = "assets/worldgen/" .. file .. ".json" }
  end
  worldgen.materialRules:add { path = "assets/worldgen/materials.json" }
  worldgen.terrain.sources:add { path = "assets/worldgen/terrain.json" }
  worldgen.carvers:add { path = "assets/worldgen/caves.json" }
end

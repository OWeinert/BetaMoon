-- Structure gameplay data stays in assets/. Lua supplies its stable identity,
-- transform policy, and processors, then returns the ordinary feature reference.

return function()
  local stoneCircle = betamoon.worldgen.structures:add {
    key = "example:structure/amber_stone_circle",
    rotation = "random_horizontal",
    mirror = "random",
    terrain = {
      mode = "fit",
      surface = "solid_surface",
      anchor = "median",
      maxSlope = 2,
      maxStep = 1,
      requireSupportRatio = 0.6
    },
    processors = {
      decay = 0.08,
      includeAir = false
    }
  }

  return {
    stoneCircle = stoneCircle
  }
end

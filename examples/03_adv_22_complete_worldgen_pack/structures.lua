-- This capstone uses the table-backed builder because a short loop describes
-- the asymmetric pillars more clearly than a separate static asset. Builder
-- helpers append ordinary structure records and compile them once at startup.

return function()
  local structures = betamoon.worldgen.structures
  local circle = structures:builder {
    palette = {
      stone = {
        variants = {
          { block = 4, weight = 3 },
          { block = 48, weight = 2 }
        },
        tags = { "weatherable" }
      },
      lamp = { block = 89, tags = { "protected" } }
    }
  }

  circle:cylinder {
    base = { 0, 0, 0 },
    radius = 3,
    height = 1,
    mode = "shell",
    state = "stone"
  }

  for _, pillar in ipairs {
    { x = -3, z =  0, height = 2 },
    { x =  3, z =  0, height = 2 },
    { x =  0, z = -3, height = 3 },
    { x =  0, z =  3, height = 2 }
  } do
    circle:fill {
      from = { pillar.x, 1, pillar.z },
      to = { pillar.x, pillar.height, pillar.z },
      state = "stone"
    }
  end

  circle:block { pos = { 0, 0, 0 }, state = "lamp" }
  circle:processor {
    type = "decay",
    key = "missing_stones",
    select = { tags = { "weatherable" }, minExposedFaces = 1 },
    chance = 0.08
  }

  local stoneCircle = structures:add {
    key = "example:structure/amber_stone_circle",
    template = circle:compile(),
    rotation = "random_horizontal",
    mirror = "random",
    terrain = {
      mode = "fit",
      surface = "solid_surface",
      anchor = "median",
      maxSlope = 2,
      maxStep = 1,
      requireSupportRatio = 0.6
    }
  }

  return {
    stoneCircle = stoneCircle
  }
end

-- This is the shortest modern biome workflow: register a reusable surface,
-- adapt a vanilla biome under a stable key, then select it through a biome source.
-- The original Desert remains unchanged because basedOn copies its initial settings.
-- Explore new chunks to see the gravel surface. Beta 1.7.3 recalculates biome
-- selection, so colors and weather can change over old terrain, but blocks do not.

name = "Custom Biome From Default Example"
version = "3.0.0"
description = "Adds Example Desert Copy with the modern keyed biome API. It inherits Minecraft's Desert " ..
    "settings, replaces its surface with gravel, remains dry, and is selected for a hot, dry part " ..
    "of the vanilla climate map.\n\n" ..
    "Create a new world or explore newly generated terrain to find the gravel-covered desert " ..
    "variant. It is not guaranteed near spawn. Existing terrain blocks are not rewritten, although " ..
    "Beta 1.7.3 recalculates biome selection, so biome-dependent colors and weather can change over " ..
    "old coordinates. Compare the three stable declarations: surface, biome, and biome source."

function modInit()
  -- A surface is reusable terrain-layer data. Keys are typed automatically;
  -- this explicit form makes the resource category obvious in a tutorial.
  local gravelSurface = betamoon.worldgen.surfaces:add {
    key = "example:surface/gravel_desert",
    -- Only these ordinary terrain blocks may be rewritten by this surface.
    replace = { 1, 2, 3, 12, 13 },
    layers = {
      -- Layers are listed from the exposed top downward.
      { block = 13, depth = 1 },
      { block = 12, depth = 3 }
    },
    underwaterBlock = 13
  }

  local desertCopy = betamoon.worldgen.biomes:add {
    key = "example:biome/gravel_desert",
    name = "Example Desert Copy",
    basedOn = betamoon.mc.world.biomes.desert,
    tags = { "example:dry", "example:sandy" },
    surface = gravelSurface,
    -- No decorator is needed because this lesson deliberately adds no trees
    -- or other biome-specific features.
    weather = { rain = false, snow = false }
  }

  -- A keyed biome does not place itself. The active biome source maps climate
  -- cells to biome references. Cells outside this entry keep vanilla selection.
  betamoon.worldgen.biomeSources:add {
    key = "example:biome_source/gravel_desert_climate",
    type = "vanilla_climate",
    active = true,
    priority = 10,
    entries = {
      {
        biome = desertCopy,
        temperature = { min = 0.95, max = 1 },
        humidity = { min = 0, max = 0.2 }
      }
    }
  }
end

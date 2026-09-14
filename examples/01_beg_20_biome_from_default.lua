-- Start with a vanilla biome when only a few settings should differ.
-- basedOn copies its configuration; this does not globally change the original Desert.
-- The climate range chooses where the custom biome can be selected in new terrain.
-- Compare this shorter declaration with the biome-from-scratch example, which spells out more settings.

name = "Custom Biome From Default Example"
version = "2.0.0"
description = "Adds Example Desert Copy by starting with Minecraft's Desert biome and changing selected " ..
    "properties. The new biome has a gravel surface, no trees, and no rain or snow, and is " ..
    "eligible in a hot, dry climate range.\n\n" ..
    "Explore newly generated terrain or create a new world to look for the gravel-covered desert " ..
    "variant. Existing chunks and the original Desert definition are not rewritten, and the new " ..
    "biome is not guaranteed near spawn. Compare the inherited desert properties with the explicit " ..
    "surface, climate, tree, and weather settings in the script."

function modInit()
  -- basedOn starts with a copy of an existing Minecraft biome.
  betamoon.worldgen.biomes:add {
    name = "Example Desert Copy",
    -- mc.world.biomes lists the vanilla biomes and supplies their exact names.
    -- The plain string "Desert" remains valid too.
    basedOn = betamoon.mc.world.biomes.desert,
    -- You only need to write the settings you want to change.
    surface = {
      -- Vanilla ID 13 is gravel. Only the exposed top changes here;
      -- the other settings begin with the chosen Desert configuration.
      top = 13
    },
    range = {
      -- Both climate values use a scale from 0 to 1. This narrow hot, dry
      -- range controls where the biome may be selected during terrain creation.
      temperature = { min = 0.95, max = 1 },
      humidity = { min = 0, max = 0.2 }
    },
    trees = {
      -- treeModes lists the four supported policies: default, normal, big, and none.
      -- Their quoted names remain valid. none disables normal tree-generation attempts.
      type = betamoon.worldgen.treeModes.none
    },
    weather = {
      -- Explicitly disabling both options keeps the copied desert dry.
      rain = false,
      snow = false
    }
  }
end

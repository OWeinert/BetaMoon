-- Register one fluid with paired blocks, a filled bucket, and independent
-- contact and camera behavior. Obtain the Acid Bucket (5047) from the creative
-- inventory, place a source, and step into it to compare with ordinary water.
-- Copy example/textures/shared/acid_frames.png beside this script.
-- The native BetaMoon agent must be enabled for fluid entity/camera policies.

name = "Custom Fluid Example"
version = "1.0.0"
description = "Adds green acid with water-like movement but no drowning or fire extinguishing. " ..
    "Obtain Acid Bucket (5047) from the creative inventory and place a source. " ..
    "After one second of contact it deals one damage every 20 ticks; entering it " ..
    "also adds green fog without a water overlay. Copy the example folder " ..
    "beside this script. Requires the BetaMoon Java agent."

function modInit()
  local acidTexture = {
    asset = "example/textures/shared/acid_frames.png",
    animation = { frameTicks = 3, clock = "unpaused" }
  }

  betamoon.fluids:add {
    key = "example:fluid/lesson_acid",
    -- A compatibility carrier lets native Minecraft treat these blocks as liquid;
    -- BetaMoon's fluid behavior is selected by the registered identity below.
    material = betamoon.mc.blockMaterials.water,
    preset = "water",

    blocks = {
      flowing = { id = 251, key = "example:block/lesson_acid_flowing" },
      still = { id = 252, key = "example:block/lesson_acid_still" }
    },
    bucket = {
      id = 5047,
      key = "example:item/lesson_acid_bucket",
      displayName = "Acid Bucket",
      icon = { x = 11, y = 4 }
    },
    appearance = {
      still = acidTexture,
      flowing = acidTexture,
      tint = 0xFFFFFF,
      renderPass = "translucent"
    },
    -- Finite fluid spreads normally but never creates a new source from neighbors.
    flow = { tickRate = 8, finite = true },

    entity = {
      movement = "water",
      currentScale = 0.6,
      drowning = false,
      countsAsWet = false,
      extinguishesFire = false,
      contactDamage = 1,
      contactIntervalTicks = 20
    },
    camera = {
      fogColor = 0x29451B,
      fogDensity = 0.16,
      overlay = false
    }
  }
end

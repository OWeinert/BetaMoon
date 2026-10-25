-- Copy this complete folder into betamoon/scripts. Its sound file belongs in
-- assets/sounds/ inside the package. Custom step sounds require the Java agent.

function modInit()
  local ceramicMaterial = betamoon.materials.blocks:add {
    key = "example:material/ceramic",
    kind = "solid",
    mapColor = "stone",
    harvestable = true
  }

  local tapClip = betamoon.assets.sounds:add {
    key = "example:block/ceramic/tap",
    path = "sounds/ceramic_tap.wav"
  }
  local tapEvent = betamoon.soundEvents:add {
    key = "example:block/ceramic/tap",
    sound = tapClip
  }
  local ceramicStep = betamoon.stepSounds:add {
    key = "example:step_sound/ceramic",
    event = tapEvent,
    volume = 0.8,
    pitch = 1
  }

  betamoon.blocks:add {
    id = 248,
    key = "example:block/ceramic_tile",
    displayName = "Ceramic Tile",
    material = ceramicMaterial,
    stepSound = ceramicStep,
    texture = 1,
    hardness = 1.5,
    resistance = 5,
    harvest = { pickaxe = 0 }
  }

  -- Vanilla materials have references too; vanilla step sounds remain names.
  betamoon.blocks:add {
    id = 249,
    key = "example:block/stone_comparison",
    displayName = "Stone Comparison Block",
    material = betamoon.materials.blocks:getRequired("rock"),
    stepSound = betamoon.stepSounds:getRequired("stone"),
    texture = 1,
    hardness = 1.5,
    resistance = 5,
    harvest = { pickaxe = 0 }
  }
end

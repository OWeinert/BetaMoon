-- This lesson compares three focused block definitions: a replaceable cross plant,
-- translucent luminous glass, and an unbreakable machine casing. Obtain IDs 219-221
-- from the creative inventory or a debug command and compare their behavior.

name = "Special Blocks Example"
version = "1.0.0"
description = "Adds three blocks that demonstrate special physical and rendering properties. No recipes are " ..
    "included; obtain IDs 219, 220, and 221 with an inventory editor or item-spawning tool.\n\n" ..
    "Replaceable Sprout (219) has a crossed-plant appearance and no collision. Walk through it or " ..
    "place another block into its space to test replacement. Luminous Glass (220) is translucent " ..
    "and emits light; place it in a dark area to compare its illumination and see-through " ..
    "rendering with ordinary blocks.\n\n" ..
    "Sealed Casing (221) demonstrates an unbreakable, highly blast-resistant block that cannot be " ..
    "moved by pistons. Use a test location when placing it, since normal survival mining will not " ..
    "remove it. Compare each block's declaration with these different behaviors."

function modInit()
  local plantSelection = { min = { 0.2, 0, 0.2 }, max = { 0.8, 0.8, 0.8 } }
  -- A cross renderer draws two crossed planes. Empty collision makes the sprout
  -- passable, while replaceable lets another placed block take its position.
  betamoon.blocks:add {
    id = 219,
    key = "replaceable_sprout",
    displayName = "Replaceable Sprout",
    -- blockMaterials and stepSounds expose the native names used by block declarations.
    material = betamoon.mc.blockMaterials.plants,
    texture = 13,
    hardness = 0,
    stepSound = betamoon.mc.stepSounds.grass,
    opaque = false,
    normalCube = false,
    replaceable = true,
    collision = { boxes = {} },
    selection = plantSelection,
    render = { preset = "cross" }
  }

  -- Rendering in pass 1 and blocking no light creates a translucent full block.
  -- normalCube is false so Minecraft does not treat it as opaque solid support.
  betamoon.blocks:add {
    id = 220,
    key = "luminous_glass",
    displayName = "Luminous Glass",
    material = betamoon.mc.blockMaterials.glass,
    texture = 49,
    hardness = 0.3,
    light = 13,
    lightOpacity = 0,
    opaque = false,
    normalCube = false,
    render = { pass = 1 }
  }

  -- unbreakable sets the mining hardness to the engine's unbreakable value.
  -- The piston rule independently prevents the casing from being moved.
  betamoon.blocks:add {
    id = 221,
    key = "sealed_casing",
    displayName = "Sealed Casing",
    material = betamoon.mc.blockMaterials.iron,
    texture = 42,
    resistance = 2000,
    stepSound = betamoon.mc.stepSounds.metal,
    unbreakable = true,
    piston = { reaction = "block" }
  }
end

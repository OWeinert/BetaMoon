-- Craft stone + glowstone dust for four lamps. A lamp can attach to a floor,
-- wall, or ceiling, and drops when its supporting block is removed.
-- attachedFace records the actual support direction in the block's metadata.

name = "Attached Blocks Example"
version = "1.0.0"
description = "Creates a small lamp that can attach to any solid face."

function modInit()
  -- Reuse one local box for collision, targeting, and drawing so the visible
  -- lamp and its physical bounds stay aligned.
  local lampBox = { min = { 0.25, 0.25, 0.25 }, max = { 0.75, 0.75, 0.75 } }
  local lamp = betamoon.blocks:add {
    id = 217,
    key = "attached_lamp",
    displayName = "Attached Lamp",
    -- blockMaterials supplies the canonical glass material name.
    material = betamoon.mc.blockMaterials.glass,
    hardness = 0.3,
    texture = 89,
    light = 12,
    lightOpacity = 0,
    opaque = false,
    normalCube = false,
    state = {
      -- All six values are required because placement may use any block face.
      -- Their order is persistent: changing it reinterprets saved metadata.
      attachedFace = {
        type = "enum",
        values = { "down", "up", "north", "south", "west", "east" }
      }
    },
    placement = {
      attachTo = { "floor", "wall", "ceiling" },
      requiresSolidSupport = true,
      dropWhenUnsupported = true
    },
    collision = { boxes = { lampBox } },
    selection = lampBox,
    -- pass 1 renders after opaque blocks, which suits the glass material.
    render = { preset = "cuboid", bounds = lampBox, pass = 1 }
  }

  betamoon.recipes:add {
    type = "shapeless",
    output = betamoon.stack(lamp, 4),
    ingredients = { betamoon.blocks:getRequired(1), betamoon.items:getRequired(348) }
  }
end

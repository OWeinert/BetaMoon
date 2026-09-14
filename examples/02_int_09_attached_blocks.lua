-- Craft stone + glowstone dust for four lamps. A lamp can attach to a floor,
-- wall, or ceiling, and drops when its supporting block is removed.
-- attachedFace records the actual support direction in the block's metadata.

name = "Attached Blocks Example"
version = "1.0.0"
description = "Adds Attached Lamp, a small light-emitting block that can attach to a solid floor, wall, or " ..
    "ceiling. Combine one stone block and one glowstone dust in any arrangement to craft four " ..
    "lamps.\n\n" ..
    "Place lamps against different faces of solid supporting blocks, then view them in a dark " ..
    "area. Their visible cuboid, collision box, and selection outline occupy only the central part " ..
    "of a block. Remove a support to see the attached lamp drop automatically. Compare the " ..
    "attachment state and support rules with the shape and light settings; no right-click switch " ..
    "or fuel is required."

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

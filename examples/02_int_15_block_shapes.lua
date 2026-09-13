-- A block can have separate shapes for collision, targeting, and drawing.
-- This example deliberately uses the same box for all three so they agree in-game.
-- Craft planks + stick for four climbing posts; stack them vertically and move against them.
-- The dimensions below use block-local coordinates: one full block spans 0 to 1.

name = "Block Shapes Example"
version = "1.0.0"
description = "Craft planks with a stick for narrow climbing posts with matching physical and visual shapes."

function modInit()
  -- min/max are opposite {x, y, z} corners, not a position and a size.
  -- Centering x and z between 0.375 and 0.625 makes a quarter-block-wide post.
  local postBox = { min = { 0.375, 0, 0.375 }, max = { 0.625, 1, 0.625 } }
  local post = betamoon.blocks:add {
    id = 215,
    key = "example_climbing_post",
    displayName = "Climbing Post",
    -- blockMaterials supplies the canonical native material name.
    material = betamoon.mc.blockMaterials.wood,
    texture = 4,
    hardness = 0.5,
    opaque = false,
    -- Keep physical collision without the suffocation and push-out behavior of a full cube.
    normalCube = false,
    lightOpacity = 0,
    -- This opts into ladder-like movement; it does not choose a ladder model.
    -- The narrow column appearance still comes from the shared postBox.
    climbable = true,
    collision = { boxes = { postBox } },
    selection = postBox,
    render = { preset = "cuboid", bounds = postBox },
    piston = { reaction = "block" }
  }

  local planks = betamoon.blocks:getRequired(5)
  betamoon.recipes:add {
    type = "shapeless", output = betamoon.stack(post, 4),
    ingredients = { planks, betamoon.items:getRequired(280) }
  }
end

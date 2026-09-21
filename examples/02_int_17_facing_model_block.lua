-- Copy facing_model_block/ beside this script in lua_scripts.
-- One south-facing model is rotated through state-dependent appearances.

name = "Facing Model Block Example"
version = "1.0.0"
description = "Adds a walkable stair-shaped block (233). Copy facing_model_block/ " ..
    "beside this script, then place the block while facing different directions. " ..
    "The four metadata variants rotate one built-in model, while BetaMoon rotates " ..
    "the two-part collision shape with the saved facing."

function modInit()
  local stone = betamoon.assets.textures:add {
    key = "example:block/stair_stone",
    path = "facing_model_block/stair_stone.png"
  }

  local function facingAppearance(yaw)
    return {
      model = "minecraft:block/stairs",
      texture = stone,
      rotation = { x = 0, y = yaw, z = 0 }
    }
  end

  local fullBox = { min = { 0, 0, 0 }, max = { 1, 1, 1 } }
  betamoon.blocks:add {
    id = 233, key = "example:block/lesson_facing_stair", displayName = "Facing Stair Display",
    material = betamoon.mc.blockMaterials.rock,
    harvest = { pickaxe = 0 },
    texture = 1, opaque = false, normalCube = false, lightOpacity = 0,
    state = {
      facing = { type = "enum", values = { "north", "east", "south", "west" } }
    },
    placement = { facing = "horizontal", facingFrom = "player" },
    -- Shapes are authored for the default north facing and rotate with the state.
    collision = { boxes = {
      { min = { 0, 0, 0 }, max = { 1, 0.5, 1 } },
      { min = { 0, 0.5, 0.5 }, max = { 1, 1, 1 } }
    } },
    selection = fullBox,
    render = {
      variants = {
        [0] = { appearance = facingAppearance(0) },
        [1] = { appearance = facingAppearance(270) },
        [2] = { appearance = facingAppearance(180) },
        [3] = { appearance = facingAppearance(90) }
      }
    }
  }
end

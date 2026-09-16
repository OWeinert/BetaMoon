-- Copy facing_model_block/ beside this script in lua_scripts.
-- One south-facing model is rotated through state-dependent appearances.

name = "Facing Model Block Example"
version = "1.0.0"
description = "Adds a decorative stair-shaped block (233). Copy facing_model_block/ " ..
    "beside this script, then place the block while facing different directions. " ..
    "The four metadata variants rotate one built-in model. The simple full-block " ..
    "collision and selection are explicit; this is not a walkable stair."

function modInit()
  local stone = betamoon.assets.textures:add {
    key = "mymod:lessons/stair_stone",
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
    id = 233, key = "lesson_facing_stair", displayName = "Facing Stair Display",
    material = betamoon.mc.blockMaterials.rock,
    texture = 1, opaque = false, normalCube = false, lightOpacity = 0,
    state = {
      facing = { type = "enum", values = { "north", "east", "south", "west" } }
    },
    placement = { facing = "horizontal", facingFrom = "player" },
    collision = { boxes = { fullBox } },
    selection = fullBox,
    render = {
      variants = {
        [0] = { appearance = facingAppearance(180) },
        [1] = { appearance = facingAppearance(90) },
        [2] = { appearance = facingAppearance(0) },
        [3] = { appearance = facingAppearance(270) }
      }
    }
  }
end

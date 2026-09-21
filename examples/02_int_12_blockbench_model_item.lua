-- Copy blockbench_model_item/ beside this script in lua_scripts.
-- The JSON uses the same Bedrock 1.12 geometry structure Blockbench exports.

name = "Blockbench Model Item Example"
version = "1.0.0"
description = "Adds a modelled desk lamp (5033). Copy blockbench_model_item/ beside this " ..
    "script, obtain the item, and compare its inventory, held, and dropped poses. " ..
    "The model's named base, stem, and shade parts can also be inspected with a pose."

function modInit()
  local lampModel = betamoon.assets.models:add {
    key = "example:item/desk_lamp",
    path = "blockbench_model_item/desk_lamp.json"
  }
  local lampTexture = betamoon.assets.textures:add {
    key = "example:item/desk_lamp",
    path = "blockbench_model_item/desk_lamp.png"
  }

  local pose = lampModel:createPose()
  assert(pose:hasPart("shade"), "The model must keep its named shade part")

  betamoon.items:add {
    id = 5033, key = "example:item/lesson_desk_lamp", displayName = "Desk Lamp Model Item",
    appearance = {
      model = lampModel, texture = lampTexture,
      display = {
        gui = { scale = 0.8, rotation = { x = 20, y = 30, z = 0 } },
        held = { scale = 0.7 },
        ground = { scale = 0.65 }
      }
    }
  }
end

-- Copy animated_model_item/ beside this script in lua_scripts.
-- The exported clip animates the head; onPose adds a small Lua-controlled turn.

name = "Animated Model Item Example"
version = "1.0.0"
description = "Adds a clockwork bird (5034). Copy animated_model_item/ beside this " ..
    "script and obtain the item. Its head and wings move from numeric animation JSON " ..
    "while Lua adds a gentle head turn using the supplied render time."

function modInit()
  local model = betamoon.assets.models:add {
    key = "mymod:lessons/clockwork_bird",
    path = "animated_model_item/clockwork_bird.json"
  }
  local animation = betamoon.assets.animations:add {
    key = "mymod:lessons/clockwork_bird",
    path = "animated_model_item/clockwork_bird.animation.json"
  }
  local texture = betamoon.assets.textures:add {
    key = "mymod:lessons/clockwork_bird",
    path = "animated_model_item/clockwork_bird.png"
  }

  betamoon.items:add {
    id = 5034, key = "lesson_clockwork_bird", displayName = "Clockwork Bird",
    appearance = {
      model = model, texture = texture,
      animation = { asset = animation, clip = "animation.bird.idle" },
      onPose = function(ctx)
        ctx.pose:rotate("head", {
          x = 0, y = math.sin(ctx.state.ageTicks * 0.08) * 12, z = 0
        })
      end,
      display = { gui = { scale = 0.8 }, held = { scale = 0.8 } }
    }
  }
end

-- Copy click_sound/ beside this script in lua_scripts.
-- Local playback is suitable for feedback to the player running this script.

name = "Registered Sound Example"
version = "1.0.0"
description = "Adds a clicker (5032). Copy click_sound/ beside this script, obtain the " ..
    "item through a creative inventory, then right-click to hear its registered WAV. " ..
    "The sound plays locally; it is not broadcast to other players."

function modInit()
  local click = betamoon.assets.sounds:add {
    key = "example:item/click",
    path = "click_sound/click.wav"
  }

  betamoon.items:add {
    id = 5032, key = "example:item/lesson_clicker", displayName = "Clicker",
    icon = { x = 10, y = 3 },
    onUse = {
      action = function(ctx)
        betamoon.audio:play(click, { volume = 0.6 })
        return betamoon.callbackResults.handled
      end
    }
  }
end

-- Events run a function when something happens later in the game.
-- Load this script, enter a world, and break a block to see its message.
-- Global gameplay events require the BetaMoon Java agent described in the README.

name = "First Event Example"
version = "1.0.0"
description = "Sends a chat message whenever the player breaks a block."

function modInit()
  -- events:on returns a subscription owned by this script. BetaMoon removes it
  -- automatically when the script unloads, so reloads do not duplicate the listener.
  local subscription = betamoon.events:on("block_broken", function(event)
    -- This event supplies the broken block's display name and world coordinates.
    betamoon.chat:send(
      "Broke %s at %i, %i, %i",
      event.displayName,
      event.x,
      event.y,
      event.z
    )
  end)

  assert(subscription.active)
end

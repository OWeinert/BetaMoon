-- Events run a function when something happens later in the game.
-- Load this script, enter a world, and break a block to see its message.
-- Global gameplay events require the BetaMoon Java agent described in the README.

name = "First Event Example"
version = "1.0.0"
description = "Demonstrates a single global block-broken event subscription. When you break a block, the " ..
    "callback sends its display name and world coordinates to your chat.\n\n" ..
    "Run Minecraft with BetaMoon's instrumentation agent enabled, enter a world, and break a few " ..
    "different blocks. Compare each chat message with the block and position you just mined. The " ..
    "script adds no custom content. Its subscription is cleaned up when the script unloads or " ..
    "reloads, so reloading should not accumulate duplicate listeners."

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

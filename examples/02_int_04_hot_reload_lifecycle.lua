-- This example makes script ownership and the three lifecycle hooks visible.
-- Change the greeting, save the file, and reload scripts while a world is open.
-- The old generation runs modUnload; the replacement then runs modInit and modReload.

name = "Hot Reload Lifecycle Example"
version = "1.0.0"
description = "Demonstrates initialization, world-join handling, unloading, and successful reload callbacks. " ..
    "While loaded, it renames vanilla dirt to Lifecycle Dirt and reports lifecycle steps through " ..
    "chat.\n\n" ..
    "Enter a world, inspect a dirt item, then edit the greeting or display-name text in the source " ..
    "and reload the script. Watch the unload, initialization, and reload messages to follow the " ..
    "transition, and inspect dirt again for the updated name. The unload callback explicitly " ..
    "removes the previous override and event subscription. This example changes a vanilla name but " ..
    "adds no blocks or items."

local worldJoinSubscription
local dirtOverride

function modInit()
  betamoon.chat:send("Lifecycle example initialized")

  -- Store cleanup handles outside modInit because modUnload runs later.
  worldJoinSubscription = betamoon.events:on("world_join", function(event)
    betamoon.chat:send("Lifecycle example entered %s", event.name)
  end)

  -- The visible rename makes ownership cleanup easy to verify in-game.
  dirtOverride = betamoon.blocks:getRequired(3):override {
    displayName = "Lifecycle Dirt"
  }
end

function modReload()
  -- This belongs to the new generation, after its modInit succeeds.
  betamoon.chat:send("Lifecycle example reloaded successfully")
end

function modUnload()
  -- Explicit cleanup is useful when the script owns other Lua-side state.
  -- BetaMoon also releases tracked subscriptions and overrides automatically.
  if worldJoinSubscription and worldJoinSubscription.active then
    worldJoinSubscription:unsubscribe()
  end
  if dirtOverride and dirtOverride.active then
    dirtOverride:remove()
  end

  betamoon.chat:send("Lifecycle example unloaded")
end

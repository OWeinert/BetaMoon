name = "Events Example"
version = "2.0.0"
description = "Subscribes to every event and demonstrates event fields."

function modInit()
  -- Available events:
  -- world_join, world_leave, player_join, player_leave, gui_opened,
  -- gui_closed, gui_tick, game_tick, screen_changed, key_input,
  -- mouse_input, block_broken, block_placed, item_used, dimension_changed.
  --
  -- events:on runs a function whenever the named event happens.
  -- It returns a subscription that can be stopped with unsubscribe().
  local worldJoinSubscription = betamoon.events:on("world_join", function(event)
    -- World events contain the world's name and more details in info.
    local worldName = event.name
    local worldInfo = event.info
  end)

  -- The subscription stays active until it is stopped or this script unloads.
  assert(worldJoinSubscription.active)
  -- worldJoinSubscription:unsubscribe()

  betamoon.events:on("world_leave", function(event)
    local worldName = event.name
    local worldInfo = event.info
  end)

  betamoon.events:on("player_join", function(event)
    -- Player events contain the player's name.
    local playerName = event.name
  end)

  betamoon.events:on("player_leave", function(event)
    local playerName = event.name
  end)

  betamoon.events:on("gui_opened", function(event)
    -- GUI events contain the name of the current screen.
    local screenName = event.name
  end)

  betamoon.events:on("gui_closed", function(event)
    local closedScreenName = event.name
  end)

  betamoon.events:on("gui_tick", function(event)
    -- gui_tick runs every game tick while a menu or screen is open.
    local screenName = event.name
  end)

  betamoon.events:on("game_tick", function(event)
    -- game_tick runs every game tick while playing in a world.
    local worldName = event.worldName
    local worldInfo = event.worldInfo
  end)

  betamoon.events:on("screen_changed", function(event)
    -- previousName is nil when there was no previous screen.
    -- name is nil when the player returns to normal gameplay.
    local previousScreenName = event.previousName
    local currentScreenName = event.name
  end)

  betamoon.events:on("key_input", function(event)
    -- Keyboard input tells you the key, typed character, and what happened.
    local keyCode = event.keyCode
    local typedCharacter = event.char
    local action = event.action
    local pressed = event.pressed
    local released = event.released
  end)

  betamoon.events:on("mouse_input", function(event)
    -- Mouse input tells you the button, cursor position, and what happened.
    local button = event.button
    local mouseX = event.x
    local mouseY = event.y
    local action = event.action
    local pressed = event.pressed
    local released = event.released
  end)

  betamoon.events:on("block_broken", function(event)
    -- Block events contain the block and its position in the world.
    betamoon.chat:send(
      "Broke %s at %i, %i, %i",
      event.displayName,
      event.x,
      event.y,
      event.z
    )

    local blockId = event.id
    local damage = event.damage
    local position = event.position
  end)

  betamoon.events:on("block_placed", function(event)
    local blockId = event.id
    local blockName = event.name
    local displayName = event.displayName
    local side = event.side
    local position = event.position
  end)

  betamoon.events:on("item_used", function(event)
    -- Item use events contain the item handle and stack details.
    local item = event.item
    local itemId = event.itemId
    local count = event.count
    local damage = event.damage
  end)

  betamoon.events:on("dimension_changed", function(event)
    -- Dimension events contain the dimension before and after the move.
    local previousDimensionId = event.oldId
    local currentDimensionId = event.newId
  end)
end

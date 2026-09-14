-- Press G during gameplay to print a greeting. Press the middle mouse button
-- to print its cursor position. Input events also fire while a GUI is open,
-- so screen_changed keeps a small amount of Lua state to suppress the shortcut there.

name = "Input Shortcuts Example"
version = "1.0.0"
description = "Adds two example input shortcuts while you are playing: press G to print a greeting, or press " ..
    "the middle mouse button to print the current mouse coordinates in chat.\n\n" ..
    "Try both shortcuts with normal gameplay visible, then open your inventory or another GUI and " ..
    "try again. The script tracks the current screen and suppresses its shortcuts while a GUI is " ..
    "open. The reported coordinates are mouse coordinates, not the world position of the block you " ..
    "are looking at. Compare the key/button checks with the screen-change subscription and " ..
    "pressed-event handling."

local currentScreen

function modInit()
  -- A nil screen name means normal world gameplay. Any other value identifies
  -- the open menu so gameplay shortcuts can ignore typing inside GUIs.
  betamoon.events:on("screen_changed", function(event)
    currentScreen = event.name
  end)

  betamoon.events:on("key_input", function(event)
    -- Use pressed instead of action text when only the transition matters.
    -- char is convenient for a tutorial shortcut that follows the typed key.
    if currentScreen == nil and event.pressed and event.char:lower() == "g" then
      betamoon.chat:send("Hello from the G shortcut")
    end
  end)

  betamoon.events:on("mouse_input", function(event)
    -- Mouse button 2 is the middle button in LWJGL's zero-based numbering.
    if currentScreen == nil and event.pressed and event.button == 2 then
      betamoon.chat:send("Middle click at %i, %i", event.x, event.y)
    end
  end)
end

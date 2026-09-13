-- Press G during gameplay to print a greeting. Press the middle mouse button
-- to print its cursor position. Input events also fire while a GUI is open,
-- so screen_changed keeps a small amount of Lua state to suppress the shortcut there.

name = "Input Shortcuts Example"
version = "1.0.0"
description = "Builds simple keyboard and mouse shortcuts from input events."

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

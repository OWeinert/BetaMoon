-- Press F6 from the main menu or a world. Rebind it in Options > Controls > BetaMoon.
-- Unknown/protected native screens refuse opening; Escape or Close returns safely.
name = "First Client Screen Example"
version = "1.0.0"
description = "Press F6 to open a worldless client screen with shared text and button controls."

function modInit()
  local ui = betamoon.client.ui
  ui.screens:add {
    key = "example:first_screen",
    root = {
      type = "column", width = 200, gap = 8,
      children = {
        { type = "text", text = "Hello from BetaMoon" },
        { type = "button", text = "Close", on = {
          activate = function(ctx) ctx.screen:close() end
        } }
      }
    }
  }

  local shortcuts = betamoon.input:map {
    key = "example:first_screen_keys", label = "First client screen",
    actions = { open = { default = "key.f6", label = "Open first screen" } }
  }
  shortcuts:on("open", function(event)
    if event.activated and not ui.screens:current() then
      -- The handle is queued until complete preparation succeeds at the next client boundary.
      local instance, reason = ui.screens:open("example:first_screen")
      if not instance then betamoon.log:debug("Screen refused: " .. reason) end
    end
  end)
end

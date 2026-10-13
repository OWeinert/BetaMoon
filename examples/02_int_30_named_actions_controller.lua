-- A named action can appear in Options > Controls > BetaMoon tab. The player
-- may change its binding without editing this script. Walk offers Hold/Toggle;
-- Walk status has a fixed Hold mode, so its Controls row has no mode button.
-- Press G in a world to walk forward; press again to stop in toggle mode.
-- Press H to report whether the walk controller is active.
-- While walking, the manual world-action stage denies attacks and held breaking.
-- Opening a GUI safely closes the temporary controller.

name = "Named Actions and Controller Example"
version = "1.0.0"
description = "Press G to try a player-configurable walk action. Open Options > Controls > BetaMoon " ..
    "tab to see Walk forward with a Hold/Toggle button and Walk status without one. " ..
    "Rebind either action or switch Walk forward's mode. While it is active, the controller " ..
    "submits forward movement each game tick and blocks attacks, item use and held block breaking. " ..
    "Press G again in Toggle mode, release G in Hold mode, or open any GUI to stop. Press H for status."

local actions
local controller
local active

function modInit()
  actions = betamoon.input:map {
    key = "example:walk_controls",
    label = "Walk Controls",
    description = "Try a mode-aware action with a player controller.",
    actions = {
      walk = {
        default = "key.g",
        label = "Walk forward",
        description = "While active, move forward and block world actions.",
        modes = { "hold", "toggle" },
        defaultMode = "toggle"
      },
      status = {
        default = "key.h",
        label = "Walk status",
        description = "Report whether the walk controller is active. Mode is fixed to Hold."
      }
    }
  }

  controller = betamoon.input.controllers:add {
    key = "example:walk_controller",
    stages = {
      movement = "manual",
      worldActions = "manual"
    }
  }

  -- activated/deactivated describe the logical action. In Toggle mode, held
  -- becomes false on release while active remains true until the next press.
  actions:on("walk", function(event)
    if event.activated then
      active = controller:activate { priority = 50 }
      betamoon.chat:send("Walk controller active")
    elseif event.deactivated then
      if active and active:isActive() then
        active:close()
      end
      active = nil
      betamoon.chat:send("Walk controller stopped")
    end
    return betamoon.callbackResults.pass
  end)

  -- Without a modes field, the action is Hold-only and has no mode button.
  actions:on("status", function(event)
    if event.phase == "pressed" then
      betamoon.chat:send(active and active:isActive() and "Walk is active" or "Walk is inactive")
    end
    return betamoon.callbackResults.pass
  end)

  -- submitIntent is valid for the next input cycle only. A missing submission
  -- means zero manual movement, so old movement cannot remain stuck.
  betamoon.events:on("game_tick", function()
    if active and active:isActive() then
      active:submitIntent {
        movement = { forward = 1, strafe = 0, jump = false, sneak = false }
      }
    else
      active = nil
    end
  end)
end

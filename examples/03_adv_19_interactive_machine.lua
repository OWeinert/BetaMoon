-- Craft and place the Control Console, then right-click it. This lesson keeps
-- control behavior in the container and layout/visuals in the GUI.

name = "Interactive Machine Controls Example"
version = "1.0.0"
description = "Adds a Control Console with the seven built-in interactive GUI elements and a custom dial area. " ..
  "Use it to compare persistent tile data, temporary container-session data, keyboard focus, and mouse dragging."

local BLOCK_ID = 235

function modInit()
  local tile = betamoon.tileEntities:add {
    name = "example:block/control_console",
    inventory = { name = "Control Console", slots = { sample = { index = 0 } } },
    data = {
      enabled = { type = "boolean", default = false, sync = true },
      speed = { type = "integer", default = 20, sync = true },
      mode = { type = "string", default = "idle" },
      label = { type = "string", default = "Console" }
    }
  }

  local container = betamoon.containers:add {
    name = "example:block/control_console",
    tileEntity = tile,
    slots = { { slot = "sample", name = "Sample", x = 151, y = 18 } },
    playerInventory = { x = 8, y = 116, includeHotbar = true },

    -- Session values disappear when the screen closes. Use them for tabs,
    -- searches, drafts, and other screen-local state.
    session = {
      dial = { type = "integer", default = 0 },
      message = { type = "string", default = "Ready", maxLength = 24 }
    },

    controls = {
      reset = {
        type = "action",
        onActivate = function(ctx)
          ctx.data:set("speed", 20)
          ctx.session:set("dial", 0)
          ctx.session:set("message", "Reset")
        end
      },
      enabled = {
        type = "toggle",
        bind = { data = "enabled" },
        onChange = function(ctx, value)
          ctx.session:set("message", value and "Enabled" or "Disabled")
        end
      },
      speed = {
        type = "number",
        bind = { data = "speed" },
        minimum = 0, maximum = 100, step = 5, pageStep = 25,
        beforeChange = function(ctx, proposed)
          -- Return DENY to leave the previous value untouched.
          if not ctx.data:get("enabled") and proposed > 50 then
            return betamoon.callbackResults.deny
          end
          return betamoon.callbackResults.pass
        end
      },
      mode = {
        type = "choice",
        bind = { data = "mode" },
        values = { "idle", "repeat", "redstone" }
      },
      label = {
        type = "text",
        bind = { data = "label" },
        maxLength = 16,
        onCommit = function(ctx, value)
          ctx.session:set("message", "Named " .. value)
        end
      },
      dial = {
        type = "custom",
        onInput = function(ctx, input)
          if input.phase == "press" or input.phase == "drag" then
            ctx.session:set("dial", math.floor(input.x / 63 * 100 + 0.5))
            ctx.session:set("message", "Dial " .. ctx.session:get("dial"))
            return betamoon.callbackResults.handled
          end
          return betamoon.callbackResults.pass
        end
      }
    }
  }

  local gui = betamoon.containerGuis:add {
    name = "example:block/control_console",
    container = container,
    layout = {
      preset = betamoon.mc.gui.backgrounds.container,
      height = 198,
      title = "Interactive Control Console",
      playerInventoryLabel = { text = "Inventory", x = 8, y = 104 }
    },
    background = { style = "minecraft", drawSlotFrames = true },
    elements = {
      { type = "button", control = "reset", x = 8, y = 18, width = 52, text = "Reset",
        tooltip = "Action control: no bound value" },
      { type = "icon_button", control = "reset", x = 62, y = 18, iconBuiltin = "confirm",
        tooltip = "Icon button: the same action in a compact 20x20 control" },
      { type = "checkbox", control = "enabled", x = 88, y = 22, width = 80, text = "Enabled" },
      { type = "toggle_button", control = "enabled", x = 8, y = 44, width = 70, text = "Power" },
      { type = "slider", control = "speed", x = 84, y = 44, width = 84,
        tooltip = "Drag, use arrows, or use Page Up/Down" },
      { type = "choice", control = "mode", x = 8, y = 70, width = 72,
        tooltip = "Use the left and right arrow regions or the keyboard arrows" },
      { type = "text_box", control = "label", x = 86, y = 70, width = 82,
        tooltip = "Enter commits; Escape reverts" },
      { type = "interactive", control = "dial", x = 8, y = 94, width = 64, height = 8,
        tooltip = "Custom control: drag across this area" },
      { type = "text", text = "Dial and messages are session-only", x = 78, y = 94,
        visibleWhen = { session = "dial", greaterOrEqual = 0 } }
    }
  }

  local block = betamoon.blocks:add {
    id = BLOCK_ID,
    key = "example:block/control_console",
    displayName = "Control Console",
    material = betamoon.mc.blockMaterials.iron,
    harvest = { pickaxe = 0 },
    hardness = 3,
    resistance = 8,
    stepSound = betamoon.mc.stepSounds.metal,
    textures = { top = 42, bottom = 42, sides = 43 },
    tileEntity = tile,
    container = container,
    gui = gui,
    drops = { { item = BLOCK_ID } }
  }

  betamoon.recipes:add {
    type = "shaped",
    output = betamoon.stack(block),
    pattern = { "IRI", "ICI", "III" },
    ingredients = {
      I = betamoon.items:getRequired(265),
      R = betamoon.items:getRequired(331),
      C = betamoon.blocks:getRequired(54)
    }
  }
end

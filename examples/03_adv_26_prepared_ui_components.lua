-- Press F8. Each ring has independent keyed state; clicking emits a typed event.
-- The caption exports its label and hosts caller-owned slot content.
-- The button template changes appearance while preserving standard control behavior.
name = "Prepared UI Components Example"
version = "1.0.0"
description = "Press F8 to try reusable keyed components, typed events, slots, exports and prepared templates."

function modInit()
  local ui = betamoon.client.ui
  ui.components:add {
    key = "example:ui_ring", size = { width = 80, height = 80 },
    props = { fraction = { type = "number", default = 0.75, min = 0, max = 1, animated = true } },
    state = { clicks = { type = "integer", default = 0, min = 0, max = 1000000 } },
    events = { changed = { count = { type = "integer", default = 0 } } },
    paint = function(view)
      return {
        { op = "ring", x = 40, y = 40, inner = 22, outer = 34, start = -90,
          sweep = { source = "property", property = "fraction", factor = 360 }, color = "#55AAFF" },
        { op = "text", x = 30, y = 36, text = tostring(view.state:get("clicks")), color = "#FFFFFF" },
        { op = "hitCircle", name = "dial", x = 40, y = 40, radius = 34 }
      }
    end,
    input = function(ctx, event)
      if event.type == "pointer_down" and event.button == 0 and event.region == "dial" then
        local count = ctx.state:get("clicks") + 1
        ctx.state:set("clicks", count)
        ctx:emit("changed", { count = count })
        return { consume = true, focus = true }
      end
      return false
    end,
    focusable = true
  }
  ui.components:add {
    key = "example:ui_caption", props = { text = { type = "string", default = "Caption" } },
    slots = { body = { min = 1, max = 1 } }, exports = { label = "label" },
    build = function(props)
      return { type = "column", gap = 4, children = {
        { type = "text", id = "label", text = props.text }, { slot = "body" }
      } }
    end
  }
  ui.themes:add {
    key = "example:prepared_skin", base = "minimal",
    templates = { button = { parts = { "content", "focus" }, paint = function(view)
      local function commands(color)
        return {
          { op = "rect", width = { source = "width" }, height = { source = "height" }, color = color },
          { op = "text", x = 6, y = 6, text = view.props.text, color = "#FFFFFF" }
        }
      end
      return { default = commands("#203040"), hovered = commands("#305070"), focused = commands("#486050") }
    end } }
  }
  ui.screens:add {
    key = "example:prepared_ui", theme = "example:prepared_skin",
    state = { message = { type = "string", default = "Click either ring" } },
    root = { type = "column", width = 240, gap = 6, children = {
      { component = "example:ui_caption", id = "caption", props = { text = "Reusable UI" }, slots = { body = {
        { type = "text", bind = function(view) return { text = view.state:get("message") } end }
      } } },
      { type = "row", children = {
        { component = "example:ui_ring", key = "left", props = { fraction = 0.75 }, on = {
          changed = function(ctx, event) ctx.state:set("message", "Left: " .. event.payload.count) end
        } },
        { component = "example:ui_ring", key = "right", props = { fraction = 0.4 }, on = {
          changed = function(ctx, event) ctx.state:set("message", "Right: " .. event.payload.count) end
        } }
      } },
      { type = "button", text = "Change exported caption", on = { activate = function(ctx)
        ctx.gui:element("caption.label"):set { text = "Explicit export reached" }
      end } },
      { type = "button", text = "Close", on = { activate = function(ctx) ctx.screen:close() end } }
    } }
  }
  local shortcuts = betamoon.input:map {
    key = "example:prepared_ui_keys", label = "Prepared UI components",
    actions = { open = { default = "key.f8", label = "Open prepared UI" } }
  }
  shortcuts:on("open", function(event)
    if event.activated and not ui.screens:current() then ui.screens:open("example:prepared_ui") end
  end)
end

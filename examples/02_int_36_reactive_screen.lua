-- Press F7. Edit a controlled textbox, then open an independent confirmation dialog.
-- Apply is a declared client action: it publishes local UI data without gameplay writes.
name = "Reactive Client Form Example"
version = "1.0.0"
description = "Press F7 to try a reactive form, detached data, queued action tickets and a modal dialog."

function modInit()
  local ui = betamoon.client.ui
  ui.screens:add {
    key = "example:form_confirmation",
    state = { accepted = { type = "boolean", default = false } },
    root = { type = "column", width = 190, gap = 6, children = {
      { type = "text", text = "This dialog has its own state." },
      { type = "checkbox", text = "I checked the form", on = {
        change = function(ctx, event) ctx.state:set("accepted", event.value) end
      } },
      { type = "button", text = "Return to form", on = {
        activate = function(ctx) ctx.screen:close() end
      } }
    } }
  }
  ui.screens:add {
    key = "example:reactive_form", theme = "minimal",
    state = { title = { type = "string", default = "My game" } },
    data = { message = { type = "string", default = "Edit the name, then Apply." } },
    actions = {
      apply = { scope = "client", payload = {}, handler = function(ctx)
        local title = ctx.state:get("title")
        ctx.data:set("message", "Applied: " .. title)
        return { title = title }
      end }
    },
    root = { type = "column", width = 240, gap = 6, children = {
      { type = "textbox", id = "title", maximumLength = 40,
        bind = function(view) return { text = view.state:get("title") } end,
        on = { change = function(ctx, event) ctx.state:set("title", event.value) end } },
      { type = "text", bind = function(view) return { text = view.data:get("message") } end },
      { type = "button", text = "Apply", on = { activate = function(ctx)
        local ticket, reason = ctx.screen:request("apply", {})
        if not ticket then ctx.data:set("message", "Request refused: " .. reason) end
      end } },
      { type = "button", text = "Confirmation dialog", on = { activate = function(ctx)
        ctx.screen:dialog("example:form_confirmation")
      end } },
      { type = "button", text = "Close", on = { activate = function(ctx) ctx.screen:close() end } }
    } }
  }
  local shortcuts = betamoon.input:map {
    key = "example:reactive_form_keys", label = "Reactive form",
    actions = { open = { default = "key.f7", label = "Open reactive form" } }
  }
  shortcuts:on("open", function(event)
    if event.activated and not ui.screens:current() then ui.screens:open("example:reactive_form") end
  end)
end

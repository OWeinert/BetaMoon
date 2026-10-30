-- Press F9. Requires OpenGL 2 shaders and framebuffer support.
-- rawDraw deliberately runs Lua each frame; use prepared paint for ordinary UI.
-- Resources are prepared before opening and disposed with the owned screen.
-- Render callbacks have graphics authority only: they cannot mutate UI/gameplay.
name = "Raw UI Graphics Example"
version = "1.0.0"
description = "Press F9 for an explicit animated raw graphics pass using a prepared shader, VBO and target."

function modInit()
  local ui = betamoon.client.ui
  ui.components:add {
    key = "example:raw_ui_panel", size = { width = 200, height = 60 },
    rawDraw = function(frame)
      local pulse = 0.5 + 0.25 * math.sin(frame.time)
      frame.gl:target("scratch")
      frame.gl:clear(0xFF102030)
      frame.gl:target(nil)
      frame.gl:enable("texture", false)
      frame.gl:program("tint")
      frame.gl:uniform("color", 0.2, pulse, 0.8, 1)
      frame.gl:buffer("quad")
      frame.gl:program(nil)
    end
  }
  ui.screens:add {
    key = "example:raw_ui",
    resources = {
      buffers = { quad = { mode = "quads", vertices = { 0,0, 200,0, 200,60, 0,60 } } },
      programs = { tint = {
        vertex = "void main(){gl_Position=gl_ModelViewProjectionMatrix*gl_Vertex;}",
        fragment = "uniform vec4 color;void main(){gl_FragColor=color;}",
        uniforms = { color = 4 }
      } },
      targets = { scratch = { width = 32, height = 32 } }
    },
    root = { type = "column", width = 220, gap = 8, children = {
      { type = "text", text = "Explicit raw rendering" },
      { component = "example:raw_ui_panel" },
      { type = "button", text = "Close", on = { activate = function(ctx) ctx.screen:close() end } }
    } }
  }
  local shortcuts = betamoon.input:map {
    key = "example:raw_ui_keys", label = "Raw UI graphics",
    actions = { open = { default = "key.f9", label = "Open raw UI graphics" } }
  }
  shortcuts:on("open", function(event)
    if event.activated and not ui.screens:current() then ui.screens:open("example:raw_ui") end
  end)
end

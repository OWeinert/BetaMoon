-- A display override adds client-side effects to an existing block without replacing it.
-- Place and light a vanilla furnace, then watch for an extra flame above it.
-- ctx:base() preserves the furnace's normal smoke and flame effects.
-- The BetaMoon Java agent must be active for callbacks on vanilla blocks.

name = "Display Override Example"
version = "1.0.0"
description = "Adds an extra flame effect to the vanilla lit furnace through a display-tick override. The " ..
    "callback preserves Minecraft's original furnace effects by calling the base implementation " ..
    "before occasionally spawning another flame above the block.\n\n" ..
    "Enable BetaMoon's instrumentation agent, place a normal furnace, and start smelting with " ..
    "suitable input and fuel. Watch its top while it is burning; the extra particle is random and " ..
    "does not appear on every display tick. This example adds no block or recipe and does not " ..
    "change smelting speed. Compare the extra flame's position and chance with the override " ..
    "callback."

function modInit()
  local litFurnace = betamoon.blocks:getRequired(62)

  -- Like property overrides, this callback is owned by the script generation
  -- and disappears automatically when that generation unloads.
  litFurnace:override {
    onDisplayTick = {
      action = function(ctx)
        -- A display override replaces the current callback layer. Call base once
        -- when the behavior underneath this override should still run.
        ctx:base()

        -- Display contexts are read-only. Their world access only exposes
        -- effects and inspection, so this callback cannot alter furnace state.
        if ctx:random() < 0.25 then
          -- particles supplies the canonical names understood by Minecraft's particle renderer.
          ctx.world:spawnParticle(betamoon.mc.particles.flame, {
            x = ctx.x + 0.5,
            y = ctx.y + 1.05,
            z = ctx.z + 0.5,
            velocityY = 0.01
          })
        end
      end
    }
  }
end

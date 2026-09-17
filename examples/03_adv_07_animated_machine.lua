-- Copy animated_machine/ beside this script in lua_scripts.
-- Its idle and running appearances both use dynamic rendering so visuals change
-- without replacing the block. The sound is local feedback for the activating player.

name = "Animated Machine Example"
version = "1.0.0"
description = "Adds a decorative animated machine (234). Copy animated_machine/ beside " ..
    "this script and right-click the block to toggle its glow and spinning rotor. " ..
    "A registered sound event provides local activation feedback. The machine " ..
    "does not process items or generate power."

function modInit()
  local model = betamoon.assets.models:add {
    key = "example:block/machine",
    path = "animated_machine/machine.json"
  }
  local glowModel = betamoon.assets.models:add {
    key = "example:block/machine_glow",
    path = "animated_machine/machine_glow.json"
  }
  local animation = betamoon.assets.animations:add {
    key = "example:block/machine",
    path = "animated_machine/machine.animation.json"
  }
  local casing = betamoon.assets.textures:add {
    key = "example:block/machine_casing",
    path = "animated_machine/machine_casing.png"
  }
  local rotor = betamoon.assets.textures:add {
    key = "example:block/machine_rotor",
    path = "animated_machine/machine_rotor.png"
  }
  local glow = betamoon.assets.textures:add {
    key = "example:block/machine_glow",
    path = "animated_machine/machine_glow.png"
  }
  local click = betamoon.assets.sounds:add {
    key = "example:block/machine_click",
    path = "animated_machine/machine_click.wav"
  }
  local toggle = betamoon.soundEvents:add {
    key = "example:block/machine_toggle", sound = click,
    volume = 0.6, pitch = { min = 0.95, max = 1.05 }
  }

  local materials = {
    casing = { texture = casing },
    rotor = { texture = rotor }
  }
  local idle = { model = model, materials = materials, mode = "dynamic" }
  local running = {
    model = model, materials = materials, mode = "dynamic",
    animation = { asset = animation, clip = "animation.machine.spin" },
    layers = {
      {
        model = glowModel, texture = glow,
        material = { blend = "additive", lighting = "unlit", opacity = 0.7 }
      }
    }
  }

  betamoon.blocks:add {
    id = 234, key = "lesson_animated_machine", displayName = "Animated Machine",
    material = betamoon.mc.blockMaterials.rock, texture = 1,
    opaque = false, normalCube = false, lightOpacity = 0,
    state = { running = { type = "boolean", default = false } },
    appearance = idle,
    render = { variants = { [1] = { appearance = running } } },
    onActivate = {
      action = function(ctx)
        ctx.state:set("running", not ctx.state:get("running"))
        betamoon.audio:play(toggle)
        return "handled"
      end
    }
  }
end

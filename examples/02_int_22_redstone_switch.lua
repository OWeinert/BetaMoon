-- Craft stone + redstone, place the switch, then put wire beside its marked output face.
-- Right-click switches the output on/off. Placement selects a horizontal orientation.
-- front is relative to that saved orientation; down always means the world-facing bottom.
-- The switch stores its small amount of state in metadata, so it needs no tile entity.
-- Keep the BetaMoon Java agent active: it enables weak output from this solid custom block.

name = "Redstone Switch Example"
version = "1.0.0"
description = "Adds Directional Switch, a manually toggled redstone source whose front follows its placement " ..
    "orientation. Combine one stone block and one redstone dust in any arrangement to craft it.\n\n" ..
    "Enable BetaMoon's instrumentation agent for custom solid-block power behavior. Place the " ..
    "switch, identify its furnace-textured front, and connect redstone wire on that side. " ..
    "Right-click to turn the output on or off. The front texture and tint change with the powered " ..
    "state. It supplies weak power at the front and strong power below.\n\n" ..
    "Place another switch while facing a different direction to compare the rotated output. The " ..
    "source keeps facing and powered values in block state and uses them for both rendering and " ..
    "redstone behavior."

function modInit()
  local switch = betamoon.blocks:add {
    id = 212,
    key = "example:block/example_redstone_switch",
    displayName = "Directional Switch",
    -- blockMaterials supplies the canonical native material name.
    material = betamoon.mc.blockMaterials.rock,
    harvest = { pickaxe = 0 },
    hardness = 1,
    texture = 1,
    -- Alphabetical packing: facing occupies bits 0..1, powered occupies bit 2.
    state = {
      facing = { type = "enum", values = { "north", "east", "south", "west" } },
      powered = { type = "boolean", default = false }
    },
    placement = { facing = "horizontal", facingFrom = "player" },
    -- The output reads the named boolean whenever Minecraft asks for power.
    -- weakPower drives the selected output side; strongPower also lets the
    -- block below conduct power. connections controls wire connection faces,
    -- not whether the stored powered flag is true. These outputs are digital.
    redstone = {
      weakPower = { state = "powered", sides = { "front" } },
      -- blockFaces contains absolute faces; front remains a relative BetaMoon direction.
      strongPower = { state = "powered", sides = { betamoon.mc.blockFaces.down } },
      connections = { "front" }
    },
    -- Facing alone uses values 0..3; powered adds 4, giving 4..7 when on.
    -- Every orientation needs both visual variants. The per-face texture keys
    -- are world directions, so each variant marks a different face.
    render = {
      variants = {
        [0] = { textures = { north = 61 } },
        [1] = { textures = { east = 61 } },
        [2] = { textures = { south = 61 } },
        [3] = { textures = { west = 61 } },
        [4] = { textures = { north = 62 }, color = 0xFFB070 },
        [5] = { textures = { east = 62 }, color = 0xFFB070 },
        [6] = { textures = { south = 62 }, color = 0xFFB070 },
        [7] = { textures = { west = 62 }, color = 0xFFB070 }
      }
    },
    onActivate = {
      action = function(ctx)
        -- State writes notify neighbors; no tile entity or per-tick Lua is needed.
        ctx.state:set("powered", not ctx.state:get("powered"))
        -- sounds.random supplies the canonical vanilla click identifier.
        ctx.world:playSound(betamoon.mc.sounds.random.click, 0.5, 1)
        return betamoon.callbackResults.handled
      end
    }
  }

  betamoon.recipes:add {
    type = "shapeless", output = betamoon.stack(switch),
    ingredients = { betamoon.blocks:getRequired(1), betamoon.items:getRequired(331) }
  }
end

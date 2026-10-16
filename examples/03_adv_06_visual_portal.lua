-- Craft obsidian + redstone for Visual Portal Frame blocks (243).
-- Build a 4-wide, 5-high frame entirely from these blocks; corners are optional.
-- Leave a 2x3 empty opening and use flint and steel on an inner frame face.
-- No particular frame block, placement order, or player-facing direction is required.

name = "Visual Multiblock Portal Example"
version = "1.0.0"
description = "Adds Visual Portal Frame (243). Craft obsidian and redstone together. Build a " ..
    "four-wide, five-high frame entirely from these blocks, with a two-wide, three-high empty " ..
    "opening. Corners are optional. Use flint and steel against an inner frame face.\n\n" ..
    "The opening fills with animated purple panels and portal particles. Walk through freely: " ..
    "this demonstration never teleports. Break a required frame block or a panel to extinguish " ..
    "it, repair the opening, and light it again. Player replacements are preserved. The Java " ..
    "agent is required; restart Minecraft after editing this example."

function modInit()
  local bm = betamoon
  local frame = bm.blocks:add {
    id = 243, key = "example:block/visual_portal_frame", displayName = "Visual Portal Frame",
    material = bm.mc.blockMaterials.rock, texture = 37, hardness = 3.5, resistance = 2000,
    harvest = { pickaxe = 0 }, stepSound = bm.mc.stepSounds.stone, drops = { { item = 243 } }
  }
  local bounds = { min = { 0, 0, 0.4375 }, max = { 1, 1, 0.5625 } }
  local surface = bm.blocks:add {
    id = 244, key = "example:block/visual_portal_surface", displayName = "Visual Portal Surface",
    material = bm.mc.blockMaterials.portal,
    -- Terrain cell 14 is Minecraft's animated portal texture.
    texture = 14, hardness = 0, light = 11, lightOpacity = 0,
    opaque = false, normalCube = false, collision = { boxes = {} },
    selection = bounds, render = { preset = "cuboid", bounds = bounds, pass = 1 },
    state = { facing = { type = "enum", values = { "north", "east", "south", "west" } } },
    placement = { facing = "horizontal", facingFrom = "player" },
    drops = {},
    onDisplayTick = { action = function(ctx)
      ctx.world:spawnParticle(bm.mc.particles.portal, {
        x = ctx.x + math.random(), y = ctx.y + math.random(), z = ctx.z + math.random(),
        velocityX = (math.random() - 0.5) * 0.1,
        velocityY = (math.random() - 0.5) * 0.1,
        velocityZ = (math.random() - 0.5) * 0.1
      })
    end }
  }
  bm.multiblocks:add {
    key = "example:multiblock/visual_portal", exclusive = true,
    -- @ is only a coordinate reference: it requires the same block as F.
    layers = { { "?@F?" }, { "FIIF" }, { "FIIF" }, { "FIIF" }, { "?FF?" } },
    palette = { ["@"] = frame, F = frame, I = { air = true } },
    captures = { frame = "@F", interior = "I" },
    discovery = { cells = "frame" },
    activation = {
      mode = "item", item = bm.items:getRequired(259),
      target = { cells = "interior", position = "adjacent" },
      cost = { damage = 1 },
      fill = { cells = "interior", block = surface, facingState = "facing" }
    },
    onActivated = function(ctx)
      ctx.world:playSound("fire.ignite")
    end
  }
  bm.recipes:add {
    type = "shapeless", output = bm.stack(frame),
    ingredients = { bm.blocks:getRequired(49), bm.items:getRequired(331) }
  }
end

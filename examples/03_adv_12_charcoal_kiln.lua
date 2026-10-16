-- Craft a furnace surrounded by bricks. Build the exact 3x3x3 shell below,
-- from bottom to top; @ is the front-facing Charcoal Kiln controller.
-- Bottom: BBB / B.B / BBB   Middle: B@B / B.B / BBB   Top: BBB / BBB / BBB
-- Leave both center cells empty. Add any vanilla log to the two-slot GUI.

-- Change this setting to adjust processing time (20 ticks per second).
local duration = 400

name = "Charcoal Kiln Multiblock Example"
version = "1.0.0"
description = "Adds Charcoal Kiln (246). Craft a furnace surrounded by eight brick blocks. " ..
    "Build a 3x3x3 brick shell with air in the centers of its top and middle layers. Replace " ..
    "the front-center brick of the middle layer with the kiln, facing outwards.\n\n" ..
    "Right-click the controller and insert oak, spruce, or birch logs. A complete kiln turns " ..
    "one log into one charcoal in " .. duration .. " ticks ( ".. (duration / 20) .." seconds), without fuel. There is one input and one output. " ..
    "The flame at the furnace arrow's position shows progress. Hover the warning triangle to " ..
    "learn why an incomplete or unverified structure cannot process. Full output pauses work; " ..
    "changing input or losing the structure resets progress. Inventory and progress are saved. " ..
    "The Java agent is required; restart Minecraft after editing this example."

function modInit()
  local bm = betamoon
  local kilnType = bm.recipeTypes:add {
    name = "example:recipe_type/charcoal_kiln", displayName = "Charcoal Kiln",
    ingredients = { log = { type = "item" } },
    outputs = { charcoal = { type = "item" } }
  }
  bm.recipes:add {
    key = "example:recipe/kiln_charcoal", type = kilnType,
    ingredients = { log = { item = bm.blocks:getRequired(17), damage = "any" } },
    output = bm.stack(bm.items:getRequired(263), 1, 1)
  }

  local tile = bm.tileEntities:add {
    name = "example:block/charcoal_kiln",
    inventory = { name = "Charcoal Kiln", slots = { input = { index = 0 }, output = { index = 1 } } },
    data = {
      progress = { type = "integer", default = 0, sync = true },
      duration = { type = "integer", default = duration, sync = true },
      formed = { type = "boolean", default = false, sync = true },
      blocked = { type = "boolean", default = false, sync = true },
      signature = { type = "string", default = "" }
    },
    onTick = { mode = "continuous", action = function(ctx)
      local data = ctx.entity.data
      -- Never use the GUI's saved/synced flag as permission to process. The
      -- scoped multiblock gate is false immediately after a relevant edit.
      local formed = ctx.multiblock ~= nil and ctx.multiblock:isFormed()
      data:set("formed", formed)
      data:set("duration", duration)
      if not formed then
        ctx.state:set("lit", false)
        data:set("progress", 0)
        data:set("signature", "")
        data:set("blocked", false)
        return
      end

      local match = ctx.recipes:match {
        type = kilnType,
        slots = { ingredients = { log = "input" }, outputs = { charcoal = "output" } }
      }
      local signature = match and match.signature or ""
      if signature ~= data:get("signature") then
        data:set("progress", 0)
        data:set("signature", signature)
      end
      local canApply = match ~= nil and match:canApply()
      data:set("blocked", match ~= nil and not canApply)
      ctx.state:set("lit", canApply)
      if not canApply or match == nil then return end

      local progress = data:get("progress") + 1
      if progress >= duration then
        -- Revalidate input and output and exchange them atomically. No fuel
        -- slot, fuel timer, or separate manual item removal is involved.
        if not match:apply() then
          data:set("blocked", true)
          ctx.state:set("lit", false)
          return
        end
        progress = 0
      end
      data:set("progress", progress)
    end }
  }
  local container = bm.containers:add {
    name = "example:block/charcoal_kiln", tileEntity = tile,
    slots = {
      { name = "Logs", slot = "input", x = 50, y = 35 },
      { name = "Charcoal", slot = "output", x = 110, y = 35, outputOnly = true }
    },
    playerInventory = { x = 8, y = 84, includeHotbar = true }
  }
  local gui = bm.containerGuis:add {
    name = "example:block/charcoal_kiln", container = container,
    -- Draw only these two slots: the furnace PNG would also paint a fuel slot
    -- and a static arrow which this fuel-free machine does not have.
    layout = { preset = bm.mc.gui.backgrounds.container, width = 176, height = 166,
      title = "Charcoal Kiln", playerInventoryLabel = { text = "Inventory", x = 8, y = 72 } },
    background = { style = "minecraft", drawSlotFrames = true },
    elements = {
      { type = "image", builtin = bm.mc.gui.sprites.furnace_flame_background, x = 80, y = 36 },
      { type = "progress", value = "progress", maximum = "duration", x = 80, y = 36,
        builtin = bm.mc.gui.sprites.furnace_flame, direction = "bottom_to_top", hideWhenEmpty = false },
      { type = "text", text = "Output full", x = 52, y = 59, color = "dark_red",
        visibleWhen = { field = "blocked", equals = true } },
      { type = "group", x = 148, y = 36, visibleWhen = { field = "formed", equals = false }, elements = {
        -- A tiny pixel warning triangle needs no separate texture file.
        { type = "rectangle", x = 5, y = 0, width = 2, height = 2, color = 0xFFFFC340 },
        { type = "rectangle", x = 4, y = 2, width = 4, height = 2, color = 0xFFFFC340 },
        { type = "rectangle", x = 3, y = 4, width = 6, height = 2, color = 0xFFFFC340 },
        { type = "rectangle", x = 2, y = 6, width = 8, height = 2, color = 0xFFFFC340 },
        { type = "rectangle", x = 1, y = 8, width = 10, height = 2, color = 0xFFFFC340 },
        { type = "rectangle", x = 0, y = 10, width = 12, height = 2, color = 0xFFFFC340 },
        { type = "text", text = "!", x = 5, y = 4, color = 0xFF402A10 },
        { type = "tooltip", width = 12, height = 12, tooltip = {
          "Multiblock incomplete or checking", "Build the 3x3x3 brick shell.",
          "Leave the top and middle centers empty.", "Place the controller at the front-middle position."
        } }
      } }
    }
  }
  local block = bm.blocks:add {
    id = 246, key = "example:block/charcoal_kiln", displayName = "Charcoal Kiln",
    material = bm.mc.blockMaterials.rock, hardness = 3.5, resistance = 5,
    harvest = { pickaxe = 0 }, stepSound = bm.mc.stepSounds.stone,
    state = {
      facing = { type = "enum", values = { "north", "east", "south", "west" } },
      lit = { type = "boolean", default = false }
    },
    placement = { facing = "horizontal", facingFrom = "player" },
    textures = { top = 7, bottom = 7, sides = 7 },
    render = { variants = {
      [0] = { textures = { north = 44 } }, [1] = { textures = { east = 44 } },
      [2] = { textures = { south = 44 } }, [3] = { textures = { west = 44 } },
      [4] = { textures = { north = 61 } }, [5] = { textures = { east = 61 } },
      [6] = { textures = { south = 61 } }, [7] = { textures = { west = 61 } }
    } },
    tileEntity = tile, container = container, gui = gui, drops = { { item = 246 } }
  }

  bm.multiblocks:add {
    key = "example:multiblock/charcoal_kiln", controller = block,
    layers = { { "BBB", "BBB", "BBB" }, { "B@B", "B.B", "BBB" }, { "BBB", "B.B", "BBB" } },
    palette = { B = bm.blocks:getRequired(45) }
  }
  bm.recipes:add {
    type = "shaped", pattern = { "BBB", "BFB", "BBB" },
    ingredients = { B = bm.blocks:getRequired(45), F = bm.blocks:getRequired(61) }, output = bm.stack(block)
  }
end

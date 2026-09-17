-- Load 07a and 07b. Craft furnace + redstone + iron, place the processor, and open it.
-- Dirt processes below 100 heat while unpowered. For sand, add fuel and hold a redstone
-- signal until heat reaches 400. Removing power invalidates that hot match immediately.
-- Structural content cannot hot reload, so restart Minecraft after machine edits.

name = "Contextual Processor Example"
version = "1.0.0"
description = "Adds Contextual Processor, a machine whose recipes depend on both heat and redstone power. " ..
    "Load advanced examples 07a and 07b with it. Combine a vanilla furnace, one redstone dust, and " ..
    "one iron ingot in any arrangement to craft it, then place and right-click it.\n\n" ..
    "First leave it unpowered and cool. Put dirt in the left input slot; at heat 100 or below, " ..
    "each dirt makes four clay balls in about three seconds. Results appear in the right output " ..
    "slot.\n\n" ..
    "For the hot recipe, replace the input with sand, put normal furnace fuel in the lower fuel " ..
    "slot, and supply a steady redstone signal. It starts burning fuel when powered with input " ..
    "present. Watch Heat reach at least 400; each sand then makes two glass in about five seconds. " ..
    "Removing power stops that recipe, but already-lit fuel keeps burning. To return to clay, let " ..
    "fuel burn out and heat fall to 100 or below.\n\n" ..
    "The flame and arrow show fuel and processing progress. A blocked output pauses valid work; " ..
    "losing the required conditions resets progress. Compare the displayed values with the context " ..
    "checks in the tick callback. Inventory and data persist with the placed machine; restart " ..
    "after structural edits."
dependencies = { "Contextual Recipe Type Example", "Contextual Recipes Example" }

function modInit()
  local recipeType = betamoon.modules:import("contextual_recipe_types").thermalProcessing
  local recipeSlots = {
    ingredients = { input = "input" },
    outputs = { result = "output" }
  }

  local tileEntity = betamoon.tileEntities:add {
    name = "contextual_processor",
    inventory = {
      name = "Contextual Processor",
      slots = {
        input = { index = 0 },
        fuel = { index = 1 },
        output = { index = 2 }
      }
    },
    data = {
      heat = { type = "integer", default = 0, sync = true },
      burnTime = { type = "integer", default = 0, sync = true },
      totalBurnTime = { type = "integer", default = 1, sync = true },
      progress = { type = "integer", default = 0, sync = true },
      duration = { type = "integer", default = 1, sync = true },
      powered = { type = "boolean", default = false, sync = true },
      blocked = { type = "boolean", default = false, sync = true },
      signature = { type = "string", default = "" }
    },
    onTick = {
      mode = "continuous",
      action = function(ctx)
        local data = ctx.entity.data
        local inventory = ctx.entity.inventory
        local powered = ctx.world:isPowered()
        local burnTime = data:get("burnTime")

        if burnTime > 0 then
          burnTime = burnTime - 1
        elseif powered and inventory:get("input") then
          burnTime = inventory:consumeFuel("fuel")
          if burnTime > 0 then
            data:set("totalBurnTime", burnTime)
          end
        end
        data:set("burnTime", burnTime)

        local heat = data:get("heat")
        if burnTime > 0 then
          heat = math.min(1000, heat + 2)
        else
          heat = math.max(0, heat - 1)
        end
        data:set("heat", heat)
        data:set("powered", powered)
        ctx.state:set("lit", burnTime > 0)

        -- This detached context is validated against the type schema before matching.
        local recipeContext = { heat = heat, powered = powered }
        local match = ctx.recipes:match {
          type = recipeType,
          slots = recipeSlots,
          context = recipeContext
        }

        if match == nil then
          data:set("progress", 0)
          data:set("duration", 1)
          data:set("blocked", false)
          data:set("signature", "")
          return
        end

        if data:get("signature") ~= match.signature then
          data:set("progress", 0)
          data:set("signature", match.signature)
        end
        data:set("duration", match.data.duration)

        -- Conditions can change between discovery and commit. Passing fresh
        -- context to canApply/apply makes the final inventory mutation atomic.
        local canApply = match:canApply { context = recipeContext }
        data:set("blocked", not canApply)
        if not canApply then
          return
        end

        local progress = data:get("progress") + 1
        if progress >= match.data.duration then
          local freshContext = {
            heat = data:get("heat"),
            powered = ctx.world:isPowered()
          }
          local applied = match:apply { context = freshContext }
          data:set("blocked", not applied)
          if not applied then
            return
          end
          progress = 0
        end
        data:set("progress", progress)
      end
    }
  }

  local container = betamoon.containers:add {
    name = "contextual_processor",
    tileEntity = tileEntity,
    slots = {
      { name = "Input", slot = "input", x = 38, y = 35 },
      { name = "Fuel", slot = "fuel", x = 62, y = 53 },
      { name = "Output", slot = "output", x = 116, y = 35, outputOnly = true }
    },
    playerInventory = { x = 8, y = 92, includeHotbar = true }
  }

  local gui = betamoon.containerGuis:add {
    name = "contextual_processor",
    container = container,
    layout = {
      -- gui.backgrounds and gui.sprites identify the built-in container assets.
      preset = betamoon.mc.gui.backgrounds.container,
      height = 176,
      title = "Contextual Processor",
      playerInventoryLabel = { text = "Inventory", x = 8, y = 80 }
    },
    background = { style = "minecraft", drawSlotFrames = true },
    elements = {
      { type = "progress", value = "burnTime", maximum = "totalBurnTime",
        direction = "bottom_to_top", x = 62, y = 34,
        builtin = betamoon.mc.gui.sprites.furnace_flame, hideWhenEmpty = true },
      { type = "progress", value = "progress", maximum = "duration",
        x = 82, y = 35, builtin = betamoon.mc.gui.sprites.furnace_arrow },
      { type = "text", value = "heat", format = "Heat: %d", x = 8, y = 68 },
      { type = "text", text = "Powered", x = 64, y = 68, color = "dark_green",
        visibleWhen = { field = "powered", equals = true } },
      { type = "text", text = "Output blocked", x = 112, y = 68, color = "dark_red",
        visibleWhen = { field = "blocked", equals = true } }
    }
  }

  local processor = betamoon.blocks:add {
    id = 226,
    key = "contextual_processor",
    displayName = "Contextual Processor",
    -- blockMaterials and stepSounds keep its native block identifiers canonical.
    material = betamoon.mc.blockMaterials.rock,
    hardness = 3.5,
    resistance = 5,
    stepSound = betamoon.mc.stepSounds.stone,
    textures = { top = 62, bottom = 62, sides = 45 },
    state = {
      facing = { type = "enum", values = { "north", "east", "south", "west" } },
      lit = { type = "boolean", default = false }
    },
    placement = { facing = "horizontal", facingFrom = "player" },
    render = {
      variants = {
        [0] = { textures = { north = 44 } },
        [1] = { textures = { east = 44 } },
        [2] = { textures = { south = 44 } },
        [3] = { textures = { west = 44 } },
        [4] = { textures = { north = 61 } },
        [5] = { textures = { east = 61 } },
        [6] = { textures = { south = 61 } },
        [7] = { textures = { west = 61 } }
      }
    },
    tileEntity = tileEntity,
    container = container,
    gui = gui,
    piston = { reaction = "block" },
    redstone = { connections = { "front", "back", "left", "right" } },
    drops = { { item = 226, damage = 0 } }
  }

  betamoon.recipes:add {
    type = "shapeless",
    output = betamoon.stack(processor),
    ingredients = {
      betamoon.blocks:getRequired(61),
      betamoon.items:getRequired(331),
      betamoon.items:getRequired(265)
    }
  }
end

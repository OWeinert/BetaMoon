-- Load 09a with this file. Craft pistons + iron + redstone for a focused press.
-- Put four matching items in one material slot. Without power the matcher prefers
-- slot 1; with power it prefers slot 5. If that slot cannot satisfy the recipe,
-- the largest eligible stack wins. Other occupied material slots remain untouched.
-- Structural machine changes require a Minecraft restart.

name = "Matcher Cookbook Press Example"
version = "1.0.0"
description = "Adds Focused Press, a five-input machine that demonstrates choosing which stack a recipe " ..
    "consumes. Load advanced example 09a as well. Craft it with pistons in the four corners, " ..
    "redstone dust in the center, and iron ingots in the four remaining cells.\n\n" ..
    "Place it and right-click. The five Material slots run left to right. Put at least four " ..
    "cobblestone in one slot and leave Die empty to make one stone in about three seconds. For " ..
    "glass, put at least four sand in one material slot and a stick in Die; a batch makes two " ..
    "glass in about five seconds and keeps the stick.\n\n" ..
    "A batch must come from one stack: two plus two items in separate slots do not qualify. With " ..
    "power off, slot 1 is preferred; supply redstone at the north connection to prefer slot 5. If " ..
    "the preferred slot cannot supply a batch, the largest eligible stack is chosen. Try stacks of " ..
    "four and eight in different slots and watch which shrinks. Other material slots remain " ..
    "untouched.\n\n" ..
    "No fuel is needed. Redstone selects a preference rather than enabling processing. Watch the " ..
    "preferred-slot label, progress arrow, and output-blocked message. Remove output to resume " ..
    "blocked work. Inventory and data persist with the placed block; restart after structural " ..
    "edits."
dependencies = { "Matcher Cookbook Type Example" }

function modInit()
  local recipeType = betamoon.modules:import("matcher_cookbook").recipeType

  betamoon.recipes:add {
    key = "example:focused_stone_pressing",
    type = recipeType,
    ingredients = {
      materials = { { item = betamoon.blocks:getRequired(4), count = 4 } }
    },
    output = betamoon.blocks:getRequired(1),
    data = { duration = 60 }
  }

  betamoon.recipes:add {
    key = "example:focused_glass_pressing",
    type = recipeType,
    ingredients = {
      materials = { { item = betamoon.blocks:getRequired(12), count = 4 } },
      die = betamoon.items:getRequired(280)
    },
    output = betamoon.stack(betamoon.blocks:getRequired(20), 2),
    data = { duration = 100 }
  }

  local materialSlots = betamoon.recipeBindings:pool { prefix = "material_", count = 5 }
  local recipeSlots = {
    ingredients = { materials = materialSlots, die = "die" },
    outputs = { result = "result" }
  }

  local tileEntity = betamoon.tileEntities:add {
    name = "focused_press",
    inventory = {
      name = "Focused Press",
      slots = {
        material_1 = { index = 0 },
        material_2 = { index = 1 },
        material_3 = { index = 2 },
        material_4 = { index = 3 },
        material_5 = { index = 4 },
        die = { index = 5 },
        result = { index = 6 }
      }
    },
    data = {
      preferredSlot = { type = "integer", default = 1, sync = true },
      progress = { type = "integer", default = 0, sync = true },
      duration = { type = "integer", default = 1, sync = true },
      blocked = { type = "boolean", default = false, sync = true },
      signature = { type = "string", default = "" }
    },
    onTick = {
      mode = "continuous",
      action = function(ctx)
        local data = ctx.entity.data
        local preferredSlot = ctx.world:isPowered() and 5 or 1
        data:set("preferredSlot", preferredSlot)

        local recipeContext = { preferredSlot = preferredSlot }
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

        local canApply = match:canApply { context = recipeContext }
        data:set("blocked", not canApply)
        if not canApply then
          return
        end

        local progress = data:get("progress") + 1
        if progress >= match.data.duration then
          local freshContext = {
            preferredSlot = ctx.world:isPowered() and 5 or 1
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
    name = "focused_press",
    tileEntity = tileEntity,
    slots = {
      { name = "Material 1", slot = "material_1", x = 20, y = 35 },
      { name = "Material 2", slot = "material_2", x = 38, y = 35 },
      { name = "Material 3", slot = "material_3", x = 56, y = 35 },
      { name = "Material 4", slot = "material_4", x = 74, y = 35 },
      { name = "Material 5", slot = "material_5", x = 92, y = 35 },
      { name = "Die", slot = "die", x = 56, y = 57 },
      { name = "Result", slot = "result", x = 142, y = 35, outputOnly = true }
    },
    playerInventory = { x = 8, y = 98, includeHotbar = true }
  }

  local gui = betamoon.containerGuis:add {
    name = "focused_press",
    container = container,
    layout = {
      -- gui.backgrounds and gui.sprites identify the built-in container assets.
      preset = betamoon.mc.gui.backgrounds.container,
      width = 184,
      height = 182,
      title = "Focused Press",
      playerInventoryLabel = { text = "Inventory", x = 8, y = 86 }
    },
    background = { style = "minecraft", drawSlotFrames = true },
    elements = {
      { type = "text", text = "Material pool", x = 20, y = 23 },
      { type = "text", text = "Die", x = 56, y = 79 },
      { type = "text", value = "preferredSlot", format = "Preferred slot: %d",
        x = 104, y = 74, color = "dark_gray" },
      -- gui.sprites supplies the canonical built-in progress image name.
      { type = "progress", value = "progress", maximum = "duration",
        x = 112, y = 35, builtin = betamoon.mc.gui.sprites.furnace_arrow },
      { type = "text", text = "Output blocked", x = 104, y = 62, color = "dark_red",
        visibleWhen = { field = "blocked", equals = true } }
    }
  }

  local press = betamoon.blocks:add {
    id = 227,
    key = "focused_press",
    displayName = "Focused Press",
    -- blockMaterials and stepSounds keep its native block identifiers canonical.
    material = betamoon.mc.blockMaterials.iron,
    hardness = 4,
    resistance = 10,
    texture = 42,
    textures = { front = 61 },
    stepSound = betamoon.mc.stepSounds.metal,
    tileEntity = tileEntity,
    container = container,
    gui = gui,
    piston = { reaction = "block" },
    redstone = { connections = { "north" } },
    drops = { { item = 227, damage = 0 } }
  }

  betamoon.recipes:add {
    type = "shaped",
    output = betamoon.stack(press),
    pattern = { "PIP", "IRI", "PIP" },
    ingredients = {
      P = betamoon.blocks:getRequired(33),
      I = betamoon.items:getRequired(265),
      R = betamoon.items:getRequired(331)
    }
  }
end

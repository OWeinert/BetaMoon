-- Load the companion type and recipe files alongside this machine.
-- Craft SSS / FOF / SSS, where S is cobblestone, F a vanilla furnace, and O stone.
-- Gold recipe: one gold ore + coal/charcoal + a retained stick mold gives three gold
-- ingots and one cobblestone. Compression: four dirt or cobblestone + coal gives stone.
-- Coal is a consumed ingredient, not a separate fuel timer. No redstone is required.
-- Compared with the custom furnace, this machine delegates timing to match.data.duration and
-- has several outputs. Restart after machine edits; recipe-only edits can reload.

name = "Simple Alloy Furnace Example"
version = "1.0.0"
description = "Adds Alloy Furnace, a machine with Base, Coal, and Mold inputs plus Result and Slag outputs. " ..
    "Load advanced examples 06a and 06b alongside it. Craft it with three cobblestone across the " ..
    "top and bottom rows and furnace, stone, furnace across the middle row.\n\n" ..
    "Place it and right-click. Put one gold ore in Base, coal or charcoal in Coal, and a stick in " ..
    "Mold. After about ten seconds, take three gold ingots and one cobblestone from the two output " ..
    "slots; the mold stays in place. Alternatively, use four dirt or four cobblestone in Base, " ..
    "ordinary coal in Coal, and an empty Mold slot to make stone in about four seconds.\n\n" ..
    "There is no separate fuel timer or redstone requirement. The arrow shows progress and the " ..
    "front changes while processing. Full outputs pause valid work and show 'Output full'; remove " ..
    "results to resume. Changing to a different recipe resets progress. The placed machine saves " ..
    "its inventory and progress. Compare slot bindings and the processing callback with the " ..
    "companion recipes. Recipe-only edits can reload; structural machine edits require a restart."
dependencies = { "Simple Alloy Recipe Type Example" }

-- This example contains an alloy furnace block which uses the custom recipe type
-- defined in the companion type file.
function modInit()
  local bm = betamoon
  local alloying = bm.modules:import("example:module/custom_recipe_types").alloying
  -- Map the recipe's logical role names to physical inventory slot names.
  -- They happen to match here, but the machine could call its slot "leftInput"
  -- and still bind base = "leftInput" without changing the recipe type.
  local recipeSlots = {
    ingredients = { base = "base", additive = "additive", mold = "mold" },
    outputs = { result = "result", slag = "slag" }
  }

  --------------------------------------
  -- TILE ENTITY
  --------------------------------------
  local tileEntity = bm.tileEntities:add {
    name = "example:block/alloy_furnace",
    inventory = {
      name = "Alloy Furnace",
      slots = {
        base = { index = 0 }, additive = { index = 1 }, mold = { index = 2 },
        result = { index = 3 }, slag = { index = 4 }
      }
    },
    -- Progress belongs to each placed machine and is saved with its tile data.
    -- Only fields the GUI reads need sync = true. The private signature stays
    -- out of GUI synchronization because it is only used by the processing logic.
    data = {
      progress = { type = "integer", default = 0, sync = true },
      duration = { type = "integer", default = 1, sync = true },
      blocked = { type = "boolean", default = false, sync = true },
      signature = { type = "string", default = "" }
    },
    onTick = {
      mode = "continuous",
      action = function(ctx)
        local data = ctx.entity.data
        local match = ctx.recipes:match { type = alloying, slots = recipeSlots}
        -- No matching recipe is different from a matching recipe with full outputs:
        -- no match clears progress, while blocked output below pauses valid work.
        if match == nil then
          ctx.state:set("lit", false)
          data:set("progress", 0); data:set("duration", 1)
          data:set("signature", ""); data:set("blocked", false)
          return
        end
        -- Reset when the selected recipe/match changes so partial work for one
        -- recipe cannot be carried over to another just by swapping its inputs.
        if data:get("signature") ~= match.signature then
          data:set("progress", 0); data:set("signature", match.signature)
        end
        data:set("duration", match.data.duration)
        -- Check ALL inputs and outputs, including optional slag, before advancing.
        -- Do not insert the main result first and check the secondary output later.
        local canApply = match:canApply()
        -- This machine has no fuel timer: light the front only while processing.
        ctx.state:set("lit", canApply)
        data:set("blocked", not canApply)
        if not canApply then return end
        local progress = data:get("progress") + 1
        if progress >= match.data.duration then
          -- One call consumes required ingredients, retains the mold and inserts
          -- all outputs atomically. Do not also remove coal or create result stacks here.
          local applied = match:apply()
          ctx.state:set("lit", applied)
          data:set("blocked", not applied)
          if not applied then return end
          progress = 0
        end
        data:set("progress", progress)
      end
    }
  }

  --------------------------------------
  -- CONTAINER
  --------------------------------------
  local container = bm.containers:add {
    name = "example:block/alloy_furnace", tileEntity = tileEntity,
    -- slot selects a tile inventory name; name is only the player-facing label.
    -- x/y place its interactive slot. outputOnly stops players inserting into
    -- results while the processing code can still produce items there.
    slots = {
      { name = "Base", slot = "base", x = 20, y = 36 },
      { name = "Coal", slot = "additive", x = 48, y = 36 },
      { name = "Mold", slot = "mold", x = 76, y = 36 },
      { name = "Result", slot = "result", x = 126, y = 36, outputOnly = true },
      { name = "Slag", slot = "slag", x = 152, y = 36, outputOnly = true }
    },
    playerInventory = { x = 8, y = 96, includeHotbar = true }
  }

  --------------------------------------
  -- GUI
  --------------------------------------
  local gui = bm.containerGuis:add {
    name = "example:block/alloy_furnace", container = container,
    layout = {
      -- gui.backgrounds and gui.sprites identify the built-in container assets.
      preset = betamoon.mc.gui.backgrounds.container, width = 184, height = 180,
      title = "Alloy Furnace", playerInventoryLabel = { text = "Inventory", x = 8, y = 84 }
    },
    background = { style = "minecraft", drawSlotFrames = true },
    elements = {
      { type = "text", text = "Base", x = 16, y = 24 },
      { type = "text", text = "Coal", x = 44, y = 24 },
      { type = "text", text = "Mold", x = 72, y = 24,
        tooltip = "Gold: insert a reusable stick mold. Compression: leave empty. No redstone required." },
      { type = "text", text = "Result", x = 119, y = 24 },
      { type = "text", text = "Slag", x = 150, y = 24 },
      -- gui.sprites supplies the canonical built-in progress image name.
      { type = "progress", value = "progress", maximum = "duration", x = 98, y = 36,
        builtin = betamoon.mc.gui.sprites.furnace_arrow, tooltip = "Processing: {progress}/{duration} ticks" },
      { type = "text", text = "Output full", x = 8, y = 65, color = "dark_red",
        visibleWhen = { field = "blocked", equals = true } }
    }
  }

  --------------------------------------
  -- BLOCK
  --------------------------------------
  local alloyFurnaceBlock = bm.blocks:add {
    id = 209,
    key = "example:block/alloy_furnace",
    displayName = "Alloy Furnace",
    -- blockMaterials and stepSounds keep its native block identifiers canonical.
    material = betamoon.mc.blockMaterials.rock,
    harvest = { pickaxe = 0 },
    hardness = 3.5,
    resistance = 5,
    stepSound = betamoon.mc.stepSounds.stone,

    -- Facing lives in block metadata; the tile entity keeps its inventory and
    -- recipe progress when this state changes (for example, through a wrench).
    state = {
      facing = { type = "enum", values = { "north", "east", "south", "west" } },
      lit = { type = "boolean", default = false }
    },
    placement = { facing = "horizontal", facingFrom = "player" },
    textures = { top = 62, bottom = 62, sides = 45 },
    -- Facing uses metadata 0..3; lit adds 4, just like the custom furnace.
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

    drops = {
      { item = 209, damage = 0 }
    },
  }

  -- The recipe are two rows of cobblestone at the top and bottom
  -- with furnaces flanking a stone block in the middle row
  bm.recipes:add {
    type = "shaped",
    pattern = {
      "SSS",
      "FOF",
      "SSS"
    },
    ingredients = {
      S = betamoon.items:getRequired(4),
      F = betamoon.blocks:getRequired(61),
      O = betamoon.blocks:getRequired(1)
    },
    output = betamoon.stack(alloyFurnaceBlock)
  }

end

-- Load the companion type and recipe files alongside this file. The machine tries custom sequence,
-- transformed grid, and unordered pool fabrication in that order.

name = "Advanced Fabricator Example"
version = "1.0.0"
description = "Adds Advanced Fabricator, a machine that applies three kinds of recipe to one 3x3 work grid. " ..
    "Load advanced examples 08a and 08b as well. Craft it with iron ingots in the four corners, a " ..
    "crafting table in the center, and redstone dust in the remaining four cells.\n\n" ..
    "Place it and right-click. For bulk mixing, put a total of two sand and two gravel anywhere in " ..
    "the work grid, keep Catalyst empty, and leave unrelated cells empty. Collect four clay balls " ..
    "from Result and flint plus cobblestone from the two byproduct slots.\n\n" ..
    "For grid assembly, place iron ingots at all four work-grid corners and a stick in the center, " ..
    "leaving the other cells empty, to produce one iron block. For sequence assembly, put only an " ..
    "iron ingot in the top-left cell and coal in the top-middle cell to make gold. Reverse that " ..
    "pair to see the ordered match fail.\n\n" ..
    "Processing is automatic with no fuel, redstone, or timed progress bar. Clear the grid between " ..
    "experiments. Make room in every required output when 'Full output' appears. Compare the " ..
    "sequence, grid, and pool search order and slot bindings with the companion files. The machine " ..
    "saves its inventory; restart after structural edits."
dependencies = { "Advanced Fabrication Types Example", "Advanced Fabrication Recipes Example" }

function modInit()
  local bm = betamoon
  local types = bm.modules:import("example:module/advanced_fabrication_types")

  -- Binding helpers create reusable, immutable descriptions of physical slots.
  -- Generated grid names are prefixROW_COLUMN, using one-based coordinates.
  local workGrid = bm.recipeBindings:grid { prefix = "work_", width = 3, height = 3 }
  local workPool = bm.recipeBindings:pool {
    "work_1_1", "work_1_2", "work_1_3",
    "work_2_1", "work_2_2", "work_2_3",
    "work_3_1", "work_3_2", "work_3_3"
  }
  local byproductPool = bm.recipeBindings:pool { prefix = "byproduct_", count = 2 }

  -- Each query maps semantic recipe roles to this machine's slot layout. A
  -- binding can be reused on every tick without rebuilding nested Lua tables.
  local searches = {
    {
      type = types.sequenceAssembly,
      slots = { ingredients = { lane = workPool }, outputs = { result = "result" } }
    },
    {
      type = types.gridAssembly,
      slots = { ingredients = { work = workGrid }, outputs = { result = "result" } }
    },
    {
      type = types.bulkMixing,
      slots = {
        ingredients = { materials = workPool, catalyst = "catalyst" },
        outputs = { result = "result", byproducts = byproductPool }
      }
    }
  }

  local function findMatch(ctx)
    for _, query in ipairs(searches) do
      local match = ctx.recipes:match(query)
      if match ~= nil then return match end
    end
    return nil
  end

  local tileEntity = bm.tileEntities:add {
    name = "example:block/advanced_fabricator",
    inventory = {
      name = "Advanced Fabricator",
      slots = {
        work_1_1 = { index = 0 }, work_1_2 = { index = 1 }, work_1_3 = { index = 2 },
        work_2_1 = { index = 3 }, work_2_2 = { index = 4 }, work_2_3 = { index = 5 },
        work_3_1 = { index = 6 }, work_3_2 = { index = 7 }, work_3_3 = { index = 8 },
        catalyst = { index = 9 }, result = { index = 10 },
        byproduct_1 = { index = 11 }, byproduct_2 = { index = 12 }
      }
    },
    data = { blocked = { type = "boolean", default = false, sync = true } },
    onTick = {
      mode = "continuous",
      action = function(ctx)
        -- recipes:match reads a detached inventory snapshot. Declarative types
        -- use the exact pool/grid planner; sequenceAssembly invokes the matcher
        -- from the type file and accepts only its validated plan:use allocations.
        local match = findMatch(ctx)
        if match == nil then
          ctx.entity.data:set("blocked", false)
          return
        end

        -- The signature identifies the recipe, binding, transform and allocation.
        -- A timed machine could save it with progress exactly like the alloy furnace.
        local canApply = match:canApply()
        ctx.entity.data:set("blocked", not canApply)
        if not canApply then return end

        -- apply repeats validation against a fresh snapshot, consumes every
        -- selected input, inserts all normal/pooled outputs and remainders in a
        -- private copy, then commits the entire inventory in one operation.
        local applied = match:apply()
        ctx.entity.data:set("blocked", not applied)
      end
    }
  }

  local container = bm.containers:add {
    name = "example:block/advanced_fabricator", tileEntity = tileEntity,
    slots = {
      { name = "Work 1", slot = "work_1_1", x = 26, y = 26 },
      { name = "Work 2", slot = "work_1_2", x = 44, y = 26 },
      { name = "Work 3", slot = "work_1_3", x = 62, y = 26 },
      { name = "Work 4", slot = "work_2_1", x = 26, y = 44 },
      { name = "Work 5", slot = "work_2_2", x = 44, y = 44 },
      { name = "Work 6", slot = "work_2_3", x = 62, y = 44 },
      { name = "Work 7", slot = "work_3_1", x = 26, y = 62 },
      { name = "Work 8", slot = "work_3_2", x = 44, y = 62 },
      { name = "Work 9", slot = "work_3_3", x = 62, y = 62 },
      { name = "Catalyst", slot = "catalyst", x = 88, y = 44 },
      { name = "Result", slot = "result", x = 126, y = 44, outputOnly = true },
      { name = "Byproduct 1", slot = "byproduct_1", x = 152, y = 35, outputOnly = true },
      { name = "Byproduct 2", slot = "byproduct_2", x = 152, y = 53, outputOnly = true }
    },
    playerInventory = { x = 8, y = 98, includeHotbar = true }
  }

  local gui = bm.containerGuis:add {
    name = "example:block/advanced_fabricator", container = container,
    layout = {
      -- gui.backgrounds identifies the built-in container layout.
      preset = betamoon.mc.gui.backgrounds.container, width = 184, height = 182,
      title = "Advanced Fabricator", playerInventoryLabel = { text = "Inventory", x = 8, y = 86 }
    },
    background = { style = "minecraft", drawSlotFrames = true },
    elements = {
      { type = "text", text = "3x3 work area", x = 20, y = 14 },
      { type = "text", text = "Catalyst", x = 82, y = 31 },
      { type = "text", text = "Result", x = 121, y = 31 },
      { type = "text", text = "Full output", x = 104, y = 72, color = "dark_red",
        visibleWhen = { field = "blocked", equals = true } }
    }
  }

  local fabricator = bm.blocks:add {
    id = 216, key = "example:block/advanced_fabricator", displayName = "Advanced Fabricator",
    -- blockMaterials and stepSounds keep its native block identifiers canonical.
    material = betamoon.mc.blockMaterials.rock, hardness = 3.5, resistance = 5, stepSound = betamoon.mc.stepSounds.stone,
    harvest = { pickaxe = 0 },
    texture = 45, tileEntity = tileEntity, container = container, gui = gui,
    drops = { { item = 216 } }
  }

  bm.recipes:add {
    type = "shaped",
    pattern = { "IRI", "RCR", "IRI" },
    ingredients = {
      I = bm.items:getRequired(265), R = bm.items:getRequired(331), C = bm.blocks:getRequired(58)
    },
    output = bm.stack(fabricator)
  }
end

-- Build a machine in four steps: tile entity (saved data and logic), container
-- (real inventory interaction), GUI (drawing), and block (ties the handles together).
-- Craft a vanilla furnace surrounded by eight iron ingots, place it, and right-click.
-- Add a vanilla smeltable input and fuel; this furnace processes one item in 100 ticks.
-- It uses vanilla smelting and works without any of the custom recipe-type scripts.
-- Read the processing callback as: spend burning time, find a match, acquire fuel,
-- advance progress, then apply the recipe. Structural changes require a game restart.

name = "Custom Furnace Example"
version = "1.0.0"
description = "Adds Fast Furnace, a custom furnace that processes vanilla smelting recipes in 100 ticks, " ..
    "about five seconds per item at normal game speed. Craft it by surrounding a vanilla furnace " ..
    "with eight iron ingots.\n\n" ..
    "Place it and right-click to open its screen. Put a smeltable item, such as iron ore, in the " ..
    "upper-left input slot and normal furnace fuel in the lower-left slot. Take the result from " ..
    "the slot on the right. The flame shows remaining fuel, the arrow shows cooking progress, and " ..
    "the front changes appearance while fuel is burning.\n\n" ..
    "Compare it with a vanilla furnace to see the shorter cooking time. Try removing the input or " ..
    "blocking the output to follow the progress checks; fuel that is already burning continues to " ..
    "burn. Inventory and machine data are saved with the placed block. Compare the processing " ..
    "callback, recipe binding, and GUI values, and restart Minecraft after structural machine " ..
    "edits."
function modInit()

  -- This is the time the furnace needs to cook in ticks. For example a vanilla furnace uses 200.
  local cookDuration = 100

  -- A tile entity stores information for one placed block. Normal blocks only
  -- store an ID and damage value, which is not enough for inventories or timers.
  local fastFurnaceEntity = betamoon.tileEntities:add {
    name = "fast_furnace",

    -- These names make the three inventory positions easy to understand in Lua.
    -- Explicit indexes keep the saved inventory layout stable in later versions.
    inventory = {
      name = "Fast Furnace",
      slots = {
        input = { index = 0 },
        fuel = { index = 1 },
        output = { index = 2 }
      }
    },

    -- Data fields are saved in the world together with the tile entity.
    -- sync = true also sends an integer to an open container GUI. The GUI needs
    -- these values to draw its flame and cooking arrow at the correct sizes.
    data = {
      burnTime = {
        type = "integer",
        default = 0,
        sync = true
      },
      totalBurnTime = {
        type = "integer",
        default = 1,
        sync = true
      },
      cookTime = {
        type = "integer",
        default = 0,
        sync = true
      },
      cookDuration = { type = "integer", default = 100, sync = true },
      recipeSignature = { type = "string", default = "" }
    },

    -- continuous means that the action runs every game tick while the block's
    -- chunk is loaded. Minecraft normally runs 20 game ticks each second.
    onTick = {
      mode = "continuous",
      action = function(ctx)
        local entity = ctx.entity
        local data = entity.data
        local inventory = entity.inventory

        -- Read the declared field through the data API. BetaMoon handles its saved
        -- representation; Lua code does not need to read or write raw NBT tags.
        local burnTime = data:get("burnTime")
        if burnTime > 0 then
          burnTime = burnTime - 1
          data:set("burnTime", burnTime)
        end

        -- Bind the vanilla smelting input/output roles to this tile's slots.
        local match = ctx.recipes:match {
          type = "smelting",
          slots = {
            ingredients = { input = "input" },
            outputs = { output = "output" }
          }
        }

        -- match is nil when no recipe fits. A signature identifies the current
        -- match so replacing the input cannot inherit another recipe's progress.
        local signature = match and match.signature or ""
        if data:get("recipeSignature") ~= signature then
          data:set("cookTime", 0)
          data:set("recipeSignature", signature)
        end
        data:set("cookDuration", cookDuration)
        -- Matching alone does not guarantee room for output. canApply also checks
        -- that the inputs and destination slots permit a complete operation.
        -- This check happens before acquiring new fuel to avoid wasting it on a full output.
        local canCook = match ~= nil and match:canApply()

        -- consumeFuel removes one fuel item and returns how many ticks it burns.
        -- It also preserves container items, such as the bucket from lava fuel.
        if burnTime == 0 and canCook then
          burnTime = inventory:consumeFuel("fuel")
          if burnTime > 0 then
            data:set("burnTime", burnTime)
            data:set("totalBurnTime", burnTime)
          end
        end

        -- Like vanilla, the front stays lit until the current fuel burns out.
        ctx.state:set("lit", burnTime > 0)

        if burnTime > 0 and canCook then
          local cookTime = data:get("cookTime") + 1

          -- The machine duration controls both processing and the GUI.
          -- Application consumes input and inserts the vanilla result atomically.
          if match ~= nil and cookTime >= cookDuration then
            -- apply rechecks and changes the inventory as one operation. A failed
            -- application must not be followed by manual input removal or free output.
            -- On success the recipe API has already consumed and inserted everything.
            if not match:apply() then return end
            cookTime = 0
          end

          data:set("cookTime", cookTime)
        elseif data:get("cookTime") ~= 0 then
          -- Losing fuel, losing input, or filling the output resets progress.
          data:set("cookTime", 0)
        end
      end
    }
  }

  -- A container describes the real inventory slots and their screen positions.
  -- It controls item interaction; it does not draw anything by itself.
  local fastFurnaceContainer = betamoon.containers:add {
    name = "fast_furnace",
    tileEntity = fastFurnaceEntity,

    slots = {
      { name = "Input", slot = "input", x = 56, y = 17 },
      { name = "Fuel", slot = "fuel", x = 56, y = 53 },

      -- outputOnly prevents the player from placing an item into this slot.
      { name = "Output", slot = "output", x = 116, y = 35, outputOnly = true }
    },

    -- These coordinates match the player inventory in Minecraft's furnace GUI.
    playerInventory = {
      x = 8,
      y = 84,
      includeHotbar = true
    }
  }

  -- The GUI is separate from the container. It only controls how the screen
  -- looks. The container above still controls all real item slots.
  --
  -- The GUI showcase explains most of the ContainerGUI API
  -- -> 03_adv_05_gui_showcase.lua
  local fastFurnaceGui = betamoon.containerGuis:add {
    name = "fast_furnace",
    container = fastFurnaceContainer,

    -- A preset supplies the normal furnace size, background, and label positions.
    -- You can replace individual layout fields without rebuilding the whole GUI.
    layout = {
      -- gui.backgrounds and gui.sprites expose BetaMoon's built-in Minecraft GUI assets.
      preset = betamoon.mc.gui.backgrounds.furnace,
      title = {
        text = "Fast Furnace",
        color = "dark_gray"
      }
    },

    elements = {
      {
        -- A progress element clips a complete image for us. This built-in sprite
        -- is Minecraft's flame, so no texture coordinates or custom PNG are needed.
        type = "progress",
        value = "burnTime",
        -- Quoted values name synced fields; a number such as maximum = 100
        -- would be a fixed limit. The GUI calculates the fraction to draw.
        maximum = "totalBurnTime",
        direction = "bottom_to_top",
        x = 56,
        y = 36,
        -- gui.sprites supplies the canonical built-in progress image name.
        builtin = betamoon.mc.gui.sprites.furnace_flame,
        hideWhenEmpty = true,
        tooltip = "Fuel left: {burnTime}/{totalBurnTime} ticks"
      },
      {
        type = "progress",
        value = "cookTime",
        maximum = "cookDuration",
        direction = "left_to_right",
        x = 79,
        y = 34,
        builtin = betamoon.mc.gui.sprites.furnace_arrow,
        tooltip = "Cooking progress: {cookTime}/{cookDuration}"
      },
      {
        -- Conditions show this label only while the furnace has burning fuel.
        -- GUI conditions may only read tile data fields marked with sync = true.
        type = "text",
        text = "Fast",
        x = 145,
        y = 6,
        color = "dark_green",
        visibleWhen = {
          field = "burnTime",
          greaterThan = 0
        }
      }
    }
  }

  -- The block owns the tile entity and connects it to its standalone container
  -- and GUI. This machine supplies all three compatible handles from this script;
  -- a tile entity that has no inventory screen can omit both container and GUI.
  local fastFurnaceBlock = betamoon.blocks:add {
    id = 204,
    -- blockMaterials and stepSounds keep native block identifiers canonical.
    material = betamoon.mc.blockMaterials.rock,
    key = "fast_furnace",
    displayName = "Fast Furnace",
    hardness = 3.5,
    resistance = 5,
    stepSound = betamoon.mc.stepSounds.stone,

    -- Store orientation in block metadata, independently of the tile entity's
    -- inventory and cooking data. Like a vanilla furnace, face toward the placer.
    state = {
      facing = { type = "enum", values = { "north", "east", "south", "west" } },
      lit = { type = "boolean", default = false }
    },
    placement = { facing = "horizontal", facingFrom = "player" },

    -- Facing occupies the first two metadata bits; lit adds 4 when true.
    -- Only the front changes: 44 is unlit, 61 is lit. Other faces use base texture 45.
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

    -- Normal unlit furnace textures from terrain.png.
    textures = {
      top = 62,
      bottom = 62,
      sides = 45
    },

    tileEntity = fastFurnaceEntity,
    container = fastFurnaceContainer,
    gui = fastFurnaceGui,

    -- Drop one unlit furnace with its default facing, regardless of placed state.
    drops = {
      { item = 204, damage = 0 }
    }
  }

  -- Finally, add a recipe so the example block can be obtained in survival.
  -- The center furnace is surrounded by iron ingots in the crafting grid.
  betamoon.recipes:add {
    type = "shaped",
    pattern = {
      "III",
      "IFI",
      "III"
    },
    ingredients = {
      I = betamoon.items:getRequired(265),
      F = betamoon.blocks:getRequired(61)
    },
    output = betamoon.stack(fastFurnaceBlock)
  }
end

-- This script is automatically kept loaded during BetaMoon hot reloads because
-- it owns structural tile-entity content which cannot be reloaded during runtime.
-- You will have to restart Minecraft after making changes.

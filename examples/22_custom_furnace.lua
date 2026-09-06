name = "Custom Furnace Example"
version = "1.0.0"
description = "Creates a furnace-like machine that cooks items twice as fast."

function modInit()
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
      }
    },

    -- continuous means that the action runs every game tick while the block's
    -- chunk is loaded. Minecraft normally runs 20 game ticks each second.
    onTick = {
      mode = "continuous",
      action = function(ctx)
        local entity = ctx.entity
        local data = entity.data
        local inventory = entity.inventory

        -- this gets the NBT data from the NBT tag defined in the tileEntity's data.
        local burnTime = data:get("burnTime")
        if burnTime > 0 then
          burnTime = burnTime - 1
          data:set("burnTime", burnTime)
        end

        -- BetaMoon asks Minecraft's normal smelting recipe list for the result.
        -- The returned value is nil when the input cannot be smelted.
        local input = inventory:get("input")
        local result = ctx.recipes:getSmeltingResult(input)
        local canCook = result ~= nil and inventory:canAdd("output", result)

        -- consumeFuel removes one fuel item and returns how many ticks it burns.
        -- It also preserves container items, such as the bucket from lava fuel.
        if burnTime == 0 and canCook then
          burnTime = inventory:consumeFuel("fuel")
          if burnTime > 0 then
            data:set("burnTime", burnTime)
            data:set("totalBurnTime", burnTime)
          end
        end

        if burnTime > 0 and canCook then
          local cookTime = data:get("cookTime") + 1

          -- A normal furnace needs 200 ticks. Finishing at 100 ticks makes this
          -- furnace exactly twice as fast while using fuel at the normal speed.
          if cookTime >= 100 then
            inventory:remove("input", 1)
            inventory:add("output", result)
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
  local fastFurnaceGui = betamoon.containerGuis:add {
    name = "fast_furnace",
    container = fastFurnaceContainer,

    -- A preset supplies the normal furnace size, background, and label positions.
    -- You can replace individual layout fields without rebuilding the whole GUI.
    layout = {
      preset = "minecraft:furnace",
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
        maximum = "totalBurnTime",
        direction = "bottom_to_top",
        x = 56,
        y = 36,
        builtin = "minecraft:furnace_flame",
        hideWhenEmpty = true,
        tooltip = "Fuel left: {burnTime}/{totalBurnTime} ticks"
      },
      {
        type = "progress",
        value = "cookTime",
        maximum = 100,
        direction = "left_to_right",
        x = 79,
        y = 34,
        builtin = "minecraft:furnace_arrow",
        tooltip = "Cooking progress: {cookTime}/100"
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
  -- and GUI. All three handles are required and must come from this script.
  local fastFurnaceBlock = betamoon.blocks:add {
    id = 204,
    material = "rock",
    key = "fast_furnace",
    displayName = "Fast Furnace",
    hardness = 3.5,
    resistance = 5,
    stepSound = "stone",

    -- These are the normal unlit furnace textures from terrain.png. The front
    -- faces south in this simple example; block rotation can be added separately.
    textures = {
      top = 62,
      bottom = 62,
      sides = 45,
      front = 44
    },

    tileEntity = fastFurnaceEntity,
    container = fastFurnaceContainer,
    gui = fastFurnaceGui
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
    output = betamoon.stack(fastFurnaceBlock, 1)
  }
end

-- This script is automatically kept loaded during BetaMoon hot reloads because
-- it owns structural tile-entity content. Restart Minecraft after editing it.

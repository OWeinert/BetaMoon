-- Read the basic storage lesson first for the tile entity -> container -> GUI -> block relationship.
-- Copy this script AND the gui_showcase folder into lua_scripts, keeping the folder name.
-- Craft chest + redstone dust and open the placed block. Use the Page slot to inspect
-- one page at a time, then find the corresponding numbered group below.
-- Edit one element at a time and restart Minecraft: this script owns structural content.
-- GUI coordinates are local GUI pixels; they are unrelated to world block coordinates.

name = "GUI Showcase"
version = "1.0.0"
description = "Adds GUI Showcase, an interactive four-page gallery of container GUI elements. Copy this " ..
    "script and the complete gui_showcase asset folder into your scripts folder, preserving the " ..
    "folder name. Craft one chest with one redstone dust in any arrangement, place the resulting " ..
    "block, and right-click it.\n\n" ..
    "Leave the Page slot empty for an automatic tour, with roughly eight seconds per page. Put a " ..
    "stack of one, two, three, or four items in that slot to hold the corresponding page; the " ..
    "items are not consumed. Put an item into Sample to see its live preview on page 1.\n\n" ..
    "Page 1 demonstrates text alignment, images, item previews, and hover tooltips. Page 2 " ..
    "animates progress bars in four directions, including empty and nearly empty values. Page 3 " ..
    "shows state images and conditional visibility. Page 4 demonstrates groups, offsets, drawing " ..
    "layers, and translucent rectangles. Hover over examples for additional explanations.\n\n" ..
    "Use the real inventory slots to control the gallery; the drawn previews and decorations are " ..
    "not clickable inventory slots. Compare each page with its numbered source section. To try " ..
    "another background, edit BACKGROUND and restart Minecraft; structural GUI changes require a " ..
    "restart."

-- This example is an overview of most features the ContainerGUI API offers.
-- The defined GUI is also explorable ingame via the "GUI Showcase"-block.

-- Empty Page slot: automatic tour, eight seconds per page.
-- Put 1, 2, 3, or 4 items in Page to hold that page; items are never consumed.
-- Put an item in Sample to see a live item preview on page 1.
-- GUI elements are visual: real item interaction belongs to container slots.
local BLOCK_ID = 208
-- The trailing slash allows ASSETS .. "panel.png" to form a relative path.
-- Keep these files under lua_scripts so the GUI can load them.
local ASSETS = "gui_showcase/"
local BACKGROUND = "panel" -- Try "custom" or "builtin", then restart.

function modInit()
  -- First we need a tileEntity to attach our GUI to.
  local showcaseEntity = betamoon.tileEntities:add {
    name = "gui_showcase",
    inventory = {
      name = "GUI Showcase",
      slots = { page = { index = 0 }, sample = { index = 1 } }
    },
    data = {
      -- GUI values, conditions, and tooltip placeholders require sync = true
      -- so the open container GUI receives changes to the tile's values.
      page = { type = "integer", default = 1, sync = true },
      progress = { type = "integer", default = 0, sync = true },
      limit = { type = "integer", default = 100, sync = true },
      mode = { type = "integer", default = 0, sync = true },
      active = { type = "boolean", default = false, sync = true },
      -- A private timer can be saved without being exposed to the GUI.
      clock = { type = "integer", default = 0 }
    },
    onTick = {
      mode = "continuous",
      action = function(ctx)
        local data = ctx.entity.data
        -- % is remainder: wrapping at 640 makes four 160-tick pages repeat.
        -- The Page slot overrides the automatic page using its stack count;
        -- an empty inventory slot is nil and selects the clock-driven tour.
        local clock = (data:get("clock") + 1) % 640
        data:set("clock", clock)
        local selector = ctx.entity.inventory:get("page")
        local page = selector and math.min(4, selector.count) or math.floor(clock / 160) + 1
        data:set("page", page)

        -- Hold zero, one, and full briefly to make clipping/minimumPixels clear.
        local tick = clock % 160
        local progress = tick < 20 and 0 or (tick < 40 and 1 or math.min(100, tick - 39))
        data:set("progress", progress)
        data:set("active", progress > 50)
        data:set("mode", math.floor(tick / 54)) -- 0, 1, then 2 (the fallback image).
      end
    }
  }

  -- This inventory GUI needs a container to connect real slots to the tile entity.
  local showcaseContainer = betamoon.containers:add {
    name = "gui_showcase",
    tileEntity = showcaseEntity,
    slots = {
      { name = "Page", slot = "page", x = 188, y = 158 },
      { name = "Sample", slot = "sample", x = 228, y = 158 }
      -- A machine output slot can additionally use outputOnly = true.
    },
    playerInventory = { x = 8, y = 152, includeHotbar = true }
  }

  -- These three alternatives demonstrate a generated panel, an entire PNG,
  -- and a built-in background. Custom images are detected at their native size.
  local background = { style = "minecraft", drawSlotFrames = true }
  if BACKGROUND == "custom" then
    background = { image = ASSETS .. "panel.png", drawSlotFrames = true }
  elseif BACKGROUND == "builtin" then
    -- gui.backgrounds and gui.sprites expose the complete built-in Minecraft GUI asset names.
    background = { builtin = betamoon.mc.gui.backgrounds.container, drawSlotFrames = true }
  end

  -----------------------------------------
  -- Here starts the actual GUI creation --
  -----------------------------------------
  local showcaseGui = betamoon.containerGuis:add {
    name = "gui_showcase",
    container = showcaseContainer,
    layout = {
      -- A preset normally supplies size, background, and label positions.
      -- This gallery overrides the size to leave room for annotated examples.
      -- Other keys in betamoon.mc.gui.backgrounds are furnace, crafting,
      -- dispenser, inventory, and chest.
      -- Chest layouts also accept rows = 1..6; see the API docs for default sizes.
      preset = betamoon.mc.gui.backgrounds.container,
      width = 256,
      height = 232,

      pauseGame = false, -- true pauses single-player; this demo needs its timer so we disable pausing.

      title = {
        text = "GUI API Showcase", x = 8, y = 6, width = 240,
        align = "center", color = "dark_gray", shadow = false
      },
      playerInventoryLabel = { text = "Inventory", x = 8, y = 140 }
      -- Either label also accepts a plain string, or false to hide it.
    },
    background = background,
    -- Elements are drawings, not inventory slots. x/y default to the GUI's
    -- top-left corner; width/height are GUI pixels, even when the screen is scaled.
    -- Groups let several drawings share a position or visibility condition.
    elements = {
      -- Shared footer: the only page controls are REAL inventory slots.
      { type = "rectangle", x = 8, y = 134, width = 240, height = 1, color = 0x808080 },
      { type = "text", text = "Page", x = 184, y = 146 },
      { type = "text", text = "Sample", x = 218, y = 146 },
      { type = "text", text = "1-4 items:", x = 184, y = 182 },
      { type = "text", text = "hold page", x = 184, y = 192 },
      { type = "text", text = "Empty: tour", x = 184, y = 208 },
      { type = "text", value = "page", format = "Page %d / 4", x = 184, y = 220 },

      -- 1. Each page is a group with one inherited visibility condition.
      { type = "group", visibleWhen = { field = "page", equals = 1 },
        elements = {
          { type = "text", text = "1. Images, text and items", x = 8, y = 22 },
          -- A nested group adds its x/y offset to each child.
          { type = "group", x = 8, y = 38, elements = {
              { type = "text", text = "left", width = 72, align = "left" },
              { type = "text", text = "center", x = 84, width = 72, align = "center" },
              { type = "text", text = "right", x = 168, width = 72, align = "right" }
            }
          },
          { type = "rectangle", x = 8, y = 50, width = 240, height = 27,
            color = 0xFF404858, layer = "background"},

          -- text supplies a fixed label; value names a synced data field.
          -- format controls its presentation: %03d pads an integer to three digits.
          { type = "text", value = "progress", format = "Value: %03d", x = 12, y = 54,
            color = "gold", shadow = true, tooltip = "Text: synced integer + format"
          },

          { type = "text", value = "active", format = "Active: %s", x = 12, y = 65,
            color = 0xFFFFFF, tooltip = "Text: synced boolean, numeric RGB color"
          },

          { type = "text", text = "Static + shadow", x = 130, y = 58, color = "aqua", shadow = true
        },

          { type = "image", image = ASSETS .. "state_on.png", x = 8, y = 90,
            tooltip = "Image: complete custom PNG at native size"
          },

          { type = "image", image = ASSETS .. "state_off.png", x = 32, y = 86,
            width = 24, height = 24, tooltip = "Image: width/height scale a 16px PNG"
          },

          -- gui.sprites supplies the canonical names for these built-in decorations.
          { type = "image", builtin = betamoon.mc.gui.sprites.furnace_flame, x = 64, y = 90,
            tooltip = "Built-in: minecraft:furnace_flame"
          },

          { type = "image", builtin = betamoon.mc.gui.sprites.furnace_arrow, x = 84, y = 88,
            tooltip = "Built-in: minecraft:furnace_arrow"
          },

          { type = "image", builtin = betamoon.mc.gui.sprites.crafting_arrow, x = 114, y = 88,
            tooltip = "Built-in: minecraft:crafting_arrow"
          },

          { type = "image", builtin = betamoon.mc.gui.sprites.slot, x = 147, y = 87,
            tooltip = "Built-in: minecraft:slot (decoration only)"
          },

          { type = "image", builtin = betamoon.mc.gui.sprites.output_slot, x = 177, y = 83 },

          -- A fixed item preview takes an ID/count description. The next preview
          -- uses slot = "sample" to mirror real inventory content without owning it.
          { type = "item",
            item = { id = 264, count = 3 },
            x = 182, y = 88,
            layer = "foreground",
            showCount = true,
            tooltip = { "Item: fixed preview of 3 diamonds", "This is a drawing, not an inventory slot." }
          },
          { type = "item",
            slot = "sample",
            x = 224, y = 88,
            showCount = false,
            layer = "foreground", tooltip = { "Item: live Sample-slot preview", "showCount = false hides the stack count." } },

          { type = "rectangle", x = 8, y = 118, width = 240, height = 12, color = 0xFFA65900 },

          { type = "text", text = "Hover here: multiline tooltip", x = 12, y = 120, color = "white" },

          -- 'tooltip' is an invisible hover region; 'text' is its fallback content.
          { type = "tooltip", x = 8, y = 118, width = 240, height = 12, text = {
            "Tooltip region: three separate lines",
            "Value: {progress}/{limit}; active: {active}",
            "Place an item in Sample for a live preview."
            }
          }
        }
      },

      -- 2. Progress clips a texture; no Lua drawing callback is necessary.
      { type = "group", visibleWhen = { field = "page", equals = 2 },
        elements = {
          { type = "text", text = "2. Four progress directions", x = 8, y = 22 },

          { type = "text", text = "left_to_right", x = 8, y = 42 },

          -- 100 is a constant; "limit" in the next bar names a synced field.
          -- The fill is clipped over the empty background. minimumPixels keeps tiny
          -- positive values visible; hideWhenEmpty suppresses the fill at zero.
          { type = "progress", value = "progress", maximum = 100,
            x = 8, y = 54, direction = "left_to_right",
            image = ASSETS .. "bar_fill.png", background = ASSETS .. "bar_empty.png",
            minimumPixels = 2, tooltip = { "Constant maximum = 100", "Value: {progress}; minimumPixels = 2" }
          },

          { type = "text", text = "right_to_left", x = 8, y = 82 },

          { type = "progress", value = "progress", maximum = "limit",
            x = 8, y = 94, direction = "right_to_left",
            image = ASSETS .. "bar_fill.png", background = ASSETS .. "bar_empty.png",
            hideWhenEmpty = true, tooltip = { "Maximum reads the synced limit field", "Value: {progress}/{limit}; hides at zero" }
          },

          { type = "text", text = "Down", x = 118, y = 42 },

          { type = "progress", value = "progress", maximum = 100,
            x = 124, y = 54, direction = "top_to_bottom",
            image = ASSETS .. "meter_fill.png", background = ASSETS .. "meter_empty.png",
            tooltip = "top_to_bottom: {progress}/{limit}"
          },

          { type = "text", text = "Up", x = 172, y = 42 },

          { type = "progress", value = "progress", maximum = "limit",
            x = 172, y = 54, direction = "bottom_to_top",
            image = ASSETS .. "meter_fill.png", background = ASSETS .. "meter_empty.png",
            tooltip = "bottom_to_top: {progress}/{limit}"
          },

          { type = "text", text = "Built-in", x = 200, y = 42 },

          { type = "progress", value = "progress", maximum = 100,
            x = 212, y = 54, direction = "bottom_to_top", minimumPixels = 1,
            builtin = betamoon.mc.gui.sprites.furnace_flame, hideWhenEmpty = true,
            tooltip = "Built-in flame: no fill at zero"
          },

          { type = "progress", value = "progress", maximum = 100,
            x = 208, y = 84, builtin = betamoon.mc.gui.sprites.furnace_arrow,
            backgroundBuiltin = betamoon.mc.gui.sprites.crafting_arrow,
            tooltip = { "A built-in sprite as the empty background", "backgroundBuiltin = minecraft:crafting_arrow" }
          },

          { type = "text", value = "progress", format = "Value: %d / 100", x = 8, y = 118,
            tooltip = "The timer holds 0, 1 and 100 to show edge cases."
          }
        }
      },

      -- 3. State images can map values, use a fallback, or use boolean shortcuts.
      { type = "group", visibleWhen = { field = "page", equals = 3 },
        elements = {
          { type = "text", text = "3. States and conditions", x = 8, y = 22 },
          { type = "state_image",
            value = "active",
            x = 8, y = 40,
            whenTrue = ASSETS .. "state_on.png", whenFalse = ASSETS .. "state_off.png",
            tooltip = "Boolean shortcuts: active = {active}"
          },

          -- [0] and [1] are explicit numeric table keys, not positions in a Lua list.
          -- No image is mapped for 2, so default supplies a deliberate fallback.
          { type = "state_image",
            value = "mode",
            x = 36, y = 40,
            states = { [0] = ASSETS .. "state_on.png", [1] = ASSETS .. "state_off.png" },
            default = ASSETS .. "state_unknown.png",
            tooltip = { "states[0] = green, states[1] = yellow", "Unmapped mode 2 uses default (red). Mode: {mode}" }
          },

          { type = "text", value = "progress", format = "Value: %d", x = 68, y = 42 },
          { type = "text", value = "mode", format = "Mode: %d", x = 164, y = 42 },
          { type = "text", text = "Green labels mean the condition is true.", x = 8, y = 60 },
          -- ALL and ANY conditions can be nested with ordinary comparisons.
          -- all requires every nested comparison; any requires at least one.
          -- The enclosing page group's condition must also be satisfied, so these
          -- labels cannot appear on another page merely because their own test passes.
          { type = "text",
            text = "ALL",
            x = 8, y = 74,
            color = "dark_green",
            visibleWhen = {
              all = {
                { field = "active", equals = true }, { field = "progress", greaterOrEqual = 75 }
              }
            }, tooltip = "all: active AND value >= 75"
          },

          { type = "text",
            text = "ANY",
            x = 128, y = 74,
            color = "dark_green",
            visibleWhen = {
              any = {
                { field = "mode", equals = 0 }, { field = "progress", lessOrEqual = 1 }
              }
            }, tooltip = "any: mode == 0 OR value <= 1"
          },

          { type = "text",
            text = "active == true", x = 8, y = 88, color = "dark_green",
            visibleWhen = { field = "active", equals = true },
            tooltip = "visibleWhen: equals"
          },

          { type = "text",
            text = "mode ~= 0",
            x = 128, y = 88,
            color = "dark_green",
            visibleWhen = { field = "mode", notEquals = 0 },
            tooltip = "visibleWhen: notEquals"
          },

          { type = "text",
            text = "value > 50",
            x = 8, y = 102,
            color = "dark_green",
            visibleWhen = { field = "progress", greaterThan = 50 },
            tooltip = "visibleWhen: greaterThan"
          },

          { type = "text",
            text = "value >= 50",
            x = 128, y = 102,
            color = "dark_green",
            visibleWhen = { field = "progress", greaterOrEqual = 50 },
            tooltip = "visibleWhen: greaterOrEqual"
          },

          { type = "text",
            text = "value < 50",
            x = 8, y = 116,
            color = "dark_green",
          visibleWhen = { field = "progress", lessThan = 50 },
          tooltip = "visibleWhen: lessThan"
          },

          { type = "text",
            text = "value <= 50",
            x = 128, y = 116,
            color = "dark_green",
            visibleWhen = { field = "progress", lessOrEqual = 50 },
            tooltip = "visibleWhen: lessOrEqual"
          }
        }
      },

      -- 4. Anchors refer to the WHOLE GUI; x/y are offsets from that anchor.
      -- The lower labels use negative y offsets to stay above the inventory.
      { type = "group", visibleWhen = { field = "page", equals = 4 },
        elements = {
          { type = "text", text = "4. Anchors, groups and layers", x = 8, y = 22 },
          { type = "text", text = "TL", x = 8, y = 38, anchor = "top_left", tooltip = "anchor = top_left" },
          { type = "text", text = "TC", y = 38, width = 24, align = "center", anchor = "top_center",
            tooltip = "anchor = top_center"
          },
          { type = "text", text = "TR", x = -8, y = 38, anchor = "top_right", tooltip = "anchor = top_right" },
          { type = "text", text = "BL", x = 8, y = -102, anchor = "bottom_left", tooltip = "bottom_left, y = -102" },
          { type = "text", text = "BC", y = -102, width = 24, align = "center", anchor = "bottom_center",
            tooltip = "bottom_center, y = -102"
          },
          { type = "text", text = "BR", x = -8, y = -102, anchor = "bottom_right", tooltip = "bottom_right, y = -102"},
          { type = "text", text = "CENTER", y = -60, width = 60, align = "center", anchor = "center",
            tooltip = "center, x = 0, y = -60"
          },

          { type = "group", x = 12, y = 58,
            elements = {
              { type = "rectangle", width = 44, height = 48, color = 0xFF404858 },
              { type = "group", x = 14, y = 6,
                elements = {
                  { type = "image", image = ASSETS .. "state_on.png", tooltip = "Nested group: offsets add together" }
                }
              },
              { type = "text", text = "Group", x = 6, y = 32, color = "white" }
            }
          },

          -- Intentionally declare the foreground BEFORE the background: layer wins.
          { type = "text", text = "Foreground", x = 76, y = 86, width = 108, align = "center",
            color = "white", shadow = true, layer = "foreground", tooltip = "Text on the foreground layer"
          },
          { type = "image", image = ASSETS .. "state_off.png", x = 122, y = 66, layer = "content",
            tooltip = "Image on the content layer"
          },
          { type = "rectangle", x = 76, y = 64, width = 108, height = 36,
            color = 0xFF404858, layer = "background"
          },

          -- ARGB permits translucent rectangles; this covers part of another panel.
          { type = "rectangle", x = 196, y = 64, width = 48, height = 36, color = 0xFF5555FF },
          -- 0x80FFAA00 is ARGB: 80 is partial opacity, FFAA00 is the RGB color.
          -- This is why the rectangle beneath remains visible through the overlay.
          { type = "rectangle", x = 206, y = 72, width = 28, height = 20, color = 0x80FFAA00,
            tooltip = "Rectangle: numeric ARGB, alpha = 0x80"
          }
        }
      }
    }
  }

  local block = betamoon.blocks:add {
    id = BLOCK_ID,
    -- blockMaterials and stepSounds keep native block identifiers canonical.
    material = betamoon.mc.blockMaterials.rock,
    key = "gui_showcase",
    displayName = "GUI Showcase",
    hardness = 1.5,
    resistance = 5,
    stepSound = betamoon.mc.stepSounds.stone,
    texture = 62,
    tileEntity = showcaseEntity,
    container = showcaseContainer,
    gui = showcaseGui
  }
  betamoon.recipes:add {
    type = "shapeless",
    ingredients = { betamoon.blocks:getRequired(54), betamoon.items:getRequired(331) },
    output = betamoon.stack(block, 1)
  }
end

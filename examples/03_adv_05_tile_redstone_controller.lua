-- Craft cobblestone + repeater + redstone for the controller. Open it and insert
-- any item as a retained control key. Power its north side to toggle a saved output
-- on each rising edge; connect output wire to the south.
-- Breaking and replacing the input signal without an off transition does not retrigger it.
-- Structural tile data survives world saves and requires a restart after script edits.

name = "Tile Redstone Controller Example"
version = "1.0.0"
description = "Adds Tile Redstone Controller, a saved toggle with a control-key slot and an accepted-pulse " ..
    "counter. Craft it in a 2x2 area: leave the upper-left empty, place cobblestone upper-right, " ..
    "redstone dust lower-left, and a repeater lower-right.\n\n" ..
    "Enable BetaMoon's instrumentation agent for custom solid-block redstone behavior. Place the " ..
    "controller, right-click it, and insert any item into the Control Key slot. The item is " ..
    "retained, not consumed. Supply a switchable redstone input on its north side and connect " ..
    "output wire to its south side. These directions are fixed in the world.\n\n" ..
    "Each new off-to-on input transition toggles the saved output and increments the counter shown " ..
    "in the GUI and chat. A held signal does not repeatedly toggle it. Removing the key prevents " ..
    "new accepted pulses but leaves the current output and count intact. Reinsert the key with the " ..
    "input off, then switch on to try again. Save and rejoin with the block placed to check " ..
    "persistence. Restart after structural edits."

function modInit()
  local tileEntity = betamoon.tileEntities:add {
    name = "tile_redstone_controller",
    inventory = {
      name = "Redstone Controller",
      slots = { controlKey = { index = 0 } }
    },
    data = {
      output = { type = "boolean", default = false, sync = true },
      inputWasPowered = { type = "boolean", default = false },
      pulseCount = { type = "integer", default = 0, sync = true }
    }
  }

  local container = betamoon.containers:add {
    name = "tile_redstone_controller",
    tileEntity = tileEntity,
    slots = {
      { name = "Control Key", slot = "controlKey", x = 80, y = 35 }
    },
    playerInventory = { x = 8, y = 84, includeHotbar = true }
  }

  local gui = betamoon.containerGuis:add {
    name = "tile_redstone_controller",
    container = container,
    layout = {
      -- gui.backgrounds identifies the built-in container layout.
      preset = betamoon.mc.gui.backgrounds.container,
      height = 168,
      title = "Redstone Controller",
      playerInventoryLabel = { text = "Inventory", x = 8, y = 72 }
    },
    background = { style = "minecraft", drawSlotFrames = true },
    elements = {
      { type = "text", text = "Retained control key", x = 54, y = 23 },
      { type = "text", value = "pulseCount", format = "Accepted pulses: %d", x = 8, y = 58 },
      { type = "text", text = "Output on", x = 110, y = 58, color = "dark_green",
        visibleWhen = { field = "output", equals = true } },
      { type = "text", text = "Output off", x = 110, y = 58, color = "dark_red",
        visibleWhen = { field = "output", equals = false } }
    }
  }

  local controller = betamoon.blocks:add {
    id = 225,
    key = "tile_redstone_controller",
    displayName = "Tile Redstone Controller",
    -- blockMaterials supplies the canonical native material identifier.
    material = betamoon.mc.blockMaterials.rock,
    hardness = 1.5,
    resistance = 8,
    texture = 1,
    textures = { north = 93, south = 94 },
    tileEntity = tileEntity,
    container = container,
    gui = gui,
    piston = { reaction = "block" },
    redstone = {
      -- blockFaces distinguishes this absolute face from the relative connection names.
      weakPower = { data = "output", sides = { betamoon.mc.blockFaces.south } },
      connections = { "north", "south" },
      onNeighborChanged = {
        action = function(ctx)
          local data = ctx.entity.data
          local wasPowered = data:get("inputWasPowered")

          -- The key is checked but never consumed. Emptying the slot safely
          -- disarms new pulses without erasing the saved output or count.
          local armed = ctx.entity.inventory:get("controlKey") ~= nil
          if armed and ctx.powered and not wasPowered then
            local output = not data:get("output")
            data:set("output", output)
            data:set("pulseCount", data:get("pulseCount") + 1)
            ctx.world:notifyNeighbors()

            betamoon.chat:send(
              "Controller pulse %i: output %s",
              data:get("pulseCount"),
              output and "on" or "off"
            )
          end

          data:set("inputWasPowered", ctx.powered)
        end
      }
    }
  }

  betamoon.recipes:add {
    type = "shaped",
    output = betamoon.stack(controller),
    pattern = { " C", "DR" },
    ingredients = {
      C = betamoon.blocks:getRequired(4),
      D = betamoon.items:getRequired(331),
      R = betamoon.items:getRequired(356)
    }
  }
end

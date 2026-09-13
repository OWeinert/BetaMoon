-- Load 02a and 02b with this file. Craft a chest surrounded by planks, place the
-- storage block, and right-click it. Items live in a real saved tile inventory;
-- the occupied counter is recomputed whenever that inventory changes.
-- Structural content cannot hot reload, so restart Minecraft after editing this file.

name = "Basic Storage Block Example"
version = "1.0.0"
description = "Combines a saved tile inventory, container, GUI, and block into a small storage machine."
dependencies = { "Basic Storage Data Example", "Basic Storage Layout Example" }

function modInit()
  local dataDefinition = betamoon.modules:import("basic_storage_data")
  local layoutDefinition = betamoon.modules:import("basic_storage_layout")

  -- The three structural handles and their block are deliberately registered
  -- together in this modInit. Imported modules only provide plain declarations,
  -- so all structural resources have one clear owner.
  local tileEntity = betamoon.tileEntities:add {
    name = "basic_storage",
    inventory = dataDefinition.inventory,
    data = dataDefinition.data,
    onInventoryChanged = {
      action = function(ctx)
        local occupied = 0
        for index = 1, 9 do
          if ctx.entity.inventory:get("storage_" .. index) then
            occupied = occupied + 1
          end
        end
        ctx.entity.data:set("occupied", occupied)
      end
    }
  }

  local container = betamoon.containers:add {
    name = "basic_storage",
    tileEntity = tileEntity,
    slots = layoutDefinition.containerSlots,
    playerInventory = layoutDefinition.playerInventory
  }

  local guiDefinition = layoutDefinition.gui
  local gui = betamoon.containerGuis:add {
    name = "basic_storage",
    container = container,
    layout = guiDefinition.layout,
    background = guiDefinition.background,
    elements = guiDefinition.elements
  }

  local storage = betamoon.blocks:add {
    id = 224,
    key = "basic_storage",
    displayName = "Basic Storage",
    -- blockMaterials and stepSounds keep the two native block identifiers canonical.
    material = betamoon.mc.blockMaterials.wood,
    hardness = 2.5,
    resistance = 5,
    stepSound = betamoon.mc.stepSounds.wood,
    textures = { top = 25, bottom = 25, sides = 26, front = 27 },
    tileEntity = tileEntity,
    container = container,
    gui = gui,
    piston = { reaction = "block" },
    drops = { { item = 224, damage = 0 } }
  }

  betamoon.recipes:add {
    type = "shaped",
    output = betamoon.stack(storage),
    pattern = { "PPP", "PCP", "PPP" },
    ingredients = {
      P = betamoon.blocks:getRequired(5),
      C = betamoon.blocks:getRequired(54)
    }
  }
end

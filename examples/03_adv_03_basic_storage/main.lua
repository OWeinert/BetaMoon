-- This manifested package keeps persistent data and GUI layout declarations in
-- private modules. Craft a chest surrounded by planks, place the storage block,
-- and right-click it. Structural changes require a Minecraft restart.

function modInit()
  local dataDefinition = require("data")
  local layoutDefinition = require("layout")

  -- The three structural handles and their block are deliberately registered
  -- together in this modInit. Private modules only provide plain declarations,
  -- so all structural resources have one clear owner.
  local tileEntity = betamoon.tileEntities:add {
    name = "example:block/basic_storage",
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
    name = "example:block/basic_storage",
    tileEntity = tileEntity,
    slots = layoutDefinition.containerSlots,
    playerInventory = layoutDefinition.playerInventory
  }

  local guiDefinition = layoutDefinition.gui
  local gui = betamoon.containerGuis:add {
    name = "example:block/basic_storage",
    container = container,
    layout = guiDefinition.layout,
    background = guiDefinition.background,
    elements = guiDefinition.elements
  }

  local storage = betamoon.blocks:add {
    id = 224,
    key = "example:block/basic_storage",
    displayName = "Basic Storage",
    -- blockMaterials and stepSounds keep the two native block identifiers canonical.
    material = betamoon.mc.blockMaterials.wood,
    harvest = { axe = 0 },
    hardness = 2.5,
    resistance = 5,
    stepSound = betamoon.mc.stepSounds.wood,
    textures = { top = 25, bottom = 25, sides = 26 },
    state = {
      facing = { type = "enum", values = { "north", "east", "south", "west" } }
    },
    placement = { facing = "horizontal", facingFrom = "player" },
    render = {
      variants = {
        [0] = { textures = { north = 27 } },
        [1] = { textures = { east = 27 } },
        [2] = { textures = { south = 27 } },
        [3] = { textures = { west = 27 } }
      }
    },
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

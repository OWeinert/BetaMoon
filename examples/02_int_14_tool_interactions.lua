-- This file creates its own practice block, so the wrench demo needs no other scripts.
-- Craft cobblestone + iron for the block, and iron + stick for the wrench.
-- Right-click the block with the wrench; sneak to rotate in the opposite direction.
-- Craft the wrench with another iron ingot for a multi-tool, then compare stone and dirt mining.
-- The two items demonstrate different approaches: an action callback and declarative tool rules.

name = "Tool Interactions Example"
version = "1.0.0"
description = "Adds Wrench Practice Block, Example Wrench, and Example Multi-tool. All three recipes are " ..
    "shapeless: cobblestone plus an iron ingot makes the practice block, iron plus a stick makes " ..
    "the wrench, and a wrench plus another iron ingot makes the multi-tool.\n\n" ..
    "Place the practice block and identify its furnace-textured front. Right-click it with the " ..
    "wrench to rotate clockwise, or sneak-right-click to rotate counterclockwise. A successful " ..
    "rotation costs one durability point. The wrench also attempts to rotate other blocks with a " ..
    "supported facing; unsuccessful rotations do not spend durability.\n\n" ..
    "Craft a separate wrench if you want to keep one before making the multi-tool. Test the " ..
    "multi-tool on stone and dirt: it combines iron-level pickaxe and shovel harvesting, spending " ..
    "one durability per mined block and two per entity hit. Compare the wrench's action callback " ..
    "with the multi-tool's declarative tool settings."

function modInit()
  local practice = betamoon.blocks:add {
    id = 211,
    key = "example:block/wrench_practice_block",
    displayName = "Wrench Practice Block",
    -- blockMaterials keeps the native material name explicit and checked.
    material = betamoon.mc.blockMaterials.rock,
    harvest = { pickaxe = 0 },
    hardness = 1,
    texture = 1,
    state = {
      facing = { type = "enum", values = { "north", "east", "south", "west" } }
    },
    -- The facing enum is saved in metadata and initialized from the player.
    -- Its four values map to 0..3, which the render variants below use.
    -- Changing their order later would reinterpret orientations in existing saves.
    placement = { facing = "horizontal", facingFrom = "player" },
    render = {
      variants = {
        [0] = { textures = { north = 61 } },
        [1] = { textures = { east = 61 } },
        [2] = { textures = { south = 61 } },
        [3] = { textures = { west = 61 } }
      }
    }
  }

  local wrench = betamoon.items:add {
    id = 5021,
    key = "example:item/example_wrench",
    displayName = "Example Wrench",
    icon = { x = 2, y = 6 },
    maxStackSize = 1,
    maxDamage = 128,
    full3D = true,
    onUseFirst = {
      action = function(ctx)
        -- This runs before the target block opens a GUI or activates.
        local target = ctx.target.block
        if not target then
          return betamoon.callbackResults.pass
        end
        local direction = ctx.player and ctx.player:isSneaking() and "counterclockwise" or "clockwise"
        -- rotate returns false when the targeted block has no supported facing.
        -- Passing then preserves normal interaction with unrelated blocks.
        if not target:rotate(direction) then
          return betamoon.callbackResults.pass
        end
        -- This changes the actual held stack. Charge durability only after a
        -- successful rotation so unsuccessful clicks do not wear the wrench.
        ctx.stack:damage(1)
        return betamoon.callbackResults.handled
      end
    }
  }

  local multiTool = betamoon.items:add {
    id = 5022,
    key = "example:item/example_multi_tool",
    displayName = "Example Multi-tool",
    icon = { x = 2, y = 6 },
    maxStackSize = 1,
    maxDamage = 250,
    full3D = true,
    tool = {
      -- Harvest level, effective mining speed and durability cost are separate:
      -- classes says what it can harvest; efficiency affects suitable blocks;
      -- durabilityCost decides how much wear successful mining/hitting causes.
      classes = { pickaxe = 2, shovel = 2 },
      efficiency = 6,
      durabilityCost = { mine = 1, hit = 2 }
    }
  }

  -- Practice block: cobblestone + iron. Wrench: iron + stick.
  -- Multi-tool: wrench + another iron ingot.
  local iron = betamoon.items:getRequired(265)
  betamoon.recipes:add {
    type = "shapeless", output = betamoon.stack(practice),
    ingredients = { betamoon.blocks:getRequired(4), iron }
  }
  betamoon.recipes:add {
    type = "shapeless", output = betamoon.stack(wrench),
    ingredients = { iron, betamoon.items:getRequired(280) }
  }
  betamoon.recipes:add {
    type = "shapeless", output = betamoon.stack(multiTool),
    ingredients = { wrench, iron }
  }
end

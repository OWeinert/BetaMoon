-- Craft obsidian + a lapis block for a Flat Dimension Anchor, and gold + diamond
-- for a reusable Flat Dimension Key. Use the key on the anchor to travel.
-- The generated anchor at (0, 3, 0) is already present before arrival checks.
-- This example requires singleplayer and the BetaMoon Java agent.

name = "Flat Dimension Example"
version = "1.0.0"
description = "Adds Flat Dimension, a classic superflat world with bedrock, two layers of dirt and grass. " ..
    "Craft obsidian + a lapis block into a Flat Dimension Anchor (247), place it, and use a Flat Dimension Key " ..
    "(5051, gold ingot + diamond) on it. The key is never consumed and has no durability or damage variants.\n\n" ..
    "You arrive standing on another anchor at the dimension's spawn. Look down and use the same key on " ..
    "that anchor to return to your departure point. The generated return anchor cannot be mined in survival. " ..
    "Keep the two blocks above it clear. Return history survives saving and reopening the world."

local ANCHOR_ID = 247
local KEY_ID = 5051
local DIMENSION_KEY = "example:dimension/flat_dimension"
local RETURN_CHANNEL = "flat_dimension"

function modInit()
  local results = betamoon.callbackResults
  local anchor = betamoon.blocks:add {
    id = ANCHOR_ID,
    key = "example:block/flat_dimension_anchor",
    displayName = "Flat Dimension Anchor",
    material = betamoon.mc.blockMaterials.rock,
    texture = 144, -- Minecraft's lapis block texture; no extra assets.
    hardness = 3,
    resistance = 6000000,
    harvest = { pickaxe = 0 },
    light = 7,
    canBreak = {
      action = function(ctx)
        -- Other anchors remain ordinary mineable blocks. Protect the generated
        -- return point, whose block coordinates differ from the player's feet Y.
        return ctx.world:getDimensionKey() ~= DIMENSION_KEY
            or ctx.x ~= 0 or ctx.y ~= 3 or ctx.z ~= 0
      end
    },
    onActivate = {
      action = function(ctx)
        betamoon.chat:send("Use a Flat Dimension Key on this anchor to travel.")
        return results.handled
      end
    }
  }

  local worldgen = betamoon.worldgen
  local plains = worldgen.biomes:add {
    key = "example:biome/flat_dimension_plains",
    displayName = "Flat Plains",
    basedOn = betamoon.mc.world.biomes.plains
  }
  local biomeSource = worldgen.biomeSources:add {
    key = "example:biome_source/flat_dimension",
    type = "fixed",
    biome = plains
  }

  local terrain = worldgen.terrain.sources:add {
    key = "example:terrain_source/flat_dimension",
    type = "compose",
    children = {
      {
        source = {
          type = "flat",
          layers = {
            { block = 7, depth = 1 }, -- Bedrock at Y=0.
            { block = 3, depth = 2 }, -- Dirt at Y=1 and Y=2.
            { block = 2, depth = 1 }  -- Grass at Y=3; feet stand at Y=4.
          }
        }
      },
      {
        -- Union ignores air, so only the single anchor replaces the grass.
        -- The mask is zero only in the world column X=0, Z=0.
        operation = "union",
        mask = { type = "max", inputs = {
          { type = "abs", input = { type = "x" } },
          { type = "abs", input = { type = "z" } }
        } },
        min = 0,
        max = 0,
        source = {
          type = "flat",
          layers = { { block = 0, depth = 3 }, { block = ANCHOR_ID, depth = 1 } }
        }
      }
    }
  }
  local profile = worldgen.generatorProfiles:add {
    key = "example:generator/flat_dimension",
    displayName = "Flat Dimension",
    dimensions = { overworld = {
      terrain = terrain,
      biomeSource = biomeSource,
      surfaces = "none",
      carvers = {},
      population = {
        native = "none",
        betamoon = {
          undergroundStructures = false, undergroundFeatures = false,
          surfaceStructures = false, surfaceFeatures = false,
          vegetation = false, entities = false, postProcess = false
        }
      }
    } }
  }
  local dimension = betamoon.dimensions:add {
    key = DIMENSION_KEY,
    displayName = "Flat Dimension",
    generator = { type = "profile", profile = profile },
    spawn = { type = "fixed", x = 0.5, y = 4, z = 0.5 },
    respawn = { type = "dimension", dimension = "minecraft:overworld" }
  }

  local function completed(result)
    -- The result is fresh; never retain the item callback's ctx/player/world.
    if not result.success and result.reason ~= "cancelled" then
      betamoon.chat:send("Teleport failed: %s", result.reason)
    end
  end

  local key = betamoon.items:add {
    id = KEY_ID,
    key = "example:item/flat_dimension_key",
    displayName = "Flat Dimension Key",
    icon = { x = 6, y = 3 },
    maxStackSize = 1,
    maxDamage = 0,
    hasSubtypes = false,
    use = { consume = 0, cooldown = 20 },
    onUseFirst = {
      -- Run before the anchor's ordinary activation/hint callback.
      action = function(ctx)
        if not ctx.player or ctx.target.kind ~= "block" or ctx.target.block.id ~= ANCHOR_ID then
          return results.pass
        end

        local ticket, reason
        if ctx.world:getDimensionKey() == DIMENSION_KEY then
          ticket, reason = betamoon.teleport:returnToPrevious(ctx.player, {
            channel = RETURN_CHANNEL,
            fallback = "dimension_spawn",
            onComplete = completed
          })
          if not ticket and reason == "no_return_point" then
            -- An externally entered dimension can still be left with this key.
            ticket, reason = betamoon.teleport:request(ctx.player, "minecraft:overworld", {
              rememberReturn = false,
              channel = RETURN_CHANNEL,
              onComplete = completed
            })
          end
        else
          ticket, reason = betamoon.teleport:request(ctx.player, dimension, {
            arrival = { type = "exact", x = 0.5, y = 4, z = 0.5 },
            rememberReturn = true,
            channel = RETURN_CHANNEL,
            onComplete = completed
          })
        end

        if not ticket then
          if reason ~= "busy" and reason ~= "cooldown" then
            betamoon.chat:send("Cannot teleport: %s", reason)
          end
          return results.deny
        end
        return results.handled
      end
    }
  }

  betamoon.recipes:add {
    type = "shapeless",
    output = betamoon.stack(anchor),
    ingredients = { betamoon.blocks:getRequired(49), betamoon.blocks:getRequired(22) }
  }
  betamoon.recipes:add {
    type = "shapeless",
    output = betamoon.stack(key),
    ingredients = { betamoon.items:getRequired(266), betamoon.items:getRequired(264) }
  }
end

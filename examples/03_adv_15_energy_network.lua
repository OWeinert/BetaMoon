-- A complete capability-backed electricity example. Place generators, cables,
-- batteries, and consumers face-to-face. The logical network discovers connected
-- components; Lua decides how energy is generated, stored, and distributed.

name = "Energy Network Example"
version = "1.0.0"
description = "Adds a generator, cable, battery, and consumer connected by an adjacent logical network. " ..
    "The battery GUI shows persistent capability energy. Distribution first simulates both endpoints, then commits " ..
    "the exact accepted amount. Place blocks face-to-face and inspect the battery after a few seconds."

function modInit()
  local energy = betamoon.capabilities:add {
    key = "example:capability/energy",
    state = {
      stored = { type = "integer", default = 0 }
    },
    config = {
      kind = { type = "string" },
      capacity = { type = "integer" }
    },
    operations = {
      status = {
        mode = "query",
        response = {
          stored = { type = "integer" },
          capacity = { type = "integer" }
        }
      },
      receive = {
        mode = "action",
        request = {
          amount = { type = "integer" },
          simulate = { type = "boolean", default = false }
        },
        response = { accepted = { type = "integer" } }
      },
      extract = {
        mode = "action",
        request = {
          amount = { type = "integer" },
          simulate = { type = "boolean", default = false }
        },
        response = { extracted = { type = "integer" } }
      }
    }
  }

  local function operations(canReceive, canExtract)
    return {
      status = function(ctx)
        return {
          stored = ctx.capability.data:get("stored"),
          capacity = ctx.capability.config.capacity
        }
      end,
      receive = function(ctx, request)
        if request.amount < 0 then error("Energy amount cannot be negative") end
        if not canReceive then return { accepted = 0 } end
        local stored = ctx.capability.data:get("stored")
        local accepted = math.min(request.amount, ctx.capability.config.capacity - stored)
        if not request.simulate then ctx.capability.data:set("stored", stored + accepted) end
        return { accepted = accepted }
      end,
      extract = function(ctx, request)
        if request.amount < 0 then error("Energy amount cannot be negative") end
        if not canExtract then return { extracted = 0 } end
        local stored = ctx.capability.data:get("stored")
        local extracted = math.min(request.amount, stored)
        if not request.simulate then ctx.capability.data:set("stored", stored - extracted) end
        return { extracted = extracted }
      end
    }
  end

  local allFaces = {
    north = "energy", south = "energy", east = "energy", west = "energy",
    up = "energy", down = "energy"
  }

  local function tile(key, kind, capacity, receive, extract, tick)
    return betamoon.tileEntities:add {
      name = key,
      data = {
        displayEnergy = { type = "integer", default = 0, sync = true }
      },
      capabilities = {{
        capability = energy,
        config = { kind = kind, capacity = capacity },
        ports = allFaces,
        operations = operations(receive, extract)
      }},
      onTick = tick and {
        mode = "continuous",
        action = tick
      } or nil
    }
  end

  local generatorTile = tile("example:block/energy_generator", "producer", 1000, false, true, function(ctx)
    local capability = ctx.entity.capabilities:getRequired(energy)
    local stored = capability.data:get("stored")
    capability.data:set("stored", math.min(capability.config.capacity, stored + 4))
    ctx.entity.data:set("displayEnergy", capability.data:get("stored"))
  end)
  local cableTile = tile("example:block/energy_cable", "cable", 0, false, false)
  local batteryTile = tile("example:block/energy_battery", "storage", 4000, true, true, function(ctx)
    local capability = ctx.entity.capabilities:getRequired(energy)
    ctx.entity.data:set("displayEnergy", capability.data:get("stored"))
  end)
  local consumerTile = tile("example:block/energy_consumer", "consumer", 200, true, false, function(ctx)
    local capability = ctx.entity.capabilities:getRequired(energy)
    local stored = capability.data:get("stored")
    if stored > 0 then capability.data:set("stored", stored - 1) end
    ctx.entity.data:set("displayEnergy", capability.data:get("stored"))
  end)

  betamoon.logicalNetworks:add {
    key = "example:network/electricity",
    capability = energy,
    topology = { type = "adjacent", directions = "orthogonal" },
    tick = { interval = 1 },
    onTick = function(ctx)
      local producers = ctx.nodes:find { config = { kind = "producer" } }
      local storage = ctx.nodes:find { config = { kind = "storage" } }
      local consumers = ctx.nodes:find { config = { kind = "consumer" } }
      local sources = {}
      for _, node in ipairs(producers) do sources[#sources + 1] = node end
      for _, node in ipairs(storage) do sources[#sources + 1] = node end

      for _, consumer in ipairs(consumers) do
        local status = consumer:call("status", {})
        local needed = status.capacity - status.stored
        for _, source in ipairs(sources) do
          if needed <= 0 then break end
          local available = source:call("extract", { amount = needed, simulate = true }).extracted
          local accepted = consumer:call("receive", { amount = available, simulate = true }).accepted
          assert(type(available) == "number" and type(accepted) == "number")
          local moved = math.min(available, accepted)
          if moved > 0 then
            source:call("extract", { amount = moved, simulate = false })
            consumer:call("receive", { amount = moved, simulate = false })
            needed = needed - moved
          end
        end
      end
    end
  }

  local batteryContainer = betamoon.containers:add {
    name = "example:block/energy_battery",
    tileEntity = batteryTile,
    slots = {},
    playerInventory = { x = 8, y = 84, includeHotbar = true }
  }
  local batteryGui = betamoon.containerGuis:add {
    name = "example:block/energy_battery",
    container = batteryContainer,
    layout = { preset = betamoon.mc.gui.backgrounds.container, height = 168, title = "Energy Battery" },
    background = { style = "minecraft", drawSlotFrames = true },
    elements = {{ type = "text", value = "displayEnergy", format = "Stored energy: %d", x = 8, y = 60 }}
  }

  local function block(id, key, displayName, texture, tileEntity, container, gui)
    return betamoon.blocks:add {
      id = id, key = key, displayName = displayName,
      material = betamoon.mc.blockMaterials.rock,
      harvest = { pickaxe = 0 }, hardness = 1.5, resistance = 8,
      texture = texture, tileEntity = tileEntity, container = container, gui = gui,
      piston = { reaction = "block" }
    }
  end

  block(235, "example:block/energy_generator", "Energy Generator", 23, generatorTile)
  block(236, "example:block/energy_cable", "Energy Cable", 6, cableTile)
  block(237, "example:block/energy_battery", "Energy Battery", 22, batteryTile, batteryContainer, batteryGui)
  block(238, "example:block/energy_consumer", "Energy Consumer", 45, consumerTile)
end

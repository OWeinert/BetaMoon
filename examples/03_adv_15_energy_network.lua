-- A complete capability-backed electricity example. Place generators, cables,
-- batteries, and consumers face-to-face. The logical network discovers connected
-- components; Lua decides how energy is generated, stored, and distributed.

name = "Energy Network Example"
version = "1.0.0"
description = "Adds a generator, cable, battery, and consumer connected by an adjacent logical network. " ..
    "Right-click every block for its role, transfer limits, and live energy information. Generators produce " ..
    "four and can output up to eight energy per tick. A connected cable grid moves at most eight energy per tick. Consumers use " ..
    "one and accept at most two energy per tick; each face is an independent input and never bridges cable grids. " ..
    "Batteries accept and output at most two energy per tick. Energy serves the nearest consumers first, and consumers " ..
    "have priority over battery charging."

function modInit()
  local GENERATOR_RATE = 4
  local GENERATOR_OUTPUT_RATE = 8
  local CABLE_RATE = 8
  local CONSUMER_USE_RATE = 1
  local MACHINE_INPUT_RATE = 2
  local BATTERY_OUTPUT_RATE = 2

  local energy = betamoon.capabilities:add {
    key = "example:capability/energy",
    state = {
      stored = { type = "integer", default = 0 },
      inputTick = { type = "number", default = -1 },
      inputUsed = { type = "integer", default = 0 },
      outputTick = { type = "number", default = -1 },
      outputUsed = { type = "integer", default = 0 }
    },
    config = {
      kind = { type = "string" },
      capacity = { type = "integer" },
      maxInput = { type = "integer" },
      maxOutput = { type = "integer" },
      maxTransfer = { type = "integer" }
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

  local function remainingRate(ctx, limitField, tickField, usedField)
    local tick = ctx.world:getInfo().worldTime
    local used = ctx.capability.data:get(tickField) == tick and ctx.capability.data:get(usedField) or 0
    return tick, used, math.max(0, ctx.capability.config[limitField] - used)
  end

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
        local tick, used, remaining = remainingRate(ctx, "maxInput", "inputTick", "inputUsed")
        local accepted = math.min(request.amount, ctx.capability.config.capacity - stored, remaining)
        if not request.simulate then
          local updated = stored + accepted
          ctx.capability.data:set("stored", updated)
          ctx.capability.data:set("inputTick", tick)
          ctx.capability.data:set("inputUsed", used + accepted)
          ctx.entity.data:set("displayEnergy", updated)
        end
        return { accepted = accepted }
      end,
      extract = function(ctx, request)
        if request.amount < 0 then error("Energy amount cannot be negative") end
        if not canExtract then return { extracted = 0 } end
        local stored = ctx.capability.data:get("stored")
        local tick, used, remaining = remainingRate(ctx, "maxOutput", "outputTick", "outputUsed")
        local extracted = math.min(request.amount, stored, remaining)
        if not request.simulate then
          local updated = stored - extracted
          ctx.capability.data:set("stored", updated)
          ctx.capability.data:set("outputTick", tick)
          ctx.capability.data:set("outputUsed", used + extracted)
          ctx.entity.data:set("displayEnergy", updated)
        end
        return { extracted = extracted }
      end
    }
  end

  local allFaces = {
    north = "energy", south = "energy", east = "energy", west = "energy",
    up = "energy", down = "energy"
  }
  local separateFaces = {
    north = "north_input", south = "south_input", east = "east_input", west = "west_input",
    up = "up_input", down = "down_input"
  }

  local function tile(key, config, ports, receive, extract, tick)
    return betamoon.tileEntities:add {
      name = key,
      data = {
        displayEnergy = { type = "integer", default = 0, sync = true }
      },
      capabilities = {{
        capability = energy,
        config = config,
        ports = ports,
        operations = operations(receive, extract)
      }},
      onTick = tick and {
        mode = "continuous",
        action = tick
      } or nil
    }
  end

  local generatorTile = tile("example:block/energy_generator", {
    kind = "producer", capacity = 1000,
    maxInput = 0, maxOutput = GENERATOR_OUTPUT_RATE, maxTransfer = 0
  }, allFaces, false, true, function(ctx)
    local capability = ctx.entity.capabilities:getRequired(energy)
    local stored = capability.data:get("stored")
    capability.data:set("stored", math.min(capability.config.capacity, stored + GENERATOR_RATE))
    ctx.entity.data:set("displayEnergy", capability.data:get("stored"))
  end)
  local cableTile = tile("example:block/energy_cable", {
    kind = "cable", capacity = 0,
    maxInput = 0, maxOutput = 0, maxTransfer = CABLE_RATE
  }, allFaces, false, false)
  local batteryTile = tile("example:block/energy_battery", {
    kind = "storage", capacity = 4000,
    maxInput = MACHINE_INPUT_RATE, maxOutput = BATTERY_OUTPUT_RATE, maxTransfer = 0
  }, allFaces, true, true, function(ctx)
    local capability = ctx.entity.capabilities:getRequired(energy)
    ctx.entity.data:set("displayEnergy", capability.data:get("stored"))
  end)
  local consumerTile = tile("example:block/energy_consumer", {
    kind = "consumer", capacity = 200,
    maxInput = MACHINE_INPUT_RATE, maxOutput = 0, maxTransfer = 0
  }, separateFaces, true, false, function(ctx)
    local capability = ctx.entity.capabilities:getRequired(energy)
    local stored = capability.data:get("stored")
    if stored > 0 then capability.data:set("stored", stored - CONSUMER_USE_RATE) end
    ctx.entity.data:set("displayEnergy", capability.data:get("stored"))
  end)

  betamoon.logicalNetworks:add {
    key = "example:network/electricity",
    capability = energy,
    topology = { type = "adjacent", directions = "orthogonal" },
    tick = { interval = 1 },
    -- Consumers are endpoints rather than conductors. The callback discovers
    -- them from each adjacent cable/source component and sends energy through
    -- that one face, so a consumer never joins cable grids on opposite sides.
    canConnect = function(ctx)
      return ctx.first.config.kind ~= "consumer" and ctx.second.config.kind ~= "consumer"
    end,
    onTick = function(ctx)
      local producers = ctx.nodes:find { config = { kind = "producer" } }
      local batteries = ctx.nodes:find { config = { kind = "storage" } }
      local cables = ctx.nodes:find { config = { kind = "cable" } }
      local consumers = {}
      local seenConsumers = {}
      local directions = {
        { 0, -1, 0, "up" }, { 0, 1, 0, "down" },
        { 0, 0, -1, "south" }, { 0, 0, 1, "north" },
        { -1, 0, 0, "east" }, { 1, 0, 0, "west" }
      }
      for _, node in ipairs(ctx.nodes) do
        if node.config.kind ~= "consumer" then
          for _, direction in ipairs(directions) do
            local position = {
              x = node.position.x + direction[1],
              y = node.position.y + direction[2],
              z = node.position.z + direction[3]
            }
            local target = ctx.world:getCapability(position, energy, direction[4])
            if target and target.config.kind == "consumer" then
              local key = position.x .. "," .. position.y .. "," .. position.z
              local consumer = seenConsumers[key]
              if not consumer then
                consumer = { capability = target, attachments = {} }
                seenConsumers[key] = consumer
                consumers[#consumers + 1] = consumer
              end
              consumer.attachments[#consumer.attachments + 1] = node
            end
          end
        end
      end

      local cableRemaining = nil
      for _, cable in ipairs(cables) do
        cableRemaining = math.min(cableRemaining or cable.config.maxTransfer, cable.config.maxTransfer)
      end

      local function transfer(source, destination, requested)
        if requested <= 0 then return 0 end
        if cableRemaining then
          if cableRemaining <= 0 then return 0 end
          requested = math.min(requested, cableRemaining)
        end
        local available = source:call("extract", { amount = requested, simulate = true }).extracted
        local accepted = destination:call("receive", { amount = available, simulate = true }).accepted
        assert(type(available) == "number" and type(accepted) == "number")
        local moved = math.min(available, accepted)
        if moved > 0 then
          local extracted = source:call("extract", { amount = moved, simulate = false }).extracted
          local accepted = destination:call("receive", { amount = moved, simulate = false }).accepted
          assert(extracted == moved and accepted == moved)
          if cableRemaining then cableRemaining = cableRemaining - moved end
        end
        return moved
      end

      local function position(value)
        return (value.capability or value).position
      end

      local function positionLess(first, second)
        local a, b = position(first), position(second)
        if a.x ~= b.x then return a.x < b.x end
        if a.y ~= b.y then return a.y < b.y end
        return a.z < b.z
      end

      -- Boundary consumers are one edge beyond their attachment node. A consumer
      -- touching the same grid on multiple faces uses its shortest actual path.
      local function distance(source, destination)
        if not destination.attachments then
          return ctx.network:pathDistance(source, destination)
        end
        local nearest = math.huge
        for _, attachment in ipairs(destination.attachments) do
          nearest = math.min(nearest, ctx.network:pathDistance(source, attachment) + 1)
        end
        return nearest
      end

      local function ordered(sourceList, destination)
        local result = {}
        for _, source in ipairs(sourceList) do result[#result + 1] = source end
        table.sort(result, function(first, second)
          local firstDistance = distance(first, destination)
          local secondDistance = distance(second, destination)
          if firstDistance ~= secondDistance then return firstDistance < secondDistance end
          return positionLess(first, second)
        end)
        return result
      end

      -- Generators establish load priority while present. A battery-only grid uses
      -- the nearest battery instead. Position is only an equal-distance tie-breaker.
      local prioritySources = #producers > 0 and producers or batteries
      table.sort(consumers, function(first, second)
        local firstDistance, secondDistance = math.huge, math.huge
        for _, source in ipairs(prioritySources) do
          firstDistance = math.min(firstDistance, distance(source, first))
          secondDistance = math.min(secondDistance, distance(source, second))
        end
        if firstDistance ~= secondDistance then return firstDistance < secondDistance end
        return positionLess(first, second)
      end)

      local function supply(destination, requested)
        local moved = 0
        for _, sourceList in ipairs({ producers, batteries }) do
          for _, source in ipairs(ordered(sourceList, destination)) do
            if moved >= requested then break end
            moved = moved + transfer(source, destination.capability or destination, requested - moved)
          end
          if moved >= requested then break end
        end
        return moved
      end

      -- Like water consumed along a pipe, nearer loads take their operating need
      -- first. Under sustained shortage, farther loads can remain unpowered.
      local operatingNeedsMet = true
      for _, consumer in ipairs(consumers) do
        local status = consumer.capability:call("status", {})
        local requested = math.min(CONSUMER_USE_RATE, status.capacity - status.stored)
        if supply(consumer, requested) < requested then operatingNeedsMet = false end
      end

      -- Only fill consumer buffers when every connected consumer received enough
      -- energy to operate this tick. The per-machine input cap still applies.
      if operatingNeedsMet then
        for _, consumer in ipairs(consumers) do
          local status = consumer.capability:call("status", {})
          supply(consumer, status.capacity - status.stored)
        end
      end

      -- Batteries only receive generator surplus after all operating needs were
      -- met. Storage never tries to charge other storage.
      if operatingNeedsMet then
        local orderedBatteries = {}
        for _, battery in ipairs(batteries) do orderedBatteries[#orderedBatteries + 1] = battery end
        table.sort(orderedBatteries, function(first, second)
          local firstDistance, secondDistance = math.huge, math.huge
          for _, producer in ipairs(producers) do
            firstDistance = math.min(firstDistance, distance(producer, first))
            secondDistance = math.min(secondDistance, distance(producer, second))
          end
          if firstDistance ~= secondDistance then return firstDistance < secondDistance end
          return positionLess(first, second)
        end)
        for _, battery in ipairs(orderedBatteries) do
          local status = battery:call("status", {})
          local needed = status.capacity - status.stored
          for _, producer in ipairs(ordered(producers, battery)) do
            if needed <= 0 then break end
            needed = needed - transfer(producer, battery, needed)
          end
        end
      end
    end
  }

  local function interface(key, title, tileEntity, elements)
    local container = betamoon.containers:add {
      name = key,
      tileEntity = tileEntity,
      slots = {},
      playerInventory = { x = 8, y = 116, includeHotbar = true }
    }
    local gui = betamoon.containerGuis:add {
      name = key,
      container = container,
      layout = {
        preset = betamoon.mc.gui.backgrounds.container,
        height = 200,
        title = title,
        playerInventoryLabel = { text = "Inventory", x = 8, y = 104 }
      },
      background = { style = "minecraft", drawSlotFrames = true },
      elements = elements
    }
    return container, gui
  end

  local generatorContainer, generatorGui = interface(
    "example:block/energy_generator", "Energy Generator", generatorTile, {
      { type = "text", text = "Role: generator", x = 8, y = 22 },
      { type = "text", value = "displayEnergy", format = "Stored: %d / 1000", x = 8, y = 34 },
      { type = "text", text = "Generates 4/tick", x = 8, y = 46 },
      { type = "text", text = "Output max: 8/tick", x = 8, y = 58 },
      { type = "text", text = "Consumers have priority", x = 8, y = 70 },
      { type = "text", text = "All faces share a network", x = 8, y = 82 }
    })
  local cableContainer, cableGui = interface(
    "example:block/energy_cable", "Energy Cable", cableTile, {
      { type = "text", text = "Role: connector", x = 8, y = 22 },
      { type = "text", text = "Stores no energy", x = 8, y = 34 },
      { type = "text", text = "Transfer max: 8/tick", x = 8, y = 46 },
      { type = "text", text = "Limit shared by cable grid", x = 8, y = 58 },
      { type = "text", text = "All faces are linked", x = 8, y = 70 },
      { type = "text", text = "Build one touching path", x = 8, y = 82 }
    })
  local batteryContainer, batteryGui = interface(
    "example:block/energy_battery", "Energy Battery", batteryTile, {
      { type = "text", text = "Role: storage", x = 8, y = 22 },
      { type = "text", value = "displayEnergy", format = "Stored: %d / 4000", x = 8, y = 34 },
      { type = "text", text = "Input max: 2/tick", x = 8, y = 46 },
      { type = "text", text = "Output max: 2/tick", x = 8, y = 58 },
      { type = "text", text = "Stores generator surplus", x = 8, y = 70 },
      { type = "text", text = "All faces share a network", x = 8, y = 82 }
    })
  local consumerContainer, consumerGui = interface(
    "example:block/energy_consumer", "Energy Consumer", consumerTile, {
      { type = "text", text = "Role: consumer", x = 8, y = 22 },
      { type = "text", value = "displayEnergy", format = "Stored: %d / 200", x = 8, y = 34 },
      { type = "text", text = "Uses 1 energy/tick", x = 8, y = 46 },
      { type = "text", text = "Input max: 2/tick", x = 8, y = 58 },
      { type = "text", text = "Nearest loads run first", x = 8, y = 70 },
      { type = "text", text = "Faces never link networks", x = 8, y = 82 }
    })

  local function block(id, key, displayName, texture, tileEntity, container, gui)
    return betamoon.blocks:add {
      id = id, key = key, displayName = displayName,
      material = betamoon.mc.blockMaterials.rock,
      harvest = { pickaxe = 0 }, hardness = 1.5, resistance = 8,
      texture = texture, tileEntity = tileEntity, container = container, gui = gui,
      piston = { reaction = "block" }
    }
  end

  block(235, "example:block/energy_generator", "Energy Generator", 23, generatorTile,
    generatorContainer, generatorGui)
  block(236, "example:block/energy_cable", "Energy Cable", 6, cableTile, cableContainer, cableGui)
  block(237, "example:block/energy_battery", "Energy Battery", 22, batteryTile, batteryContainer, batteryGui)
  block(238, "example:block/energy_consumer", "Energy Consumer", 45, consumerTile,
    consumerContainer, consumerGui)
end

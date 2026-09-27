-- Power the transmitter with redstone. Every loaded receiver on the same channel
-- mirrors its state and records rising-edge pulses without cables. Right-click
-- either block to edit its channel or restore the default "workshop" channel.

name = "Wireless Trigger Example"
version = "1.0.0"
description = "Adds a wireless transmitter and receiver. Power the transmitter with redstone; receivers on its " ..
    "channel emit redstone. Right-click either block to edit its channel. Rising edges are also delivered as " ..
    "bounded pulses and counted in persistent tile data."

function modInit()
  local wireless = betamoon.capabilities:add {
    key = "example:capability/wireless_trigger",
    config = {
      role = { type = "string" },
      owner = { type = "string" }
    },
    state = {
      channel = { type = "string", default = "workshop" }
    },
    operations = {
      setActive = {
        mode = "action",
        request = { active = { type = "boolean" } }
      },
      recordPulse = { mode = "action" }
    }
  }

  local function tile(key, role, operations, tick)
    return betamoon.tileEntities:add {
      name = key,
      data = {
        active = { type = "boolean", default = false },
        previous = { type = "boolean", default = false },
        pulses = { type = "integer", default = 0 },
        channel = { type = "string", default = "workshop" }
      },
      capabilities = {{
        capability = wireless,
        config = { role = role, owner = "example" },
        operations = operations
      }},
      onTick = { mode = "continuous", action = tick }
    }
  end

  local function updateChannel(ctx)
    local capability = ctx.entity.capabilities:getRequired(wireless)
    local channel = ctx.entity.data:get("channel")
    if capability.data:get("channel") ~= channel then
      capability.data:set("channel", channel)
    end
  end

  local transmitterTile = tile("example:block/wireless_transmitter", "transmitter", {
    setActive = function() end,
    recordPulse = function() end
  }, function(ctx)
    updateChannel(ctx)
    local powered = ctx.world:isPowered()
    local network = ctx.entity.networks:getRequired("example:network/wireless_trigger")
    network:publish(powered)
    if powered and not ctx.entity.data:get("previous") then network:pulse(true) end
    ctx.entity.data:set("previous", powered)
  end)

  local receiverTile = tile("example:block/wireless_receiver", "receiver", {
    setActive = function(ctx, request)
      if ctx.entity.data:get("active") ~= request.active then
        ctx.entity.data:set("active", request.active)
        ctx.world:notifyNeighbors()
      end
      return {}
    end,
    recordPulse = function(ctx)
      ctx.entity.data:set("pulses", ctx.entity.data:get("pulses") + 1)
      return {}
    end
  }, updateChannel)

  local function interface(key, title, tileEntity)
    local container = betamoon.containers:add {
      name = key,
      tileEntity = tileEntity,
      slots = {},
      playerInventory = { x = 8, y = 84, includeHotbar = true },
      controls = {
        channel = {
          type = "text",
          bind = { data = "channel" },
          maxLength = 24
        },
        resetChannel = {
          type = "action",
          onActivate = function(ctx)
            ctx.data:set("channel", "workshop")
          end
        }
      }
    }
    local gui = betamoon.containerGuis:add {
      name = key,
      container = container,
      layout = {
        preset = betamoon.mc.gui.backgrounds.container,
        title = title,
        playerInventoryLabel = { text = "Inventory", x = 8, y = 72 }
      },
      background = { style = "minecraft", drawSlotFrames = true },
      elements = {
        { type = "text", text = "Channel", x = 8, y = 22 },
        { type = "text_box", control = "channel", x = 8, y = 34, width = 108,
          tooltip = "Enter commits the channel; Escape restores the previous value" },
        { type = "button", control = "resetChannel", x = 120, y = 34, width = 48, text = "Default",
          tooltip = "Reset the channel to workshop" },
        { type = "text", text = "Matching channels connect within 64 blocks.", x = 8, y = 58 }
      }
    }
    return container, gui
  end

  local transmitterContainer, transmitterGui = interface(
    "example:block/wireless_transmitter", "Wireless Transmitter", transmitterTile)
  local receiverContainer, receiverGui = interface(
    "example:block/wireless_receiver", "Wireless Receiver", receiverTile)

  betamoon.logicalNetworks:add {
    key = "example:network/wireless_trigger",
    capability = wireless,
    topology = {
      type = "wireless", scope = "dimension", range = 64,
      channelField = "channel", roleField = "role", compatibilityFields = { "owner" }
    },
    signal = {
      mode = "both", value = { type = "boolean", default = false }, aggregate = "any"
    },
    tick = { interval = 1 },
    onTick = function(ctx)
      for _, receiver in ipairs(ctx.nodes:find { config = { role = "receiver" } }) do
        receiver:call("setActive", { active = ctx.signal or false })
      end
    end,
    onPulse = function(ctx)
      for _, receiver in ipairs(ctx.nodes:find { config = { role = "receiver" } }) do
        receiver:call("recordPulse", {})
      end
    end
  }

  betamoon.blocks:add {
    id = 239, key = "example:block/wireless_transmitter", displayName = "Wireless Transmitter",
    material = betamoon.mc.blockMaterials.rock, harvest = { pickaxe = 0 }, hardness = 1.5,
    texture = 42, tileEntity = transmitterTile, container = transmitterContainer, gui = transmitterGui,
    piston = { reaction = "block" }
  }
  betamoon.blocks:add {
    id = 240, key = "example:block/wireless_receiver", displayName = "Wireless Receiver",
    material = betamoon.mc.blockMaterials.rock, harvest = { pickaxe = 0 }, hardness = 1.5,
    texture = 41, tileEntity = receiverTile, container = receiverContainer, gui = receiverGui,
    piston = { reaction = "block" },
    redstone = { weakPower = { data = "active" } }
  }
end

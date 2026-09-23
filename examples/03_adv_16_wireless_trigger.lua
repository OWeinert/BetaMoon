-- Power the transmitter with redstone. Every loaded receiver on the same channel
-- mirrors its state and records rising-edge pulses without cables.

name = "Wireless Trigger Example"
version = "1.0.0"
description = "Adds a wireless transmitter and receiver. Power the transmitter with redstone; receivers on its " ..
    "channel emit redstone. Rising edges are also delivered as bounded pulses and counted in persistent tile data."

function modInit()
  local wireless = betamoon.capabilities:add {
    key = "example:capability/wireless_trigger",
    config = {
      role = { type = "string" },
      channel = { type = "string" },
      owner = { type = "string" }
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
        pulses = { type = "integer", default = 0 }
      },
      capabilities = {{
        capability = wireless,
        config = { role = role, channel = "workshop", owner = "example" },
        operations = operations
      }},
      onTick = tick and { mode = "continuous", action = tick } or nil
    }
  end

  local transmitterTile = tile("example:block/wireless_transmitter", "transmitter", {
    setActive = function() end,
    recordPulse = function() end
  }, function(ctx)
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
  })

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
    texture = 42, tileEntity = transmitterTile, piston = { reaction = "block" }
  }
  betamoon.blocks:add {
    id = 240, key = "example:block/wireless_receiver", displayName = "Wireless Receiver",
    material = betamoon.mc.blockMaterials.rock, harvest = { pickaxe = 0 }, hardness = 1.5,
    texture = 41, tileEntity = receiverTile, piston = { reaction = "block" },
    redstone = { weakPower = { data = "active" } }
  }
end

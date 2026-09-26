-- Build two local segments: Input -> Gateway, then Gateway -> Output. The two
-- gateways may be separated by up to 128 blocks and bridge the adjacent segments.

name = "Hybrid Network Example"
version = "1.0.0"
description = "Adds a hybrid trigger network. Place an input beside one gateway and an output beside another. " ..
    "The gateways connect wirelessly while the input and output remain local to their adjacent segment."

function modInit()
  local trigger = betamoon.capabilities:add {
    key = "example:capability/hybrid_trigger",
    config = {
      role = { type = "string" },
      channel = { type = "string" },
      kind = { type = "string" }
    },
    operations = {
      setOutput = { mode = "action", request = { active = { type = "boolean" } } }
    }
  }

  local function tile(key, role, channel, kind, operation, tick)
    return betamoon.tileEntities:add {
      name = key,
      data = { active = { type = "boolean", default = false } },
      capabilities = {{
        capability = trigger,
        config = { role = role, channel = channel, kind = kind },
        ports = { north = "trigger", south = "trigger", east = "trigger", west = "trigger" },
        operations = { setOutput = operation }
      }},
      onTick = tick and { mode = "continuous", action = tick } or nil
    }
  end

  local noOutput = function() return {} end
  local inputTile = tile("example:block/hybrid_input", "transmitter", "local-input", "input", noOutput,
    function(ctx)
      ctx.entity.networks:getRequired("example:network/hybrid_trigger"):publish(ctx.world:isPowered())
    end)
  local gatewayTile = tile("example:block/hybrid_gateway", "transceiver", "bridge", "gateway", noOutput)
  local outputTile = tile("example:block/hybrid_output", "receiver", "local-output", "output",
    function(ctx, request)
      if ctx.entity.data:get("active") ~= request.active then
        ctx.entity.data:set("active", request.active)
        ctx.world:notifyNeighbors()
      end
      return {}
    end)

  betamoon.logicalNetworks:add {
    key = "example:network/hybrid_trigger",
    capability = trigger,
    topology = {
      type = "hybrid", directions = "orthogonal", scope = "dimension", range = 128,
      channelField = "channel", roleField = "role"
    },
    signal = { mode = "state", value = { type = "boolean", default = false }, aggregate = "any" },
    tick = { interval = 1 },
    onTick = function(ctx)
      for _, output in ipairs(ctx.nodes:find { config = { kind = "output" } }) do
        output:call("setOutput", { active = ctx.signal or false })
      end
    end
  }

  local function block(id, key, displayName, texture, tileEntity, redstone)
    betamoon.blocks:add {
      id = id, key = key, displayName = displayName,
      material = betamoon.mc.blockMaterials.rock, harvest = { pickaxe = 0 }, hardness = 1.5,
      texture = texture, tileEntity = tileEntity, piston = { reaction = "block" }, redstone = redstone
    }
  end
  block(241, "example:block/hybrid_input", "Hybrid Input", 42, inputTile)
  block(242, "example:block/hybrid_gateway", "Hybrid Gateway", 22, gatewayTile)
  block(243, "example:block/hybrid_output", "Hybrid Output", 41, outputTile,
    { weakPower = { data = "active" } })
end

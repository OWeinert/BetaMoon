-- Build two separate adjacent segments: Input -> Gateway A and Gateway B -> Output.
-- Put the gateways in the same dimension no more than 128 blocks apart. Power or
-- unpower the Input; each transition crosses the wireless gateway link and makes
-- the Output emit the matching redstone state. The channels are intentionally
-- fixed so this example stays focused on hybrid topology; Advanced 16 demonstrates
-- editable channel GUIs. Only transitions are forwarded, avoiding repeated calls.

name = "Hybrid Network Example"
version = "1.0.0"
description = "Adds three blocks that demonstrate a hybrid trigger network. Obtain them from a creative or " ..
    "debug inventory, then build two separate adjacent segments: place Hybrid Input directly beside Hybrid " ..
    "Gateway A, and place Hybrid Output directly beside Hybrid Gateway B. Keep both gateways in the same " ..
    "dimension and no more than 128 blocks apart; the blocks do not need line of sight.\n\n" ..
    "Apply redstone power to Hybrid Input. Gateway A carries that transition over the wireless bridge to " ..
    "Gateway B, and Hybrid Output begins emitting redstone power. Remove the input power to turn the output " ..
    "off. The network forwards changes rather than continuously repeating the same state, so toggle the input " ..
    "after assembling the network. Its fixed internal channels keep this lesson focused on adjacent-to-wireless " ..
    "bridging; Advanced 16 shows editable wireless channel controls."

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
      data = {
        active = { type = "boolean", default = false },
        previous = { type = "boolean", default = false }
      },
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
      local powered = ctx.world:isPowered()
      if powered ~= ctx.entity.data:get("previous") then
        ctx.entity.networks:getRequired("example:network/hybrid_trigger"):pulse(powered)
        ctx.entity.data:set("previous", powered)
      end
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
    signal = { mode = "pulse", value = { type = "boolean", default = false } },
    onPulse = function(ctx)
      for _, output in ipairs(ctx.nodes:find { config = { kind = "output" } }) do
        output:call("setOutput", { active = ctx.pulse or false })
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

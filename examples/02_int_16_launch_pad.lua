-- This block launches players about four blocks upward when they step on it.
-- Craft planks + redstone for one launch pad and place it on a solid floor.
-- Its thin collision and drawing bounds make the model agree with its physical surface.

name = "Launch Pad Example"
version = "1.0.0"
description = "Craft a floor-supported launch pad that preserves horizontal movement while launching players."

function modInit()
  local padBox = { min = { 0, 0, 0 }, max = { 1, 0.25, 1 } }
  local pad = betamoon.blocks:add {
    id = 214,
    key = "example_launch_pad",
    displayName = "Launch Pad",
    -- blockMaterials supplies the canonical native material name.
    material = betamoon.mc.blockMaterials.wood,
    texture = 4,
    hardness = 0.5,
    opaque = false,
    normalCube = false,
    lightOpacity = 0,
    collision = { boxes = { padBox } },
    selection = padBox,
    render = { preset = "cuboid", bounds = padBox },
    placement = {
      attachTo = { "floor" },
      requiresSolidSupport = true,
      dropWhenUnsupported = true
    },
    piston = { reaction = "destroy" },
    fire = { spread = 5, burn = 20 },
    onEntityWalk = {
      action = function(ctx)
        -- Items and creatures pass over the pad normally. isPlayer checks the
        -- entity that triggered this callback without exposing Minecraft internals.
        if not ctx.entity or not ctx.entity:isPlayer() then
          return
        end

        -- getVelocity returns a detached snapshot of the player's current motion.
        -- Keeping x and z preserves forward movement instead of making the pad sticky.
        local velocity = ctx.entity:getVelocity()

        -- Beta 1.7.3 gravity and drag turn this initial speed into an apex of
        -- approximately four blocks above the pad, independent of the landing speed.
        ctx.entity:setVelocity(velocity.x, 0.8032, velocity.z)
      end
    }
  }

  betamoon.recipes:add {
    type = "shapeless",
    output = betamoon.stack(pad),
    ingredients = {
      betamoon.blocks:getRequired(5),
      betamoon.items:getRequired(331)
    }
  }
end

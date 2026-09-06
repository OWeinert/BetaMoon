name = "Override Examples"
version = "2.0.0"
description = "Shows direct, conditional, prioritized, and bulk overrides."

function modInit()
  local stone = betamoon.blocks:require(1)
  -- An override temporarily changes an existing block or item.
  local direct = stone:override {
    displayName = "Smooth Stone"
  }
  -- Conditions make sure the original resource is what you expect.
  -- A higher priority wins when scripts change the same setting.
  local conditional = betamoon.overrides:add {
    target = stone,
    priority = 10,
    when = {
      owner = "minecraft",
      properties = {
        hardness = stone.hardness
      }
    },
    changes = {
      hardness = 2,
      resistance = 12
    }
  }
  -- overrideAll makes the same change to every matching item.
  local bulk = betamoon.items:find {
    where = function(item)
      return item.maxStackSize == 64
    end
  }:overrideAll {
    changes = {
      maxStackSize = 32
    },
    priority = -10
  }

  assert(direct.active)
  assert(conditional.active)

  -- remove puts the old values back. Calling it again is safe.
  -- BetaMoon also removes these changes when the script reloads.
  direct:remove()
  conditional:remove()
  for _, handle in ipairs(bulk) do
    handle:remove()
  end
end

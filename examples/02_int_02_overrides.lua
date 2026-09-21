-- Use an override to change an EXISTING resource while your script owns the change.
-- Use blocks:add/items:add instead when you want a separate new resource.
-- All patches here are removed before modInit ends, making this a reversible API demo.
-- For a lasting example effect, omit the matching remove call; script cleanup still
-- owns the patch. Bulk overrides can affect many items, so inspect the query first.

name = "Override Examples"
version = "2.0.0"
description = "Demonstrates direct, conditional, and bulk overrides of existing resources, including a stone " ..
    "display name, stone hardness and resistance, and stack limits for items that normally stack " ..
    "to 64. It also shows how priorities and explicit removal are expressed.\n\n" ..
    "All demonstration overrides are removed again before initialization finishes. No lasting " ..
    "gameplay changes or custom content are expected from loading this file. Follow the creation " ..
    "and removal calls in order; to observe one override in-game while experimenting, edit the " ..
    "corresponding removal call and reload, then inspect stone or an affected item stack."

function modInit()
  local stone = betamoon.blocks:getRequired(1)
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
  -- Bulk application returns several removal handles. _ names the unused
  -- list index; handle is the individual patch being removed.
  for _, handle in ipairs(bulk) do
    handle:remove()
  end
end

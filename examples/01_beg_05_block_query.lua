-- A query inspects registered block TYPES, not individual blocks placed in the world.
-- Use a numeric ID or a stable key for known content; use find when filtering a group.
-- This example only reads properties and checks results, so it makes no visible changes.
-- Lua's nil means "no value"; check for it before reading a possibly missing result.

name = "Block Registry Query Examples"
version = "2.0.0"
description = "Shows block lookup, criteria, and result helpers."

function modInit()
  -- Find a block by its ID or internal key.
  local stone = betamoon.blocks:getRequired("minecraft:stone")
  -- one is useful when your search should find no more than one block.
  local exact = betamoon.blocks:one {
    id = 1,
    damage = 0
  }
  -- find returns every block that matches these settings. Each supplied field
  -- narrows the result, so these results are Minecraft blocks containing "stone".
  local matches = betamoon.blocks:find {
    nameContains = "stone",
    ignoreCase = true,
    owner = "minecraft"
  }

  -- Results have simple helpers for checking and choosing matches.
  assert(exact ~= nil)
  assert(stone.id == exact.id)
  assert(not matches:isEmpty())
  assert(matches:first())
  assert(matches:last())

  -- A result list can also be read in order when every match matters.
  for _, block in ipairs(matches) do
    assert(block.owner == "minecraft")
  end
end

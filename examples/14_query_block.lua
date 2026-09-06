name = "Block Registry Query Examples"
version = "2.0.0"
description = "Shows block lookup, criteria, and result helpers."

function modInit()
  -- Find a block by its ID or internal key.
  local stone = betamoon.blocks:require("minecraft:stone")
  -- one is useful when your search should find no more than one block.
  local exact = betamoon.blocks:one {
    id = 1,
    damage = 0
  }
  -- find returns every block that matches these settings.
  local matches = betamoon.blocks:find {
    nameContains = "stone",
    ignoreCase = true,
    owner = "minecraft",
    where = function(block)
      return block.hardness >= 0
    end
  }

  -- Results have simple helpers for checking and choosing matches.
  assert(exact ~= nil)
  assert(stone.id == exact.id)
  assert(not matches:isEmpty())
  assert(matches:first())
  assert(matches:last())
end

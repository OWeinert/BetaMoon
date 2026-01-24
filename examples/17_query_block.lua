name = "Query API: Block Examples"
version = "1.0.0"
description = "Shows how to query blocks using the BetaMoon query API."

function modInit()

  -- Just a simple shortening so we don't need to call "betamoon.query():block()" for every query example.
  local function blockQuery()
    return betamoon.query():block()
  end

  -- getById limits the query to a specific block id (0-255).
  -- finishQuery executes the accumulated steps and returns a block handle because the query is singular.
  local stoneHandle = blockQuery()
    :getById(1)
    :finishQuery()

  -- fromHandle validates that a provided handle still exists in the registry,
  -- then finishQuery returns a fresh handle that is guaranteed to be valid.
  blockQuery()
    :fromHandle(stoneHandle)
    :finishQuery()

  -- filterByName keeps only blocks with the given internal name.
  -- finishQuery returns a result handle that exposes list-style helpers.
  local stoneResult = blockQuery()
    :filterByName("tile.stone")
    :finishQuery()

  -- filterByDisplayName matches the name shown to players.
  blockQuery()
    :filterByDisplayName("Stone")
    :finishQuery()

  -- filterDamage narrows the query to blocks whose metadata is within the range.
  blockQuery()
    :filterDamage(0, 0)
    :finishQuery()

  -- getByDamage selects a single block by damage value.
  -- If multiple blocks share that damage, the query becomes an error at finishQuery.
  blockQuery()
    :filterByName("tile.stone")
    :getByDamage(0)
    :finishQuery()

  -- first/last/get can be used as query steps to make the query singular,
  -- so finishQuery returns a block handle instead of a result list.
  blockQuery()
    :filterByName("tile.stone")
    :first()
    :finishQuery()

  blockQuery()
    :filterByName("tile.stone")
    :last()
    :finishQuery()

  blockQuery()
    :filterByName("tile.stone")
    :get(1)
    :finishQuery()
end

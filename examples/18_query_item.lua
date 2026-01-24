name = "Query API: Item Examples"
version = "1.0.0"
description = "Shows how to query items using the BetaMoon query API."

function modInit()
  
  -- Just a simple shortening so we don't need to call "betamoon.query():item()" for every query example.
  local function itemQuery()
    return betamoon.query():item()
  end

  -- getById limits the query to a specific item id (>= 256).
  -- finishQuery executes the accumulated steps and returns an item handle because the query is singular.
  local ironHandle = itemQuery()
    :getById(265)
    :finishQuery()

  -- fromHandle validates that a provided handle still exists in the registry,
  -- then finishQuery returns a fresh handle that is guaranteed to be valid.
  itemQuery()
    :fromHandle(ironHandle)
    :finishQuery()

  -- filterByName keeps only items with the given internal name.
  -- finishQuery returns a result handle that exposes list-style helpers.
  local ironResult = itemQuery()
    :filterByName("item.ingotIron")
    :finishQuery()

  -- filterByDisplayName matches the name shown to players.
  itemQuery()
    :filterByDisplayName("Iron Ingot")
    :finishQuery()

  -- filterDamage narrows the query to items whose metadata is within the range.
  itemQuery()
    :filterDamage(0, 0)
    :finishQuery()

  -- getByDamage selects a single item by damage value.
  -- If multiple items share that damage, the query becomes an error at finishQuery.
  itemQuery()
    :filterByName("item.ingotIron")
    :getByDamage(0)
    :finishQuery()

  -- first/last/get can be used as query steps to make the query singular,
  -- so finishQuery returns an item handle instead of a result list.
  itemQuery()
    :filterByName("item.ingotIron")
    :first()
    :finishQuery()

  itemQuery()
    :filterByName("item.ingotIron")
    :last()
    :finishQuery()

  itemQuery()
    :filterByName("item.ingotIron")
    :get(265)
    :finishQuery()
end

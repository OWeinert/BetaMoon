name = "Query API: Item Examples"
version = "1.0.0"
description = "Shows how to query items using the BetaMoon query API."

function modInit()
  -- Query API example: items
  -- Demonstrates all ItemQueryHandle and ItemQueryResultHandle features.
  -- Note: many query functions can be chained together; these snippets keep them separate for clarity.

  local itemQuery = betamoon.query():item()

  -- Single builder-style chains without intermediate results.
  -- Each snippet stands alone and shows one API call in context.

  -- getById + getId: look up an item by its numeric id and read the id back.
  -- Item ids start at 256 in Minecraft Beta.
  itemQuery
    :getById(265)
    :getId()

  -- getById + getDamage: read the metadata/damage value for the item.
  -- Some items use metadata for variants (dyes, tools, etc.).
  itemQuery
    :getById(265)
    :getDamage()

  -- fromHandle: resolve an item from an existing handle.
  -- This is a validation step when you already have a handle from elsewhere.
  itemQuery
    :fromHandle(itemQuery:getById(265))

  -- filterByName + first: filter by the internal (unlocalized) name.
  -- Internal names are stable but are not shown in the UI.
  itemQuery
    :filterByName("item.ingotIron")
    :first()

  -- filterByDisplayName + last: filter by the localized display name.
  -- Display names are what players see in-game, but can vary by language.
  itemQuery
    :filterByDisplayName("Iron Ingot")
    :last()

  -- filterDamage + count: count how many items in the query fall within a damage range.
  -- Useful for items that have multiple metadata variants.
  itemQuery
    :filterDamage(0, 3)
    :count()

  -- getByDamage: find the single item with this exact damage value.
  -- Errors if the query matches none or more than one item.
  itemQuery
    :filterByName("item.ingotIron")
    :getByDamage(0)

  -- get(index): for items, the "index" is the item id (>255).
  -- Returns the matching item entry for that id if it exists in the query.
  itemQuery
    :filterByName("item.ingotIron")
    :get(265)

  -- finishQuery + first: finalize the query, then return its first item.
  -- The query result is immutable and won’t change if items are registered later.
  itemQuery
    :filterByName("item.ingotIron")
    :finishQuery()
      :first()

  -- finishQuery + last: finalize the query, then return its last item.
  itemQuery
    :filterByName("item.ingotIron")
    :finishQuery()
      :last()

  -- finishQuery + get: fetch an item by id from the query result.
  itemQuery
    :filterByName("item.ingotIron")
    :finishQuery()
      :get(265)

  -- count: return how many items match the current query.
  itemQuery
    :filterByName("item.ingotIron")
    :count()

  -- finishQuery + count: count how many items are in the query result.
  itemQuery
    :filterByName("item.ingotIron")
    :finishQuery()
      :count()

  -- finishQuery + ensureOne + intoHandle: assert exactly one match, then return an immutable item handle.
  itemQuery
    :getById(265)
    :finishQuery()
      :ensureOne()
      :intoHandle()

  -- finishQuery + intoHandles: convert all matched items into immutable item handles.
  itemQuery
    :filterByName("item.ingotIron")
    :finishQuery()
      :intoHandles()
end

name = "Query API: Block Examples"
version = "1.0.0"
description = "Shows how to query blocks using the BetaMoon query API."

function modInit()
  -- Query API example: blocks
  -- Demonstrates all BlockQueryHandle and BlockQueryResultHandle features.
  -- Note: many query functions can be chained together; these snippets keep them separate for clarity.

  local blockQuery = betamoon.query():block()

  -- Single builder-style chains without intermediate results.
  -- Each snippet stands alone and shows one API call in context.

  -- getById + getId: look up a block by its numeric id and read the id back.
  -- Useful when you only know the numeric id (0-255 in Minecraft Beta).
  blockQuery
    :getById(1)
    :getId()

  -- getById + getDamage: read the metadata/damage value for the block.
  -- Most blocks use damage=0, but some blocks use metadata variants.
  blockQuery
    :getById(1)
    :getDamage()

  -- fromHandle: resolve a block from an existing handle.
  -- This is a validation step when you already have a handle from elsewhere.
  blockQuery
    :fromHandle(blockQuery:getById(1))

  -- filterByName + first: filter by the internal (unlocalized) name.
  -- Internal names are stable but are not shown in the UI.
  blockQuery
    :filterByName("tile.stone")
    :first()

  -- filterByDisplayName + last: filter by the localized display name.
  -- Display names are what players see in-game, but can vary by language.
  blockQuery
    :filterByDisplayName("Stone")
    :last()

  -- filterDamage + count: count how many blocks in the query fall within a damage range.
  -- Useful for blocks that have multiple metadata variants.
  blockQuery
    :filterDamage(0, 2)
    :count()

  -- getByDamage: find the single block with this exact damage value.
  -- Errors if the query matches none or more than one block.
  blockQuery
    :filterByName("tile.stone")
    :getByDamage(0)

  -- get(index): for blocks, the "index" is the block id (0-255).
  -- Returns the matching block entry for that id if it exists in the query.
  blockQuery
    :filterByName("tile.stone")
    :get(1)

  -- finishQuery + first: finalize the query, then return its first block.
  -- The query result is immutable and won’t change if blocks are registered later.
  blockQuery
    :filterByName("tile.stone")
    :finishQuery()
      :first()

  -- finishQuery + last: finalize the query, then return its last block.
  blockQuery
    :filterByName("tile.stone")
    :finishQuery()
      :last()

  -- finishQuery + get: fetch a block by id from the query result.
  blockQuery
    :filterByName("tile.stone")
    :finishQuery()
      :get(1)

  -- count: return how many blocks match the current query.
  blockQuery
    :filterByName("tile.stone")
    :count()

  -- finishQuery + count: count how many blocks are in the query result.
  blockQuery
    :filterByName("tile.stone")
    :finishQuery()
      :count()

  -- finishQuery + ensureOne + intoHandle: assert exactly one match, then return an immutable block handle.
  blockQuery
    :getById(1)
    :finishQuery()
      :ensureOne()
      :intoHandle()

  -- finishQuery + intoHandles: convert all matched blocks into immutable block handles.
  blockQuery
    :filterByName("tile.stone")
    :finishQuery()
      :intoHandles()
end

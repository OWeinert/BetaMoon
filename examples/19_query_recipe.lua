name = "Query API: Recipe Examples"
version = "1.0.0"
description = "Shows how to query recipes using the BetaMoon query API."

function modInit()
  -- Query API example: recipes
  -- Demonstrates all RecipeQueryHandle and RecipeQueryResultHandle features.
  -- Note: many query functions can be chained together; these snippets keep them separate for clarity.

  local recipeQuery = betamoon.query():recipe()

  -- filterTypes: return only recipes of the listed types (duplicates ignored).
  recipeQuery
    :filterTypes({ "shaped", "shapeless", "shaped" })
    :count()

  -- filterTypes: use a single string for quick type filtering.
  recipeQuery
    :filterTypes("smelting")
    :count()

  -- filterShaped: return only shaped crafting recipes.
  recipeQuery
    :filterShaped()
    :count()

  -- filterShapeless: return only shapeless crafting recipes.
  recipeQuery
    :filterShapeless()
    :count()

  -- filterSmelting: return only furnace smelting recipes.
  recipeQuery
    :filterSmelting()
    :count()

  -- filterOutput: return recipes whose output matches the given stack.
  recipeQuery
    :filterOutput({ id = 1, count = 1, damage = 0 })
    :count()

  -- filterInput: return recipes that use the given input ingredient.
  -- Smelting matches by input id; crafting matches any ingredient.
  recipeQuery
    :filterInput({ id = 4 })
    :count()

  -- filterOutAndIn: return recipes that match both output and input list.
  -- Use this when you know the result and the ingredients.
  -- - when inputs length > 1, smelting is excluded.
  -- - when length == 1, smelting input id is also checked.
  recipeQuery
    :filterOutAndIn(
      { id = 1, count = 1 },
      { { id = 4 } }
    )
    :count()

  -- Exact recipe matching.
  -- Shaped recipe format matches betamoon.addShapedRecipe output.
  local shapedRecipe = {
    output = { id = 1, count = 1 },
    pattern = { "AA", "AA" },
    key = {
      A = { id = 4 }
    }
  }

  -- Shapeless recipe format matches betamoon.addShapelessRecipe output.
  local shapelessRecipe = {
    output = { id = 265, count = 1 },
    ingredients = {
      { id = 265 },
      { id = 263 }
    }
  }

  -- Smelting recipe format matches betamoon.addSmeltingRecipe output.
  local smeltingRecipe = {
    input = { id = 15 },
    output = { id = 265, count = 1 }
  }

  -- getShaped: find a specific shaped recipe by pattern + key table.
  -- The shape layout must match; key names are not preserved at runtime.
  local shapedHandle = recipeQuery
    :getShaped(shapedRecipe)

  -- getShapeless: find a specific shapeless recipe by ingredient list.
  local shapelessHandle = recipeQuery
    :getShapeless(shapelessRecipe)

  -- getSmelting: find a specific smelting recipe by input and output.
  local smeltingHandle = recipeQuery
    :getSmelting(smeltingRecipe)

  -- getByName: look up a recipe by its internal recipe-map key.
  -- Format: "{type}/{itemName_or_id}_{count}" (optionally with a "_n" suffix for duplicates).
  -- Example: "smelting/item.ingotIron_1" or similar in your pack.
  local recipeByName = recipeQuery
    :getByName("smelting/item.ingotIron_1")

  -- fromHandle: verify a recipe handle is still registered.
  recipeQuery
    :fromHandle(smeltingHandle)

  -- first: return the first recipe in the filtered query.
  recipeQuery
    :filterTypes({ "shaped", "shapeless" })
    :first()

  -- last: return the last recipe in the filtered query.
  recipeQuery
    :filterTypes({ "shaped", "shapeless" })
    :last()

  -- get: return the recipe at the given index in the filtered query.
  recipeQuery
    :filterTypes({ "shaped", "shapeless" })
    :get(1)

  -- count: return how many recipes match the filtered query.
  recipeQuery
    :filterTypes({ "shaped", "shapeless" })
    :count()

  -- finishQuery + first: finalize the query, then return the first recipe.
  -- The query result is immutable and won’t change if recipes are added later.
  recipeQuery
    :filterTypes({ "shaped", "shapeless" })
    :finishQuery()
      :first()

  -- finishQuery + last: finalize the query, then return the last recipe.
  recipeQuery
    :filterTypes({ "shaped", "shapeless" })
    :finishQuery()
      :last()

  -- finishQuery + get: finalize the query, then return the recipe at an index.
  recipeQuery
    :filterTypes({ "shaped", "shapeless" })
    :finishQuery()
      :get(1)

  -- finishQuery + count: count recipes in the query result.
  recipeQuery
    :filterTypes({ "shaped", "shapeless" })
    :finishQuery()
      :count()

  -- finishQuery + ensureOne: assert the query result has exactly one recipe.
  recipeQuery
    :filterTypes({ "shaped", "shapeless" })
    :finishQuery()
      :ensureOne() -- errors if size ~= 1
end

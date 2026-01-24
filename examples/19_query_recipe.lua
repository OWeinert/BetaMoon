name = "Query API: Recipe Examples"
version = "1.0.0"
description = "Shows how to query recipes using the BetaMoon query API."
dependencies = { "Custom Recipe Examples" }

function modInit()

  -- Just a simple shortening so we don't need to call "betamoon.query():recipe()" for every query example.
  local function recipeQuery()
    return betamoon.query():recipe()
  end

  -- filterTypes narrows the query to the recipe types you want to keep.
  -- finishQuery returns a result handle so you can inspect the matched set.
  recipeQuery()
    :filterTypes({ "shaped", "shapeless" })
    :finishQuery()

  -- filterTypes also accepts a single string for quick filtering.
  recipeQuery()
    :filterTypes("smelting")
    :finishQuery()

  -- filterShaped, filterShapeless, filterSmelting are convenience filters.
  -- Basically does the same as :filterTypes("shaped"), etc.
  recipeQuery()
    :filterShaped()
    :finishQuery()

  recipeQuery()
    :filterShapeless()
    :finishQuery()

  recipeQuery()
    :filterSmelting()
    :finishQuery()

  -- filterOutput matches recipes by their output stack.
  -- count=0 means "any stack size" for that item id.
  recipeQuery()
    :filterOutput({ id = 5003, count = 0 })
    :finishQuery()

  -- filterInput matches any recipe that uses the given input.
  -- For smelting, this matches the input id; for crafting, any ingredient matches.
  recipeQuery()
    :filterInput({ id = 3 })
    :finishQuery()

  -- filterOutAndIn matches both the output and the full input list.
  -- With a single input entry, smelting recipes are included as well.
  recipeQuery()
    :filterOutAndIn(
      { id = 5003, count = 1 },
      { { id = 3 } }
    )
    :finishQuery()

  -- Exact recipe matching uses the same structures as addShapedRecipe/addShapelessRecipe/addSmeltingRecipe.
  local shapedRecipe = {
    output = { id = 5003, count = 4 },
    pattern = { "##", "##" },
    key = {
      ["#"] = { id = 265 }
    }
  }

  local shapelessRecipe = {
    output = { id = 5003, count = 2 },
    ingredients = {
      { id = 264 },
      { id = 263 }
    }
  }

  local smeltingRecipe = {
    input = { id = 3 },
    output = { id = 5003, count = 1 }
  }

  -- getShaped returns a singular query, so finishQuery yields the recipe handle.
  local shapedHandle = recipeQuery()
    :getShaped(shapedRecipe)
    :finishQuery()

  -- getShapeless returns a singular query, so finishQuery yields the recipe handle.
  local shapelessHandle = recipeQuery()
    :getShapeless(shapelessRecipe)
    :finishQuery()

  -- getSmelting returns a singular query, so finishQuery yields the recipe handle.
  local smeltingHandle = recipeQuery()
    :getSmelting(smeltingRecipe)
    :finishQuery()

  -- getByName looks up a recipe by its recipe-map key.
  -- Keys are formatted as "{type}/{itemName_or_id}_{count}".
  recipeQuery()
    :getByName("shaped/item.example_dust_4")
    :finishQuery()

  recipeQuery()
    :getByName("shapeless/item.example_dust_2")
    :finishQuery()

  recipeQuery()
    :getByName("smelting/item.example_dust_1")
    :finishQuery()

  -- fromHandle validates that a recipe handle is still registered.
  recipeQuery()
    :fromHandle(shapedHandle)
    :finishQuery()

  recipeQuery()
    :fromHandle(shapelessHandle)
    :finishQuery()

  recipeQuery()
    :fromHandle(smeltingHandle)
    :finishQuery()

  -- first/last/get as query steps make the query singular,
  -- so finishQuery yields the recipe handle instead of a list.
  recipeQuery()
    :filterOutput({ id = 5003, count = 0 })
    :first()
    :finishQuery()

  recipeQuery()
    :filterOutput({ id = 5003, count = 0 })
    :last()
    :finishQuery()

  recipeQuery()
    :filterOutput({ id = 5003, count = 0 })
    :get(1)
    :finishQuery()

end

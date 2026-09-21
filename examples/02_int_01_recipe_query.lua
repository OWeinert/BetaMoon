-- Copy 01_beg_12_vanilla_recipes.lua too: it creates the dust and recipes queried here.
-- dependencies contains the other script's declared name, not its filename.
-- The temporary changes below are removed immediately, so recipes finish unchanged.
-- Use these queries to inspect or adjust recipes without registering duplicate content.

name = "Recipe Registry Query Examples"
version = "2.0.0"
description = "Demonstrates finding shaped, shapeless, and smelting recipes by their output and inspecting " ..
    "the matching recipe handles. Load beginner example 11, Custom Recipe Examples, alongside this " ..
    "file so its Example Dust recipes are available.\n\n" ..
    "During initialization, the script temporarily changes a recipe's output and temporarily " ..
    "disables it, then removes both changes. These checks are silent and leave the original " ..
    "recipes available. Try the companion script's iron-square, diamond-and-coal, and " ..
    "dirt-smelting recipes to confirm their normal results. Compare the queries, handles, and " ..
    "removal calls with the unchanged crafting behavior."
dependencies = {
  "Custom Recipe Examples"
}

function modInit()
  local dust = betamoon.items:getRequired(5014)
  -- Find recipes that make dust. A count of zero means any amount.
  local matches = betamoon.recipes:find {
    output = betamoon.stack(dust, 0),
    type = {
      "shaped",
      "shapeless",
      "smelting"
    }
  }
  local recipe = matches:first()

  -- An empty result has no first recipe. Guard against nil before calling
  -- methods; an optional lookup should not crash just because nothing matched.
  if recipe then
    -- A recipe key lets you find the same recipe again.
    assert(betamoon.recipes:getRequired(recipe.key).key == recipe.key)

    -- This change is only made when the recipe is currently enabled.
    local patch = recipe:override {
      when = {
        type = recipe.type,
        properties = {
          enabled = true
        }
      },
      changes = {
        output = betamoon.stack(dust)
      }
    }
    -- Removing the change puts the old output back.
    patch:remove()

    -- disable turns a recipe off. Removing it turns the recipe back on.
    local disabled = recipe:disable()
    disabled:remove()
  end
end

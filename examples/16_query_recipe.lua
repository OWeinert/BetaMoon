name = "Recipe Registry Query Examples"
version = "2.0.0"
description = "Shows recipe lookup, filtering, and reversible changes."
dependencies = {
  "Custom Recipe Examples"
}

function modInit()
  local dust = betamoon.items:require(5014)
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

  if recipe then
    -- A recipe key lets you find the same recipe again.
    assert(betamoon.recipes:require(recipe.key).key == recipe.key)

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

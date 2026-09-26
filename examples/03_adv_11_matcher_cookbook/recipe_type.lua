-- Private registration module for the Matcher Cookbook type.
-- Returning a function keeps registration inside the package modInit lifecycle.

return function()
  local function ingredientMatches(stack, ingredient)
    if stack == nil or stack.count < ingredient.count then
      return false
    end

    for _, alternative in ipairs(ingredient.anyOf) do
      local damageMatches = ingredient.damage == "any" or stack.damage == alternative.damage
      if stack.id == alternative.id and damageMatches then
        return true
      end
    end
    return false
  end

  local matcher = betamoon.recipeMatchers:add {
    name = "example:recipe_matcher/preferred_single_stack",
    match = function(recipe, snapshot, context, plan)
      local requirements = recipe.ingredients.materials
      if #requirements ~= 1 then
        return false
      end

      -- A normal item role is represented by one cell. Match and allocate the
      -- retained die explicitly when this recipe declares it.
      local die = recipe.ingredients.die
      if die ~= nil then
        if not ingredientMatches(snapshot.die.stack, die) then
          return false
        end
        plan:use { role = "die", slot = snapshot.die.slot }
      elseif snapshot.die.stack ~= nil then
        return false
      end

      -- Pool roles are ordered cell lists. The score makes the preferred slot
      -- win whenever it is eligible, then falls back to the largest stack.
      local bestPosition
      local bestScore = -1
      for position, cell in ipairs(snapshot.materials) do
        if ingredientMatches(cell.stack, requirements[1]) then
          local preferredBonus = position == context.preferredSlot and 100000 or 0
          local score = preferredBonus + cell.stack.count
          if score > bestScore then
            bestPosition = position
            bestScore = score
          end
        end
      end

      if bestPosition == nil then
        return false
      end

      -- The plan contains selections, not mutations. BetaMoon validates complete,
      -- non-overlapping allocations before returning a match and commits them later.
      plan:use {
        role = "materials",
        slot = bestPosition,
        requirement = 1
      }
      return true
    end
  }

  local recipeType = betamoon.recipeTypes:add {
    name = "example:recipe_type/focused_pressing",
    displayName = "Focused Pressing",
    matcher = matcher,
    ingredients = {
      materials = { type = "item_pool", allowExtra = true },
      die = { type = "item", optional = true, consume = false }
    },
    outputs = {
      result = { type = "item" }
    },
    data = {
      duration = { type = "integer", default = 80, min = 1 }
    },
    context = {
      preferredSlot = { type = "integer", min = 1, max = 5 }
    }
  }

  local public = {
    matcher = matcher,
    recipeType = recipeType
  }
  return public
end

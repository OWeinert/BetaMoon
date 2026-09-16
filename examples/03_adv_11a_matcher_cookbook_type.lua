-- This matcher demonstrates a policy the built-in pool allocator intentionally does
-- not impose: take an entire batch from one slot, prefer a machine-selected slot,
-- and otherwise choose the largest eligible stack. Other occupied pool slots are kept.

name = "Matcher Cookbook Type Example"
version = "1.0.0"
description = "Defines a custom matcher that takes an entire batch from one material slot, prefers a slot " ..
    "selected by the machine, and otherwise chooses the largest eligible stack. It also supports a " ..
    "reusable die and leaves other occupied material slots untouched.\n\n" ..
    "Load advanced example 09b to craft and operate the Focused Press; this file alone adds no " ..
    "machine or recipes. Compare four cobblestone in one material slot with two cobblestone in " ..
    "each of two slots: only the single complete stack qualifies for a batch.\n\n" ..
    "Try eligible stacks in slots 1 and 5, then switch redstone power to change the preferred " ..
    "slot. Compare the selected stack with the scoring and allocation code here. Unlike a pooled " ..
    "quantity requirement, this matcher deliberately does not combine smaller stacks to fill a " ..
    "batch."

function modInit()
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
    name = "example:preferred_single_stack",
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
    name = "example:focused_pressing",
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
  betamoon.modules:export("matcher_cookbook", public)
end

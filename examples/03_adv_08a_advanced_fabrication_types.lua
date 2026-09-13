-- This file defines three fabrication contracts. The companion recipe file supplies
-- instances, and the machine file supplies the required slot layout.

name = "Advanced Fabrication Types Example"
version = "1.0.0"
description = "Declares pool, grid, pooled-output, and custom-matched recipe types."

function modInit()
  local bm = betamoon
  local public = {}

  -- Matcher arguments are detached values. Changing recipe or snapshot tables
  -- cannot change a registered recipe or the machine inventory.
  local function ingredientMatches(stack, ingredient)
    if stack == nil or stack.count < ingredient.count then return false end
    for _, alternative in ipairs(ingredient.anyOf) do
      local damageMatches = ingredient.damage == "any" or stack.damage == alternative.damage
      if stack.id == alternative.id and damageMatches then return true end
    end
    return false
  end

  public.adjacentSequence = bm.recipeMatchers:add {
    name = "example:adjacent_sequence",
    match = function(recipe, snapshot, context, plan)
      local requirements = recipe.ingredients.lane
      local cells = snapshot.lane
      if #requirements ~= 2 then return false end

      -- This custom fabrication rule accepts exactly two occupied cells. They
      -- must be adjacent and must appear in recipe declaration order.
      local occupied = 0
      for _, cell in ipairs(cells) do
        if cell.stack ~= nil then occupied = occupied + 1 end
      end
      if occupied ~= 2 then return false end

      for position = 1, #cells - 1 do
        local first = cells[position].stack
        local second = cells[position + 1].stack
        if ingredientMatches(first, requirements[1])
            and ingredientMatches(second, requirements[2]) then
          -- use selects quantities from the read-only snapshot. The builder
          -- verifies role names, requirement indexes, item matches, quantities,
          -- duplicate allocation, and complete coverage before a match exists.
          -- An error or an invalid/incomplete accepted plan disables this matcher
          -- and records a script error instead of risking an inventory mutation.
          plan:use { role = "lane", slot = position, requirement = 1 }
          plan:use { role = "lane", slot = position + 1, requirement = 2 }
          return true
        end
      end
      return false
    end
  }

  public.bulkMixing = bm.recipeTypes:add {
    name = "example:bulk_mixing",
    displayName = "Bulk Mixing",
    ingredients = {
      -- Requirements may be distributed across any of the bound pool slots.
      -- With allowExtra false, unrelated occupied slots reject the match.
      materials = { type = "item_pool", allowExtra = false },
      catalyst = { type = "item", optional = true, consume = false }
    },
    outputs = {
      result = { type = "item" },
      -- A recipe can produce several stacks which the transaction inserts
      -- across all slots bound to this role.
      byproducts = { type = "item_output_pool", optional = true }
    },
    primaryOutput = "result"
  }

  public.gridAssembly = bm.recipeTypes:add {
    name = "example:grid_assembly",
    displayName = "Grid Assembly",
    ingredients = {
      work = {
        type = "item_grid", width = 3, height = 3,
        allowSmaller = true, placement = "anywhere",
        transformations = {
          "mirror_horizontal", "rotate_90", "rotate_180", "rotate_270"
        },
        emptyCells = "required"
      }
    },
    outputs = { result = { type = "item" } }
  }

  public.sequenceAssembly = bm.recipeTypes:add {
    name = "example:sequence_assembly",
    displayName = "Sequence Assembly",
    matcher = public.adjacentSequence,
    ingredients = { lane = { type = "item_pool", allowExtra = false } },
    outputs = { result = { type = "item" } }
  }

  bm.modules:export("advanced_fabrication_types", public)
end

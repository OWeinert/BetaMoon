-- One item ID can represent several inventory subtypes through its damage value.
-- Unlike tool wear, subtype damage deliberately chooses a persistent variant.
-- Craft coal, redstone, or glowstone dust by itself to obtain the three samples.

name = "Item Subtypes Example"
version = "1.0.0"
description = "Declares metadata-based item subtypes with distinct icons and colors."

function modInit()
  local crystal = betamoon.items:add {
    id = 5025,
    key = "example_crystal",
    displayName = "Example Crystal",
    maxStackSize = 64,
    -- hasSubtypes tells Minecraft that damage selects a kind of crystal instead
    -- of tracking durability wear.
    hasSubtypes = true,
    -- icon is the fallback for damage values without an explicit variant.
    icon = { x = 7, y = 3 },
    render = {
      -- Variant keys are concrete damage values. icon uses the zero-based
      -- vanilla atlas index; color is an RGB tint applied while rendering.
      variants = {
        [0] = { icon = 55, color = 0x80D8FF },
        [1] = { icon = 56, color = 0xFF8080 },
        [2] = { icon = 57, color = 0xFFF080 }
      }
    }
  }

  local ingredients = {
    betamoon.items:getRequired(263),
    betamoon.items:getRequired(331),
    betamoon.items:getRequired(348)
  }
  for damage, ingredient in ipairs(ingredients) do
    -- Subtracting one maps these entries to damage values 0, 1, and 2.
    betamoon.recipes:add {
      type = "shapeless",
      ingredients = { ingredient },
      output = betamoon.stack(crystal, 1, damage - 1)
    }
  end
end

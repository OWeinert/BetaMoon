-- One item ID can represent several inventory subtypes through its damage value.
-- Unlike tool wear, subtype damage deliberately chooses a persistent variant.
-- Craft coal, redstone, or glowstone dust by itself to obtain the three samples.

name = "Item Subtypes Example"
version = "1.0.0"
description = "Adds three visual subtypes of Example Crystal under one item ID, 5025. The item's damage " ..
    "value selects a blue, red, or yellow icon and tint rather than representing tool wear.\n\n" ..
    "Craft one coal by itself to obtain the blue crystal, one redstone dust by itself for red, or " ..
    "one glowstone dust by itself for yellow. Place the results side by side in your inventory and " ..
    "select each in turn. They share a display name but have different appearances. The crystals " ..
    "have no special use powers; compare their damage values, icons, colors, and recipe outputs in " ..
    "the source."

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

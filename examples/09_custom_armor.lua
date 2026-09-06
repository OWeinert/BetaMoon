name = "Custom Armor Example"
version = "2.0.0"
description = "Declares armor with a vanilla material and render texture."

function modInit()
  -- armor:add creates armor for a chosen body slot.
  betamoon.armor:add {
    id = 5008,
    material = "iron",
    slot = "helmet",
    key = "example_helmet",
    displayName = "Example Helmet",
    -- renderIndex uses the normal iron armor picture on the player.
    renderIndex = "iron",
    icon = { x = 2, y = 0 }
  }

  -- Create each piece of an armor set separately.
  betamoon.armor:add {
    id = 5009,
    material = "iron",
    slot = "chestplate",
    key = "example_chestplate",
    displayName = "Example Chestplate",
    renderIndex = "iron",
    icon = { x = 2, y = 1 }
  }
end

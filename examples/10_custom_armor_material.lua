name = "Custom Armor Material Example"
version = "1.0.0"
description = "Defines a custom armor material."


function modInit()

  -- Creates a new armor material (level: 2 = iron-tier).
  local example_material = betamoon.createArmorMaterial("EXAMPLE_MATERIAL", 2)

  -- Use the custom material to create a full set piece.
  betamoon.createArmor(5007, example_material, "leggings", "example_leggings")
    :setVanillaRenderIndex("iron")
    :setIconCoord(2, 2)
    :register("Example Leggings")
end

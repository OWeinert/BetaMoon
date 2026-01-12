name = "Custom Armor Example"

dependencies = {}

function modInit()

  -- Creates a helmet using a vanilla armor material (iron).
  betamoon.createArmor(5005, "iron", "helmet", "example_helmet")
    -- Reuse vanilla armor textures for the selected material.
    :setVanillaRenderIndex("iron")
    -- Use a vanilla item icon.
    :setIconCoord(2, 0)
    :register("Example Helmet")

  -- Creates a chestplate using the same material.
  betamoon.createArmor(5006, "iron", "chestplate", "example_chestplate")
    :setVanillaRenderIndex("iron")
    :setIconCoord(2, 1)
    :register("Example Chestplate")
end

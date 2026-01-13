name = "Custom Recipe Examples"
version = "1.0.0"
description = "Shows custom crafting and smelting recipes."


function modInit()

  betamoon.createItem(5003, "example_dust")
    :setIconCoord(8, 3)
    :register("Example Dust")

  -- Shaped recipe: 2x2 iron ingots -> 4 Example Dust.
  betamoon.addShapedRecipe(
    { id = 5003, count = 4 },
    { "##", "##" },
    { ["#"] = 265 }
  )

  -- Shapeless recipe: diamond + coal -> 2 Example Dust.
  betamoon.addShapelessRecipe(
    { id = 5003, count = 2 },
    { 264, 263 }
  )

  -- Smelting recipe: dirt -> Example Dust.
  betamoon.addSmeltingRecipe(3, { id = 5003 })
end

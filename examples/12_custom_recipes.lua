name = "Custom Recipe Examples"

dependencies = {}

function modInit()

  -- Create two custom items used by the recipes below.
  betamoon.createItem(5003, "example_gem")
    :setIconCoord(1, 7)
    :register("Example Gem")

  betamoon.createItem(5004, "example_dust")
    :setIconCoord(2, 7)
    :register("Example Dust")

  -- Shaped recipe: 2x2 iron ingots -> 4 Example Gems.
  betamoon.addShapedRecipe(
    { id = 5003, count = 4 },
    { "##", "##" },
    { ["#"] = 265 }
  )

  -- Shapeless recipe: diamond + coal -> 2 Example Dust.
  betamoon.addShapelessRecipe(
    { id = 5004, count = 2 },
    { 264, 263 }
  )

  -- Smelting recipe: cobblestone -> Example Gem.
  betamoon.addSmeltingRecipe(4, { id = 5003 })
end

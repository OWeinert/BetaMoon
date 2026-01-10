-- The name of your mod
name = "Mod Name"

-- A list of names of other Lua mods that have to be loaded before your mod.
-- This is needed in the case you want to add further functionality or content depending on the other mod's content.
dependencies = {}

-- This is the entrypoint of your mod.
-- Here you create and register any functionality or content your mod provides.
function modInit()
  -- Creating a new tool material is very simple.
  -- You just need to provide a name, harvestLevel, maxUses, harvestSpeed and damage.
  -- "name" is the materials name. This has to be in full caps so Minecraft doesn't complain.
  -- "harvestLevel" is a number from 0-3 where 0 is equal to Wood tools and 3 is equal to Diamond tools.
  -- "maxUses" is the amount of blocks that can be broken before the tool breaks.
  -- "harvestSpeed" is how fast the tool breaks blocks.
  -- "damage" is how much damage the sword of this material deals to entities.
  local example_tool_material = betamoon.createToolMaterial("EXAMPLE", 3, 2048, 7.0, 3)

  -- Of course you can also defines tool materials global so other mods can use your tool material.
  -- the tool material variable can also be used when creating tools to directly set the tool material instead of using it's name. (see 06_custom_tool.lua for more info)
end

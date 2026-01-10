name = "Custom Block Example"

-- List of lua mod names which this mod depends on to be loaded before.
dependencies = {}

-- This is your mod entrypoint. Anything you want to create has to be executed in here.
function modInit()

  -- Creates a BlockHandle which is used to configure your block.
  betamoon.createBlock(200, "rock")

    -- Sets the internal name for your block (This is needed from Minecraft).
    :setBlockName("example_block") 

    -- Sets the block hardness, i.e. how long it takes to break the block. [Optional]
    :setHardness(1.5) 

    -- Sets the resistance of the block, i.e. how resitant it is to explosions. [Optional]
    :setResistance(10.0) 

    -- Sets the tool type and harvest level of the block. E.g. ores need pickaxes to be harvested and have different harvest levels. [Optional]
    -- The tool type can be any of pickaxe, axe, shovel, hoe and sword.
    -- Harvest level is only effective for pickaxes.
    -- Multiple calls to :setBlockHavestLevel(...) make it possible for blocks to be mined using multiple tool types.
    :setBlockHarvestLevel("pickaxe", 0) 

    -- Sets the step sound when walking on the block. For a full list of step sounds refer to the wiki page. [Optional]
    :setStepSound("stone")

    -- Finally register the block and give it it's ingame name.
    :register("Example Block")
end

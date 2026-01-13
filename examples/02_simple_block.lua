name = "Custom Block Example"
version = "1.0.0"
description = "Shows a basic custom block setup."


function modInit()

  -- Creates a BlockHandle which is used to configure your block.
  betamoon.createBlock(200, "rock", "example_block")

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

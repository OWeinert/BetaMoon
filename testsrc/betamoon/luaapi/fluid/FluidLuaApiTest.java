package betamoon.luaapi.fluid;

import betamoon.fluid.FluidBlockRole;
import betamoon.fluid.FluidRegistration;
import betamoon.fluid.FluidRegistry;
import betamoon.fluid.block.FluidBlock;
import betamoon.fluid.interaction.FluidInteractionRegistry;
import betamoon.fluid.item.FluidBucketItem;
import betamoon.luaapi.BetaMoonModule;
import betamoon.luamodloader.LuaScriptRegistry;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.src.Block;
import net.minecraft.src.Item;
import net.minecraft.src.ModLoader;
import net.minecraft.src.Session;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/** Exercises shorthand, advanced binding, preflight and retained native identities. */
public final class FluidLuaApiTest {
    private FluidLuaApiTest() {
    }

    public static void main(String[] arguments) throws Exception {
        prepareModLoaderBlockList();
        Method owner = LuaScriptRegistry.class.getDeclaredMethod("setCurrentScriptFile", String.class);
        owner.setAccessible(true);
        owner.invoke(null, "fluid_test.lua");
        try {
            Globals lua = JsePlatform.standardGlobals();
            new BetaMoonModule().call(LuaValue.NIL, lua);
            verifyShorthand(lua);
            verifyAdvancedBinding(lua);
            verifyInteractions(lua);
            verifyPreflight(lua);
            verifyReload(lua);
            verifyUnboundTracking(lua);
            System.out.println("Fluid Lua API checks passed.");
        } finally {
            owner.invoke(null, new Object[]{null});
        }
    }

    private static void verifyShorthand(Globals lua) {
        lua.load("acid=betamoon.fluids:add{key='quality:fluid/acid',material=betamoon.mc.blockMaterials.water,"
                + "preset='water',blocks={flowing={id=240,key='quality:block/acid_flowing'},"
                + "still={id=241,key='quality:block/acid_still'}},bucket={id=28200,"
                + "key='quality:item/acid_bucket',displayName='Acid Bucket',icon={x=3,y=4}},"
                + "appearance={still=17,flowing=18,tint=0x83D936,renderPass='translucent'},"
                + "flow={tickRate=8,sourceCreation='never'},displacement={blocks='drop'}};"
                + "assert(acid.key=='quality:fluid/acid' and acid.material=='water' and acid.preset=='water');"
                + "assert(acid.flowingBlock.id==240 and acid.flowingBlock.fluidRole=='flowing');"
                + "assert(acid.stillBlock.id==241 and acid.stillBlock.fluidRole=='still');"
                + "assert(acid.bucketItem.id==28200 and acid.bucketItem.fluid.key==acid.key);"
                + "assert(not pcall(function() acid.key='changed' end));"
                + "assert(not pcall(function() rawset(acid,'key','changed') end));"
                + "assert(betamoon.fluids:get('quality:acid').key==acid.key);"
                + "assert(betamoon.fluids:one{key='quality:acid',isVanilla=false}.key==acid.key)").call();

        require(Block.blocksList[240] instanceof FluidBlock && Block.blocksList[241] instanceof FluidBlock,
                "Shorthand creates two specialized fluid blocks");
        require(Item.itemsList[240] == null && Item.itemsList[241] == null,
                "Fluid blocks do not expose ordinary ItemBlock forms");
        require(Item.itemsList[28200] instanceof FluidBucketItem, "Shorthand creates a specialized bucket item");
        FluidRegistration acid = FluidRegistry.instance().getByBlockId(240);
        require(acid == FluidRegistry.instance().getByBlockId(241), "Explicit pair IDs share one registration");
        require(acid.roleOf(240) == FluidBlockRole.FLOWING && acid.roleOf(241) == FluidBlockRole.STILL,
                "Pair roles are preserved");
        require(acid.definition().flow().tickRate() == 8, "Explicit flow fields override the preset");
    }

    private static void verifyAdvancedBinding(Globals lua) {
        lua.load("local definition=betamoon.fluids:define{key='quality:fluid/brine',material='water',"
                + "appearance={still=19,flowing=20}};"
                + "local flowing=betamoon.blocks:add{id=242,key='quality:block/brine_flowing',type='fluid',"
                + "fluid=definition,fluidRole='flowing'};"
                + "local still=betamoon.blocks:add{id=246,key='quality:block/brine_still',type='fluid',"
                + "fluid=definition,fluidRole='still'};"
                + "brine=definition:bind{flowing=flowing,still=still};"
                + "assert(brine.flowingBlock.id==242 and brine.stillBlock.id==246 and brine.bucketItem==nil)").call();
        require(FluidRegistry.instance().isSameFluid(242, 246),
                "Advanced binding supports deliberately non-adjacent pair IDs");
    }

    private static void verifyPreflight(Globals lua) {
        lua.load("local ok=pcall(function() betamoon.fluids:add{key='quality:fluid/invalid',material='water',"
                + "blocks={flowing={id=243,key='quality:block/not_allocated'},"
                + "still={id=1,key='quality:block/occupied'}},appearance={still=1,flowing=2}} end);"
                + "assert(not ok)").call();
        require(Block.blocksList[243] == null, "Shorthand validates every nested block before allocating the first");
    }

    private static void verifyInteractions(Globals lua) {
        lua.load("local rule=betamoon.fluids.interactions:add{key='quality:fluid_interaction/acid_brine',"
                + "fluids={acid,brine},replace=acid,faces={'north','up'},outcomes={"
                + "{when={source=true},replaceWith=betamoon.blocks:getRequired(1)},"
                + "{when={source=false},replaceWith=false}},effects={sound='random.fizz',particle='largesmoke',"
                + "particleCount=4}};assert(rule.key=='quality:fluid_interaction/acid_brine');"
                + "local ok=pcall(function() betamoon.fluids.interactions:add{"
                + "key='quality:fluid_interaction/overlap',fluids={acid,brine},replace=acid,faces={'north'},"
                + "outcomes={{replaceWith=false}}} end);assert(not ok)").call();

        FluidInteractionRegistry.instance().beginLoadPass();
        FluidInteractionRegistry.instance().finishLoadPass();
        lua.load("betamoon.fluids.interactions:add{key='quality:fluid_interaction/replacement',"
                + "fluids={acid,brine},replace=acid,faces={'north'},outcomes={{replaceWith=false}}}").call();
    }

    private static void verifyReload(Globals lua) {
        Block flowing = Block.blocksList[240];
        Block still = Block.blocksList[241];
        Item bucket = Item.itemsList[28200];
        lua.load("acid=betamoon.fluids:add{key='quality:fluid/acid',material='water',preset='water',"
                + "blocks={flowing={id=240,key='quality:block/acid_flowing'},"
                + "still={id=241,key='quality:block/acid_still'}},"
                + "bucket={id=28200,key='quality:item/acid_bucket',icon={x=3,y=4}},"
                + "appearance={still=21,flowing=22},flow={tickRate=11}};"
                + "assert(acid.flowingBlock.texture==21)").call();
        require(Block.blocksList[240] == flowing && Block.blocksList[241] == still && Item.itemsList[28200] == bucket,
                "Reload preserves native block and item identity");
        require(FluidRegistry.instance().getByBlockId(240).definition().flow().tickRate() == 11,
                "Reload atomically replaces the fluid policy snapshot");
        lua.load("local ok=pcall(function() betamoon.fluids:add{key='quality:fluid/acid',material='water',"
                + "blocks={flowing={id=244,key='quality:block/moved_flowing'},"
                + "still={id=245,key='quality:block/moved_still'}},appearance={still=1,flowing=2}} end);"
                + "assert(not ok)").call();
        require(Block.blocksList[244] == null && Block.blocksList[245] == null,
                "Structural reload mismatches fail before allocating replacement IDs");
    }

    private static void verifyUnboundTracking(Globals lua) {
        FluidDefinitionTracker.beginLoadPass();
        lua.load("betamoon.fluids:define{key='quality:fluid/unbound',material='water',"
                + "appearance={still=1,flowing=2}}").call();
        require(FluidDefinitionTracker.finishLoadPass().size() == 1,
                "An advanced definition left unbound is reported at the end of the load pass");
    }

    private static void prepareModLoaderBlockList() throws Exception {
        Field blockList = Session.class.getDeclaredFields()[0];
        blockList.setAccessible(true);
        Field modLoaderList = ModLoader.class.getDeclaredField("field_blockList");
        modLoaderList.setAccessible(true);
        modLoaderList.set(null, blockList);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

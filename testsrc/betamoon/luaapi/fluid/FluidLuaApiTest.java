package betamoon.luaapi.fluid;

import betamoon.fluid.FluidBlockRole;
import betamoon.fluid.FluidFlowPolicy;
import betamoon.fluid.FluidRegistration;
import betamoon.fluid.FluidRegistry;
import betamoon.fluid.block.FluidBlock;
import betamoon.fluid.interaction.FluidInteractionRegistry;
import betamoon.fluid.item.FluidBucketItem;
import betamoon.assets.io.FileAssetProvider;
import betamoon.client.assets.AtlasTextures;
import betamoon.client.assets.ClientAssets;
import betamoon.luaapi.BetaMoonModule;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.instrumentation.agent.AgentRuntime;
import betamoon.resources.EnumTexAtlas;
import java.io.File;
import java.io.FileReader;
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
        installTestHookStatuses();
        prepareModLoaderBlockList();
        Method owner = LuaScriptRegistry.class.getDeclaredMethod("setCurrentScriptFile", String.class);
        owner.setAccessible(true);
        owner.invoke(null, "fluid_test.lua");
        try {
            Globals lua = JsePlatform.standardGlobals();
            new BetaMoonModule().call(LuaValue.NIL, lua);
            verifyShorthand(lua);
            verifyPolicyParsing(lua);
            verifyAdvancedBinding(lua);
            verifyInteractions(lua);
            verifyPreflight(lua);
            verifyReload(lua);
            verifyUnboundTracking(lua);
            verifyDistributedExample(lua, owner);
            System.out.println("Fluid Lua API checks passed.");
        } finally {
            owner.invoke(null, new Object[]{null});
            System.clearProperty(AgentRuntime.STATUS_PROPERTY);
        }
    }

    private static void installTestHookStatuses() {
        System.setProperty(AgentRuntime.STATUS_PROPERTY, "ACTIVE");
        for (String id : new String[]{"water_movement", "lava_movement", "eye_material", "entity_tick",
                "native_lava_damage", "breathing", "living_tick"}) {
            AgentRuntime.getReport().applied("betamoon:fluid_contact:" + id, "test");
        }
        AgentRuntime.getReport().waiting("betamoon:fluid_contact:item_water_movement", "EntityItem");
        for (String id : new String[]{"wet", "air_hud", "updateFogColor_material", "updateFogColor",
                "setupFog_material", "setupFog", "overlay_visibility", "overlay_texture"}) {
            AgentRuntime.getReport().applied("betamoon:fluid_camera:" + id, "test");
        }
    }

    private static void verifyShorthand(Globals lua) {
        lua.load("acid=betamoon.fluids:add{key='quality:fluid/acid',material=betamoon.mc.blockMaterials.water,"
                + "preset='water',blocks={flowing={id=240,key='quality:block/acid_flowing'},"
                + "still={id=241,key='quality:block/acid_still'}},bucket={id=28200,"
                + "key='quality:item/acid_bucket',displayName='Acid Bucket',icon={x=3,y=4}},"
                + "appearance={still=17,flowing=18,tint=0x83D936,renderPass='translucent'},"
                + "flow={tickRate=8,finite=true},displacement={blocks='drop'},"
                + "entity={movement='water',currentScale=0.6,drowning=false,countsAsWet=false,"
                + "extinguishesFire=false,contactDamage=2,contactIntervalTicks=15},"
                + "camera={fogColor=0x29451B,fogDensity=0.16,overlay=false}};"
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
        require(acid.definition().flow().sourceCreation() == FluidFlowPolicy.SourceCreation.NEVER,
                "Finite overrides a renewable water preset");
        require(!acid.definition().entity().drowning() && acid.definition().entity().contactDamage() == 2
                && acid.definition().entity().contactIntervalTicks() == 15,
                "Entity declaration publishes its compiled policy atomically");
        require(acid.definition().camera().fogColor().intValue() == 0x29451B
                && !acid.definition().camera().hasOverlay(), "Camera declaration publishes its resolved policy");
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

    private static void verifyPolicyParsing(Globals lua) {
        require(policy(lua, "water", null).sourceCreation() == FluidFlowPolicy.SourceCreation.TWO_ADJACENT
                        && policy(lua, "lava", null).sourceCreation() == FluidFlowPolicy.SourceCreation.NEVER,
                "Water is renewable by default and lava is finite by default");
        require(policy(lua, "water", "{finite=true}").sourceCreation() == FluidFlowPolicy.SourceCreation.NEVER
                        && policy(lua, "lava", "{finite=false}").sourceCreation()
                                == FluidFlowPolicy.SourceCreation.TWO_ADJACENT,
                "Finite explicitly overrides either preset");
        require(policy(lua, "water", "{sourceCreation='never'}").sourceCreation()
                        == FluidFlowPolicy.SourceCreation.NEVER
                        && policy(lua, "water", "{finite=false,sourceCreation='two_adjacent'}").sourceCreation()
                                == FluidFlowPolicy.SourceCreation.TWO_ADJACENT,
                "Existing sourceCreation declarations and matching explicit settings remain valid");
        lua.load("local function invalid(flow) "
                + "return not pcall(function() betamoon.fluids:define{key='quality:fluid/bad_finite',"
                + "material='water',appearance={still=1,flowing=2},flow=flow} end) end;"
                + "assert(invalid{finite='yes'});"
                + "assert(invalid{finite=true,sourceCreation='two_adjacent'});"
                + "assert(invalid{finite=false,sourceCreation='never'})").call();

        LuaValue declaration = lua.load("return {key='quality:fluid/policy',material='water',"
                + "appearance={still=1,flowing=2},entity={movement='lava',currentScale=0.5,"
                + "drowning=true,countsAsWet=false,extinguishesFire=false,ignitesEntities=true,"
                + "contactDamage=3,contactIntervalTicks=15},"
                + "camera={fogColor=0x123456,fogDensity=0.2,overlay=false}}").call();
        FluidDraft draft = FluidDeclaration.parse(declaration, false).draft;
        require(draft.entity().movement() == betamoon.fluid.FluidEntityPolicy.Movement.LAVA
                && draft.entity().contactDamage() == 3 && draft.entity().contactIntervalTicks() == 15,
                "Entity policy compiles independently of fluid registration");
        require(draft.camera().fogColor().intValue() == 0x123456
                && Math.abs(draft.camera().fogDensity().floatValue() - 0.2F) < 0.0001F
                && !draft.camera().hasOverlay(), "Camera fields compile into an immutable render policy");
        lua.load("local ok=pcall(function() betamoon.fluids:define{key='quality:fluid/bad_policy',"
                + "material='water',appearance={still=1,flowing=2},"
                + "entity={contactIntervalTicks=201}} end); assert(not ok)").call();
        lua.load("local ok=pcall(function() betamoon.fluids:define{key='example:fluid/not_a_lesson',"
                + "material='water',appearance={still=1,flowing=2}} end); assert(not ok)").call();
        System.setProperty(AgentRuntime.STATUS_PROPERTY, "NOT_LOADED");
        try {
            lua.load("local ok=pcall(function() betamoon.fluids:add{key='quality:fluid/no_hook',"
                    + "material='water',appearance={still=1,flowing=2},entity={drowning=true},"
                    + "blocks={flowing={id=249,key='quality:block/no_hook_flow'},"
                    + "still={id=250,key='quality:block/no_hook_still'}}} end); assert(not ok)").call();
        } finally {
            System.setProperty(AgentRuntime.STATUS_PROPERTY, "ACTIVE");
        }
        require(Block.blocksList[249] == null && Block.blocksList[250] == null,
                "Missing entity hooks reject shorthand before native block allocation");
    }

    private static FluidFlowPolicy policy(Globals lua, String preset, String flow) {
        String flowField = flow == null ? "" : ",flow=" + flow;
        LuaValue declaration = lua.load("return {key='quality:fluid/policy_parse',material='water',preset='"
                + preset + "',appearance={still=1,flowing=2}" + flowField + "}").call();
        return FluidDeclaration.parse(declaration, false).draft.flow();
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
        require(FluidRegistry.instance().getByBlockId(240).definition().flow().sourceCreation()
                        == FluidFlowPolicy.SourceCreation.TWO_ADJACENT,
                "Reload restores the preset renewal rule when finite is omitted");
        require(FluidRegistry.instance().getByBlockId(240).definition().entity().drowning()
                && FluidRegistry.instance().getByBlockId(240).definition().camera().hasOverlay(),
                "Reload restores omitted entity and camera fields to the selected preset");
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

    private static void verifyDistributedExample(Globals lua, Method owner) throws Exception {
        String file = "02_int_20_custom_fluid.lua";
        ClientAssets.useProviders(new FileAssetProvider(new File("examples")), null, message -> {
            throw new AssertionError(message);
        });
        AtlasTextures.useBackend(new AtlasTextures.Backend() {
            private int nextIndex = 200;

            public int allocate(EnumTexAtlas atlas) {
                return nextIndex++;
            }

            public boolean isReady() {
                return false;
            }

            public boolean isAnaglyph() {
                return false;
            }

            public void upload(EnumTexAtlas atlas, int index, byte[] pixels) {
            }
        });
        owner.invoke(null, file);
        FileReader reader = new FileReader(new File("examples", file));
        try {
            lua.load(reader, file).call();
        } finally {
            reader.close();
        }
        lua.get("modInit").call();

        FluidRegistration acid = FluidRegistry.instance().getByBlockId(251);
        require(acid != null && "example:fluid/lesson_acid".equals(acid.key().toString()),
                "The distributed fluid example must register in its example namespace");
        require(FluidRegistry.instance().getByBlockId(252) == acid && Item.itemsList[5047] instanceof FluidBucketItem,
                "The distributed fluid example must publish its paired blocks and bucket");
        require(acid.definition().flow().sourceCreation() == FluidFlowPolicy.SourceCreation.NEVER,
                "The distributed finite-fluid example must not create new sources");
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

package betamoon.luaapi;

import betamoon.instrumentation.api.CallRedirectHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.diagnostics.TransformationReport;
import betamoon.instrumentation.mapping.RuntimeNamespace;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.instrumentation.registry.HookRegistry;
import betamoon.instrumentation.transform.BetaMoonTransformer;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptResourceTracker;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.minecraft.src.Block;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.TileEntity;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Exercises Lua overrides through an injected virtual call and the actual
 * vanilla furnace method.
 */
public final class BlockDisplayOverrideTest {
    public static void main(String[] args) throws Exception {
        Method dispatch = createDispatch(args[0]);
        TestWorld world = new TestWorld();
        Globals lua = JsePlatform.standardGlobals();
        new BetaMoonModule().call(LuaValue.NIL, lua);
        Method owner = LuaScriptRegistry.class.getDeclaredMethod("setCurrentScriptFile", String.class);
        owner.setAccessible(true);
        owner.invoke(null, "display_low.lua");
        lua.load("furnace = betamoon.blocks:getRequired(62); "
                + "function particle(ctx, name) ctx.world:spawnParticle(name, {x=ctx.x,y=ctx.y,z=ctx.z}) end").call();

        verifyPropertyLayers(lua, owner);
        owner.invoke(null, "display_low.lua");
        verify(dispatch, world, "smoke", "flame");
        lua.load("low = furnace:override {onDisplayTick={action=function(ctx) "
                + "saved=ctx; particle(ctx,'before'); ctx:base(); particle(ctx,'after') end}}").call();
        verify(dispatch, world, "before", "smoke", "flame", "after");
        lua.load("assert(not pcall(function() saved:base() end))").call();

        owner.invoke(null, "display_high.lua");
        lua.load("high = furnace:override {priority=10,onDisplayTick={action=function(ctx) "
                + "particle(ctx,'replacement') end}}").call();
        verify(dispatch, world, "replacement");
        ScriptResourceTracker.unload("display_high.lua");
        verify(dispatch, world, "before", "smoke", "flame", "after");

        lua.load("high = furnace:override {priority=10,onDisplayTick={action=function(ctx) "
                + "ctx:base(); assert(not pcall(function() ctx:base() end)); particle(ctx,'once') end}}").call();
        verify(dispatch, world, "smoke", "flame", "once");
        lua.load("high:remove(); low:remove()").call();
        verify(dispatch, world, "smoke", "flame");

        lua.load("bad = furnace:override {onDisplayTick={action=function(ctx) error('before base') end}}").call();
        verify(dispatch, world, "smoke", "flame");
        verify(dispatch, world, "smoke", "flame");
        lua.load("bad:remove(); bad = furnace:override {onDisplayTick={action=function(ctx) "
                + "ctx:base(); error('after base') end}}").call();
        verify(dispatch, world, "smoke", "flame");
        lua.load("bad:remove(); assert(not pcall(function() furnace:override " + "{onDisplayTick={action=42}} end))")
                .call();
        verify(dispatch, world, "smoke", "flame");
        System.out.println(
                "Display overrides passed: injected dispatch, vanilla base, order, lifetime, layers and errors.");
    }

    private static void verifyPropertyLayers(Globals lua, Method owner) throws Exception {
        int baseLight = Block.lightValue[62];
        owner.invoke(null, "property_low.lua");
        lua.load("propertyLow = furnace:override {priority=0, light=1}").call();
        owner.invoke(null, "property_middle.lua");
        lua.load("propertyMiddle = furnace:override {priority=5, light=2}").call();
        owner.invoke(null, "property_high.lua");
        lua.load("propertyHigh = furnace:override {priority=10, light=3}").call();
        require(Block.lightValue[62] == 3, "Highest-priority property layer was not applied");

        lua.load("propertyMiddle:remove()").call();
        require(Block.lightValue[62] == 3, "Removing a middle property layer changed the effective value");
        ScriptResourceTracker.unload("property_high.lua");
        require(Block.lightValue[62] == 1, "Removing the highest layer did not reveal the remaining lower layer");
        ScriptResourceTracker.unload("property_low.lua");
        require(Block.lightValue[62] == baseLight, "Removing all property layers did not restore the base value");

        owner.invoke(null, "property_equal_first.lua");
        lua.load("propertyFirst = furnace:override {priority=4, light=4}").call();
        owner.invoke(null, "property_equal_second.lua");
        lua.load("propertySecond = furnace:override {priority=4, light=5}").call();
        require(Block.lightValue[62] == 5, "Later equal-priority property layer did not win");
        lua.load("propertySecond:remove()").call();
        require(Block.lightValue[62] == 4, "Removing an equal-priority layer did not reveal its predecessor");
        ScriptResourceTracker.unload("property_equal_first.lua");
        require(Block.lightValue[62] == baseLight, "Equal-priority cleanup did not restore the base value");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void verify(Method dispatch, TestWorld world, String... expected) throws Exception {
        world.particles.clear();
        dispatch.invoke(null, Block.stoneOvenActive, world, new Random(1));
        if (!world.particles.equals(Arrays.asList(expected))) {
            throw new AssertionError("Expected " + Arrays.toString(expected) + ", got " + world.particles);
        }
    }

    private static Method createDispatch(String mappingsPath) throws Exception {
        TinyMappingResolver mappings;
        try (FileInputStream input = new FileInputStream(mappingsPath)) {
            mappings = TinyMappingResolver.read(input);
        }
        String owner = "fixture/DisplayDispatch";
        String descriptor = "(Lnet/minecraft/src/Block;Lnet/minecraft/src/World;Ljava/util/Random;)V";
        String original = "(Lnet/minecraft/src/World;IIILjava/util/Random;)V";
        TransformationReport report = new TransformationReport();
        HookRegistry registry = new HookRegistry(report);
        registry.register(
                new CallRedirectHookDefinition("test:display", new MethodRef(new ClassRef(owner), "run", descriptor),
                        new MethodRef(new ClassRef("net/minecraft/src/Block"), "randomDisplayTick", original),
                        HandlerRef.of("betamoon/luaapi/block/BlockDisplayTickOverrides", "display",
                                "(Lnet/minecraft/src/Block;Lnet/minecraft/src/World;IIILjava/util/Random;)V")));
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run", descriptor, null,
                null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitInsn(Opcodes.ICONST_0);
        method.visitInsn(Opcodes.ICONST_0);
        method.visitInsn(Opcodes.ICONST_0);
        method.visitVarInsn(Opcodes.ALOAD, 2);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/src/Block", "randomDisplayTick", original, false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        BetaMoonTransformer transformer = new BetaMoonTransformer(registry.freeze(mappings, RuntimeNamespace.NAMED),
                mappings, report, true, false);
        final byte[] bytes = transformer.transform(null, owner, null, null, writer.toByteArray());
        if (transformer.transform(null, owner, null, null, bytes) != null) {
            throw new AssertionError("Display redirect is not idempotent");
        }
        class Loader extends ClassLoader {
            Class<?> fixture() {
                return defineClass("fixture.DisplayDispatch", bytes, 0, bytes.length);
            }
        }
        return new Loader().fixture().getMethod("run", Block.class, World.class, Random.class);
    }

    private static final class TestWorld extends World {
        private final List<String> particles = new ArrayList<String>();

        private TestWorld() {
            super(null, "display_test", new WorldProvider() {
            }, 0L);
        }

        protected IChunkProvider getChunkProvider() {
            return null;
        }

        public int getBlockId(int x, int y, int z) {
            return 62;
        }

        public int getBlockMetadata(int x, int y, int z) {
            return 3;
        }

        public TileEntity getBlockTileEntity(int x, int y, int z) {
            return null;
        }

        public void spawnParticle(String name, double x, double y, double z, double vx, double vy, double vz) {
            particles.add(name);
        }
    }
}

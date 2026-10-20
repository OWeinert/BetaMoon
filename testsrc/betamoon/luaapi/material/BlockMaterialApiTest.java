package betamoon.luaapi.material;

import betamoon.fluid.FluidCarrier;
import betamoon.luamodloader.LuaScriptRegistry;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.src.Block;
import net.minecraft.src.Material;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Native material identity, validation, fluid carrier, and save preflight checks. */
public final class BlockMaterialApiTest {
    private BlockMaterialApiTest() {
    }

    public static void main(String[] arguments) throws Exception {
        owner("materials_test.lua");
        try {
            BlockMaterialRegistry.Entry acid = BlockMaterialRegistry.register(definition("testmod:material/acid",
                    "liquid", "water"));
            BlockMaterialRegistry.Entry brine = BlockMaterialRegistry.register(definition("testmod:material/brine",
                    "liquid", "water"));
            require(acid.material != brine.material, "Two custom liquids retain distinct native identity");
            require(acid.material.getIsLiquid() && !acid.material.isSolid() && !acid.material.getIsSolid(),
                    "Liquid has coherent native flags");
            require(!acid.material.getCanBlockGrass() && acid.material.getIsGroundCover(),
                    "Liquid uses native liquid ground and grass flags");
            require(acid.material.getMaterialMobility() == 1, "Liquid defaults to no-push mobility");
            require(BlockMaterialRegistry.register(definition("testmod:material/acid", "liquid", "water")) == acid,
                    "Reload retains the native material object");
            expect(LuaError.class, () -> BlockMaterialRegistry.register(definition("testmod:material/acid",
                    "liquid", "ice")));
            expect(LuaError.class, () -> BlockMaterialRegistry.register(definition("testmod:material/invalid",
                    "liquid", "missing")));
            expect(LuaError.class, () -> BlockMaterialRegistry.register(definition("testmod:material/invalid",
                    "unknown", "water")));

            LuaTable solid = definition("testmod:material/crystal", "solid", "stone");
            solid.set("harvestable", LuaValue.FALSE);
            solid.set("translucent", LuaValue.TRUE);
            BlockMaterialRegistry.Entry crystal = BlockMaterialRegistry.register(solid);
            require(crystal.material.isSolid() && crystal.material.getIsSolid(), "Solid methods agree");
            require(!crystal.material.getIsHarvestable() && !crystal.material.getIsTranslucent(),
                    "Solid property overrides reach native queries");
            solid.set("blocksGrass", LuaValue.FALSE);
            expect(LuaError.class, () -> BlockMaterialRegistry.register(solid));

            BlockMaterialRegistry.Entry transparent = BlockMaterialRegistry.register(
                    definition("testmod:material/smoke", "transparent", "air"));
            require(!transparent.material.getIsSolid() && !transparent.material.isSolid()
                    && transparent.material.getIsGroundCover() && !transparent.material.getCanBlockGrass(),
                    "Transparent preset follows native non-solid behavior");
            BlockMaterialRegistry.Entry logic = BlockMaterialRegistry.register(
                    definition("testmod:material/wire", "logic", "iron"));
            require(!logic.material.getIsSolid() && !logic.material.getIsGroundCover()
                    && logic.material.getMaterialMobility() == 1,
                    "Logic preset follows native non-solid and no-push behavior");
            LuaTable invalidFlags = definition("testmod:material/invalid_flags", "liquid", "water");
            invalidFlags.set("blocksGrass", LuaValue.TRUE);
            expect(LuaError.class, () -> BlockMaterialRegistry.register(invalidFlags));

            BlockMaterialRegistry.Entry water = BlockMaterialRegistry.resolve(LuaValue.valueOf("water"), "test");
            require(water.material == Material.water && !water.custom(), "Vanilla water retains its identity");
            LuaTable materialModule = new LuaTable();
            BlockMaterialApi.attach(materialModule);
            LuaValue materialApi = materialModule.get("blocks");
            LuaValue acidReference = materialApi.get("get").call(LuaValue.valueOf(acid.key));
            require(acidReference == materialApi.get("getRequired").call(LuaValue.valueOf(acid.key)),
                    "Custom material lookups retain their Lua reference identity");
            require(acidReference == materialApi.get("add").call(definition(acid.key, "liquid", "water")),
                    "A compatible reload returns the same Lua material reference");
            LuaValue waterReference = materialApi.get("get").call(LuaValue.valueOf("water"));
            require(waterReference == materialApi.get("getRequired").call(LuaValue.valueOf("water")),
                    "Vanilla material lookups retain their Lua reference identity");
            require(BlockMaterialRegistry.find("water") == water,
                    "Vanilla material lookup retains its native registry entry");
            BlockMaterialReference reference = new BlockMaterialReference(acid);
            require(BlockMaterialRegistry.resolve(reference, "test") == acid, "Reference resolves to the same material");
            require(BlockMaterialRegistry.resolve(LuaValue.valueOf(acid.key), "test") == acid,
                    "Canonical key resolves to the same material");
            expect(LuaError.class, () -> BlockMaterialRegistry.resolve(LuaValue.valueOf("missing"), "test"));

            FluidCarrier first = new FluidCarrier.Custom(acid.key, acid.signature, acid.material);
            FluidCarrier second = new FluidCarrier.Custom(brine.key, brine.signature, brine.material);
            require(first.material() != second.material() && !first.signature().equals(second.signature()),
                    "Fluid carriers preserve distinct custom material signatures");

            owner("another.lua");
            expect(LuaError.class, () -> BlockMaterialRegistry.register(definition("testmod:material/acid",
                    "liquid", "water")));
            owner("materials_test.lua");
            verifyWorldPreflight(acid, brine);
        } finally {
            owner(null);
        }
        System.out.println("Block material checks passed.");
    }

    private static void verifyWorldPreflight(BlockMaterialRegistry.Entry acid, BlockMaterialRegistry.Entry brine)
            throws IOException {
        int id = 250;
        require(Block.blocksList[id] == null, "Test block ID is available");
        Path world = Files.createTempDirectory("betamoon-materials-");
        try {
            new TestBlock(id, acid.material);
            WorldMaterialSignatures.record(world);
            WorldMaterialSignatures.preflight(world);
            Block.blocksList[id] = null;
            new TestBlock(id, brine.material);
            expect(IOException.class, () -> {
                try {
                    WorldMaterialSignatures.preflight(world);
                } catch (IOException error) {
                    throw new CheckedFailure(error);
                }
            });
        } finally {
            Block.blocksList[id] = null;
            Files.deleteIfExists(world.resolve("data/betamoon_materials.dat"));
            Files.deleteIfExists(world.resolve("data"));
            Files.deleteIfExists(world);
        }
    }

    private static LuaTable definition(String key, String kind, String color) {
        LuaTable result = new LuaTable();
        result.set("key", key);
        result.set("kind", kind);
        result.set("mapColor", color);
        return result;
    }

    private static void owner(String value) throws Exception {
        Method method = LuaScriptRegistry.class.getDeclaredMethod("setCurrentScriptFile", String.class);
        method.setAccessible(true);
        method.invoke(null, value);
    }

    private static void expect(Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable error) {
            Throwable actual = error instanceof CheckedFailure ? error.getCause() : error;
            if (type.isInstance(actual)) {
                return;
            }
            throw new AssertionError("Expected " + type.getName() + ", found " + actual, actual);
        }
        throw new AssertionError("Expected " + type.getName());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class TestBlock extends Block {
        private TestBlock(int id, Material material) {
            super(id, material);
        }
    }

    private static final class CheckedFailure extends RuntimeException {
        private CheckedFailure(IOException cause) {
            super(cause);
        }
    }
}

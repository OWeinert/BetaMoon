package betamoon.client.render;

import betamoon.content.ContentKey;
import betamoon.content.ContentType;
import betamoon.fluid.FluidAppearance;
import betamoon.fluid.FluidCarrier;
import betamoon.fluid.FluidDefinition;
import betamoon.fluid.FluidDisplacementPolicy;
import betamoon.fluid.FluidFlowPolicy;
import betamoon.fluid.FluidRegistry;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.src.IBlockAccess;
import net.minecraft.src.Material;
import net.minecraft.src.TileEntity;
import net.minecraft.src.WorldChunkManager;

/** Verifies that surface sampling uses fluid identity rather than carrier material. */
public final class FluidRendererTest {
    private FluidRendererTest() {
    }

    public static void main(String[] arguments) {
        FluidRegistry registry = FluidRegistry.instance();
        registry.publish(definition("render_first", 232, 236));
        registry.publish(definition("render_second", 233, 237));

        FakeAccess world = new FakeAccess();
        world.set(0, 0, 0, 232, 0);
        world.set(1, 0, 1, 233, 0);
        float withOtherFluid = FluidRenderer.cornerHeight(world, 232, 1, 0, 1);
        world.set(1, 0, 1, 0, 0);
        float withAir = FluidRenderer.cornerHeight(world, 232, 1, 0, 1);
        require(close(withOtherFluid, withAir),
                "A different fluid with the same water carrier must sample as a neighboring fluid, not this fluid");

        world.set(0, 1, 0, 233, 0);
        require(FluidRenderer.cornerHeight(world, 232, 0, 0, 0) < 1.0F,
                "A different fluid above the corner must not force a full-height surface");
        world.set(0, 1, 0, 236, 0);
        require(close(FluidRenderer.cornerHeight(world, 232, 0, 0, 0), 1.0F),
                "The explicitly paired same fluid above the corner forces a full-height surface");
        System.out.println("Fluid renderer identity checks passed.");
    }

    private static FluidDefinition definition(String name, int flowing, int still) {
        return new FluidDefinition(ContentKey.of("render_test", ContentType.FLUID, name), "render_test.lua",
                "neutral", FluidCarrier.WATER, FluidFlowPolicy.neutral(),
                new FluidAppearance(1, 2, 0xFFFFFF, 1, 0, 3), FluidDisplacementPolicy.DROP,
                flowing, still, -1, false);
    }

    private static boolean close(float first, float second) {
        return Math.abs(first - second) < 0.0001F;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class FakeAccess implements IBlockAccess {
        private final Map<String, Cell> cells = new HashMap<String, Cell>();

        private void set(int x, int y, int z, int id, int metadata) {
            if (id == 0) {
                cells.remove(key(x, y, z));
            } else {
                cells.put(key(x, y, z), new Cell(id, metadata));
            }
        }

        @Override
        public int getBlockId(int x, int y, int z) {
            Cell cell = cells.get(key(x, y, z));
            return cell == null ? 0 : cell.id;
        }

        @Override
        public int getBlockMetadata(int x, int y, int z) {
            Cell cell = cells.get(key(x, y, z));
            return cell == null ? 0 : cell.metadata;
        }

        @Override
        public Material getBlockMaterial(int x, int y, int z) {
            return getBlockId(x, y, z) == 0 ? Material.air : Material.water;
        }

        @Override
        public TileEntity getBlockTileEntity(int x, int y, int z) {
            return null;
        }

        @Override
        public float getBrightness(int x, int y, int z, int minimum) {
            return 1.0F;
        }

        @Override
        public float getLightBrightness(int x, int y, int z) {
            return 1.0F;
        }

        @Override
        public boolean isBlockOpaqueCube(int x, int y, int z) {
            return false;
        }

        @Override
        public boolean isBlockNormalCube(int x, int y, int z) {
            return false;
        }

        @Override
        public WorldChunkManager getWorldChunkManager() {
            return null;
        }

        private static String key(int x, int y, int z) {
            return x + ":" + y + ":" + z;
        }
    }

    private static final class Cell {
        private final int id;
        private final int metadata;

        private Cell(int id, int metadata) {
            this.id = id;
            this.metadata = metadata;
        }
    }
}

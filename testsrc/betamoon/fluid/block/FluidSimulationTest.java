package betamoon.fluid.block;

import betamoon.content.ContentKey;
import betamoon.content.ContentType;
import betamoon.fluid.FluidAppearance;
import betamoon.fluid.FluidCarrier;
import betamoon.fluid.FluidDefinition;
import betamoon.fluid.FluidDisplacementPolicy;
import betamoon.fluid.FluidFlowPolicy;
import betamoon.fluid.FluidRegistry;
import java.util.Random;
import net.minecraft.src.Block;
import net.minecraft.src.Chunk;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.IProgressUpdate;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;

/** Regression checks for choosing flow directions on flat and sloped ground. */
public final class FluidSimulationTest {
    private static final int NO_PATH = 1000;

    private FluidSimulationTest() {
    }

    public static void main(String[] arguments) {
        verifyDirectionChoices();
        verifyWorldFlow();
        System.out.println("Fluid simulation passed: flat spread, barriers, and downhill priority.");
    }

    private static void verifyDirectionChoices() {
        boolean[] flat = FluidSimulation.chooseDirections(
                new int[]{NO_PATH, NO_PATH, NO_PATH, NO_PATH},
                new boolean[]{true, true, true, true});
        require(flat[0] && flat[1] && flat[2] && flat[3],
                "A source must spread in every open direction on a flat floor");

        boolean[] walled = FluidSimulation.chooseDirections(
                new int[]{NO_PATH, NO_PATH, NO_PATH, NO_PATH},
                new boolean[]{true, false, true, false});
        require(walled[0] && !walled[1] && walled[2] && !walled[3],
                "Flat-floor fallback must exclude walls and neighboring sources");

        boolean[] downhill = FluidSimulation.chooseDirections(
                new int[]{2, 0, NO_PATH, 0},
                new boolean[]{true, true, false, true});
        require(!downhill[0] && downhill[1] && !downhill[2] && downhill[3],
                "A downhill route must take priority over flat directions");
    }

    private static void verifyWorldFlow() {
        int flowingId = freeBlockId();
        int stillId = freeBlockId(flowingId);
        FluidAppearance appearance = new FluidAppearance(1, 2, 0xFFFFFF, 1, 0, 3);
        FlowingFluidBlock flowing = new FlowingFluidBlock(flowingId, FluidCarrier.WATER.material(), appearance);
        new StillFluidBlock(stillId, FluidCarrier.WATER.material(), appearance);
        FluidRegistry.instance().publish(new FluidDefinition(
                ContentKey.of("simulation_test", ContentType.FLUID, "flat_and_downhill"),
                "fluid_simulation_test.lua", "neutral", FluidCarrier.WATER, FluidFlowPolicy.neutral(),
                appearance, FluidDisplacementPolicy.DROP, flowingId, stillId, -1, false));

        TestWorld flat = new TestWorld();
        flat.fillFloor();
        flat.setBlockAndMetadata(8, 64, 8, flowingId, 0);
        FluidSimulation.update(flowing, flat, 8, 64, 8, new Random(0L));
        require(flat.getBlockId(7, 64, 8) == flowingId && flat.getBlockMetadata(7, 64, 8) == 1
                        && flat.getBlockId(9, 64, 8) == flowingId && flat.getBlockMetadata(9, 64, 8) == 1
                        && flat.getBlockId(8, 64, 7) == flowingId && flat.getBlockMetadata(8, 64, 7) == 1
                        && flat.getBlockId(8, 64, 9) == flowingId && flat.getBlockMetadata(8, 64, 9) == 1,
                "A real source update must spread over a level solid floor");

        TestWorld downhill = new TestWorld();
        downhill.fillFloor();
        downhill.setBlock(9, 63, 8, 0);
        downhill.setBlockAndMetadata(8, 64, 8, flowingId, 0);
        FluidSimulation.update(flowing, downhill, 8, 64, 8, new Random(0L));
        require(downhill.getBlockId(9, 64, 8) == flowingId
                        && downhill.getBlockId(7, 64, 8) == 0
                        && downhill.getBlockId(8, 64, 7) == 0
                        && downhill.getBlockId(8, 64, 9) == 0,
                "A real source update must prefer the downhill opening");
        FluidSimulation.update(flowing, downhill, 9, 64, 8, new Random(0L));
        require(downhill.getBlockId(9, 63, 8) == flowingId
                        && downhill.getBlockMetadata(9, 63, 8) >= 8,
                "Flowing fluid must enter the opening as a falling state");
    }

    private static int freeBlockId(int... excluded) {
        for (int id = 230; id >= 1; id--) {
            if (Block.blocksList[id] != null || FluidRegistry.instance().getByBlockId(id) != null) {
                continue;
            }
            boolean available = true;
            for (int used : excluded) {
                available &= id != used;
            }
            if (available) {
                return id;
            }
        }
        throw new AssertionError("No free block ID for fluid simulation test");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class TestWorld extends World {
        private final Chunk chunk;

        private TestWorld() {
            super(null, "fluid_simulation_test", new WorldProvider() { }, 0L);
            chunk = new Chunk(this, new byte[32768], 0, 0);
            chunk.isChunkLoaded = true;
        }

        private void fillFloor() {
            for (int x = 3; x <= 13; x++) {
                for (int z = 3; z <= 13; z++) {
                    setBlock(x, 63, z, Block.stone.blockID);
                }
            }
        }

        @Override
        protected IChunkProvider getChunkProvider() {
            return new IChunkProvider() {
                @Override
                public boolean chunkExists(int x, int z) {
                    return x == 0 && z == 0;
                }

                @Override
                public Chunk provideChunk(int x, int z) {
                    return chunk;
                }

                @Override
                public Chunk prepareChunk(int x, int z) {
                    return chunk;
                }

                @Override
                public void populate(IChunkProvider provider, int x, int z) {
                }

                @Override
                public boolean saveChunks(boolean force, IProgressUpdate progress) {
                    return true;
                }

                @Override
                public boolean unload100OldestChunks() {
                    return false;
                }

                @Override
                public boolean canSave() {
                    return false;
                }

                @Override
                public String makeString() {
                    return "FluidSimulationTest";
                }
            };
        }

        @Override
        public Chunk getChunkFromChunkCoords(int x, int z) {
            return chunk;
        }

        @Override
        public boolean blockExists(int x, int y, int z) {
            return x >= 0 && x < 16 && z >= 0 && z < 16 && y >= 0 && y < 128;
        }
    }
}

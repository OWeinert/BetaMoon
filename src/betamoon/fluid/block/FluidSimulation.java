package betamoon.fluid.block;

import betamoon.fluid.FluidDefinition;
import betamoon.fluid.FluidDisplacementPolicy;
import betamoon.fluid.FluidFlowPolicy;
import betamoon.fluid.FluidRegistry;
import betamoon.fluid.FluidState;
import betamoon.fluid.interaction.FluidInteractionRegistry;
import java.util.Random;
import net.minecraft.src.Block;
import net.minecraft.src.Material;
import net.minecraft.src.World;

/** Bounded, registry-aware adaptation of the Beta 1.7.3 liquid algorithm. */
final class FluidSimulation {
    private static final int NO_PATH = 1000;
    private static final int[] OFFSET_X = {-1, 1, 0, 0};
    private static final int[] OFFSET_Z = {0, 0, -1, 1};
    private static final int[] OPPOSITE = {1, 0, 3, 2};

    private FluidSimulation() {
    }

    static void update(FlowingFluidBlock block, World world, int x, int y, int z, Random random) {
        if (!world.blockExists(x, y, z) || world.getBlockId(x, y, z) != block.blockID) {
            return;
        }
        FluidDefinition definition = block.definition();
        FluidFlowPolicy flow = definition.flow();
        int current = decay(definition, world, x, y, z);
        boolean settle = true;

        if (current > 0) {
            AdjacentDecay adjacent = smallestAdjacentDecay(definition, world, x, y, z);
            int next = adjacent.decay < 0 ? -1 : adjacent.decay + levelStep(world, definition);
            if (next >= 8) {
                next = -1;
            }

            int above = decay(definition, world, x, y + 1, z);
            if (above >= 0) {
                next = above >= 8 ? above : above + 8;
            }
            if (adjacent.sources >= 2 && flow.sourceCreation() == FluidFlowPolicy.SourceCreation.TWO_ADJACENT
                    && hasSourceSupport(definition, world, x, y, z)) {
                next = 0;
            }
            if (shouldDelayRetreat(current, next, flow.retreatDelayChance(), random)) {
                next = current;
                settle = false;
            }

            if (next != current) {
                current = next;
                if (next < 0) {
                    world.setBlockWithNotify(x, y, z, 0);
                } else {
                    world.setBlockMetadataWithNotify(x, y, z, next);
                    world.scheduleBlockUpdate(x, y, z, block.blockID, block.tickRate(world));
                    world.notifyBlocksOfNeighborChange(x, y, z, block.blockID);
                }
            } else if (settle) {
                settle(definition, world, x, y, z);
            }
        } else {
            settle(definition, world, x, y, z);
        }

        if (canDisplace(definition, world, x, y - 1, z)) {
            int falling = current >= 8 ? current : current + 8;
            displace(definition, world, x, y - 1, z, falling);
        } else if (current >= 0 && (current == 0 || blocksFlow(definition, world, x, y - 1, z))) {
            boolean[] optimal = optimalDirections(definition, world, x, y, z);
            int next = current >= 8 ? 1 : current + levelStep(world, definition);
            if (next >= 8) {
                return;
            }
            for (int direction = 0; direction < optimal.length; direction++) {
                if (optimal[direction]) {
                    displace(definition, world, x + OFFSET_X[direction], y, z + OFFSET_Z[direction], next);
                }
            }
        }
    }

    private static int levelStep(World world, FluidDefinition definition) {
        return definition.flow().levelStep(world.worldProvider.isHellWorld);
    }

    private static boolean shouldDelayRetreat(int current, int next, float chance, Random random) {
        return current < 8 && next < 8 && next > current && chance > 0.0F && random.nextFloat() < chance;
    }

    private static boolean hasSourceSupport(FluidDefinition definition, World world, int x, int y, int z) {
        Material below = world.getBlockMaterial(x, y - 1, z);
        return below.isSolid() || decay(definition, world, x, y - 1, z) == 0;
    }

    private static void settle(FluidDefinition definition, World world, int x, int y, int z) {
        int metadata = world.getBlockMetadata(x, y, z);
        world.setBlockAndMetadata(x, y, z, definition.stillBlockId(), metadata);
        world.markBlocksDirty(x, y, z, x, y, z);
        world.markBlockNeedsUpdate(x, y, z);
    }

    private static AdjacentDecay smallestAdjacentDecay(FluidDefinition definition, World world, int x, int y, int z) {
        int smallest = -100;
        int sources = 0;
        for (int direction = 0; direction < 4; direction++) {
            int value = decay(definition, world, x + OFFSET_X[direction], y, z + OFFSET_Z[direction]);
            if (value < 0) {
                continue;
            }
            if (value == 0) {
                sources++;
            }
            if (value >= 8) {
                value = 0;
            }
            if (smallest < 0 || value < smallest) {
                smallest = value;
            }
        }
        return new AdjacentDecay(smallest, sources);
    }

    private static int decay(FluidDefinition definition, World world, int x, int y, int z) {
        if (!world.blockExists(x, y, z)) {
            return -1;
        }
        int blockId = world.getBlockId(x, y, z);
        if (blockId != definition.flowingBlockId() && blockId != definition.stillBlockId()) {
            return -1;
        }
        return world.getBlockMetadata(x, y, z);
    }

    private static boolean[] optimalDirections(FluidDefinition definition, World world, int x, int y, int z) {
        int[] costs = new int[4];
        boolean[] candidates = new boolean[4];
        for (int direction = 0; direction < 4; direction++) {
            int targetX = x + OFFSET_X[direction];
            int targetZ = z + OFFSET_Z[direction];
            costs[direction] = NO_PATH;
            if (!blocksFlow(definition, world, targetX, y, targetZ)
                    && decay(definition, world, targetX, y, targetZ) != 0) {
                candidates[direction] = true;
                costs[direction] = !blocksFlow(definition, world, targetX, y - 1, targetZ)
                        ? 0
                        : flowCost(definition, world, targetX, y, targetZ, 1, direction);
            }
        }

        return chooseDirections(costs, candidates);
    }

    /** Spreads over open level ground when every candidate lacks a downhill route. */
    static boolean[] chooseDirections(int[] costs, boolean[] candidates) {
        int minimum = NO_PATH;
        for (int direction = 0; direction < costs.length; direction++) {
            if (candidates[direction]) {
                minimum = Math.min(minimum, costs[direction]);
            }
        }
        boolean[] optimal = new boolean[4];
        for (int direction = 0; direction < 4; direction++) {
            optimal[direction] = candidates[direction] && costs[direction] == minimum;
        }
        return optimal;
    }

    private static int flowCost(FluidDefinition definition, World world, int x, int y, int z, int depth,
            int previousDirection) {
        int minimum = NO_PATH;
        for (int direction = 0; direction < 4; direction++) {
            if (direction == OPPOSITE[previousDirection]) {
                continue;
            }
            int targetX = x + OFFSET_X[direction];
            int targetZ = z + OFFSET_Z[direction];
            if (blocksFlow(definition, world, targetX, y, targetZ)
                    || decay(definition, world, targetX, y, targetZ) == 0) {
                continue;
            }
            if (!blocksFlow(definition, world, targetX, y - 1, targetZ)) {
                return depth;
            }
            if (depth < definition.flow().slopeSearch()) {
                minimum = Math.min(minimum,
                        flowCost(definition, world, targetX, y, targetZ, depth + 1, direction));
            }
        }
        return minimum;
    }

    private static boolean canDisplace(FluidDefinition definition, World world, int x, int y, int z) {
        if (!world.blockExists(x, y, z) || world.getBlockTileEntity(x, y, z) != null) {
            return false;
        }
        int blockId = world.getBlockId(x, y, z);
        if (blockId == 0) {
            return true;
        }
        if (FluidRegistry.instance().getByBlockId(blockId) != null) {
            return false;
        }
        if (world.getBlockMaterial(x, y, z) == Material.lava
                || definition.displacement() == FluidDisplacementPolicy.BLOCK) {
            return false;
        }
        return !blocksFlow(definition, world, x, y, z);
    }

    private static boolean blocksFlow(FluidDefinition definition, World world, int x, int y, int z) {
        if (!world.blockExists(x, y, z)) {
            return true;
        }
        int blockId = world.getBlockId(x, y, z);
        if (blockId == 0) {
            return false;
        }
        if (blockId == Block.doorWood.blockID || blockId == Block.doorSteel.blockID
                || blockId == Block.signPost.blockID || blockId == Block.ladder.blockID
                || blockId == Block.reed.blockID) {
            return true;
        }
        return Block.blocksList[blockId].blockMaterial.getIsSolid();
    }

    private static void displace(FluidDefinition definition, World world, int x, int y, int z, int metadata) {
        int targetFluidId = world.getBlockId(x, y, z);
        if (FluidRegistry.instance().getByBlockId(targetFluidId) != null) {
            FluidInteractionRegistry.instance().evaluateCell(world, x, y, z);
            return;
        }
        if (!canDisplace(definition, world, x, y, z)) {
            return;
        }
        int targetId = world.getBlockId(x, y, z);
        if (targetId > 0 && definition.displacement() == FluidDisplacementPolicy.DROP) {
            Block.blocksList[targetId].dropBlockAsItem(world, x, y, z, world.getBlockMetadata(x, y, z));
        }
        world.setBlockAndMetadataWithNotify(x, y, z, definition.flowingBlockId(), FluidState.of(metadata).metadata());
    }

    private static final class AdjacentDecay {
        private final int decay;
        private final int sources;

        private AdjacentDecay(int decay, int sources) {
            this.decay = decay;
            this.sources = sources;
        }
    }
}

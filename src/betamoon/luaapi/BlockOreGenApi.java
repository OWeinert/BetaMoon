package betamoon.luaapi;

import betamoon.registry.WorldGenRegistry;
import net.minecraft.src.Block;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

final class BlockOreGenApi {
    private BlockOreGenApi() {
    }

    static VarArgFunction createAddOreGen(BlockApi.BlockHandle handle) {
        return new AddOreGen(handle);
    }

    private static final class AddOreGen extends VarArgFunction {
        private final BlockApi.BlockHandle handle;

        private AddOreGen(BlockApi.BlockHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            int base = (args.narg() >= 4 && args.arg(1).istable()) ? 2 : 1;
            int veinsPerChunk = args.checkint(base);
            int veinSize = args.checkint(base + 1);
            int minY = args.checkint(base + 2);
            int maxY = args.checkint(base + 3);
            if (minY < 0 || maxY < 0 || minY > maxY) {
                throw new LuaError("Invalid ore Y range: " + minY + " to " + maxY);
            }
            return new OreGenHandle(handle, veinsPerChunk, veinSize, minY, maxY);
        }
    }

    private static final class OreGenHandle extends LuaTable {
        private final BlockApi.BlockHandle blockHandle;
        private final int veinsPerChunk;
        private final int veinSize;
        private final int minY;
        private final int maxY;
        private boolean nether;
        private int targetBlockId = -1;
        private String[] biomeNames;
        private boolean finished;

        private OreGenHandle(BlockApi.BlockHandle blockHandle, int veinsPerChunk, int veinSize, int minY, int maxY) {
            this.blockHandle = blockHandle;
            this.veinsPerChunk = veinsPerChunk;
            this.veinSize = veinSize;
            this.minY = minY;
            this.maxY = maxY;
            set("setDimension", new SetDimension(this));
            set("setSpawnBlock", new SetSpawnBlock(this));
            set("setBiomes", new SetBiomes(this));
            set("finishOreGen", new FinishOreGen(this));
        }
    }

    private static final class SetDimension extends VarArgFunction {
        private final OreGenHandle handle;

        private SetDimension(OreGenHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            ensureNotFinished(handle);
            String dim = LuaApiUtils.getStringArg(args, 1);
            if (dim.equalsIgnoreCase("nether") || dim.equalsIgnoreCase("hell")) {
                handle.nether = true;
            } else if (dim.equalsIgnoreCase("overworld")) {
                handle.nether = false;
            } else {
                throw new LuaError("Unknown dimension: " + dim);
            }
            return handle;
        }
    }

    private static final class SetSpawnBlock extends VarArgFunction {
        private final OreGenHandle handle;

        private SetSpawnBlock(OreGenHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            ensureNotFinished(handle);
            int base = (args.narg() >= 1 && args.arg(1).istable()) ? 2 : 1;
            int blockId = args.checkint(base);
            if (blockId < 0 || blockId >= Block.blocksList.length || Block.blocksList[blockId] == null) {
                throw new LuaError("Unknown spawn block id: " + blockId);
            }
            handle.targetBlockId = blockId;
            return handle;
        }
    }

    private static final class SetBiomes extends VarArgFunction {
        private final OreGenHandle handle;

        /**
         * Sets the allowed biomes for ore generation.
         * Accepted biome names (case-insensitive): Rainforest, Swampland, Seasonal Forest, Forest,
         * Savanna, Shrubland, Taiga, Desert, Plains, Ice Desert, Tundra, Hell, Sky.
         */
        private SetBiomes(OreGenHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            ensureNotFinished(handle);
            int base = (args.narg() >= 1 && args.arg(1).istable()) ? 2 : 1;
            LuaValue value = args.arg(base);
            if (!value.istable()) {
                throw new LuaError("Biomes must be provided as a table of names.");
            }
            LuaTable table = value.checktable();
            int len = table.length();
            String[] names = new String[len];
            for (int i = 1; i <= len; i++) {
                names[i - 1] = table.get(i).checkjstring();
            }
            handle.biomeNames = names;
            return handle;
        }
    }

    private static final class FinishOreGen extends VarArgFunction {
        private final OreGenHandle handle;

        private FinishOreGen(OreGenHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            ensureNotFinished(handle);
            handle.finished = true;
            int targetBlockId = handle.targetBlockId;
            if (targetBlockId < 0) {
                targetBlockId = handle.nether ? Block.netherrack.blockID : Block.stone.blockID;
            }
            WorldGenRegistry.addOreGen(handle.blockHandle.block.blockID, handle.veinsPerChunk, handle.veinSize,
                handle.minY, handle.maxY, handle.nether, targetBlockId,
                WorldGenRegistry.resolveBiomes(handle.biomeNames));
            return handle.blockHandle;
        }
    }

    private static void ensureNotFinished(OreGenHandle handle) {
        if (handle.finished) {
            throw new LuaError("Ore generation handle already finished.");
        }
    }
}

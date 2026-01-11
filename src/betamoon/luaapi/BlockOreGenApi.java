package betamoon.luaapi;

import betamoon.worldgen.WorldGenRegistry;
import net.minecraft.src.Block;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

final class BlockOreGenApi {
    private BlockOreGenApi() {
    }

    /**
     * @deprecated Use betamoon.startWorldGen():addOreGen(...) instead.
     */
    @Deprecated
    static VarArgFunction createAddOreGen(BlockApi.BlockHandle handle) {
        return new AddOreGenForBlock(handle.block.blockID, handle);
    }

    static VarArgFunction createWorldGenAddOreGen(WorldGenApi.WorldGenHandle handle) {
        return new AddOreGenForWorldGen(handle);
    }

    @Deprecated
    private static final class AddOreGenForBlock extends VarArgFunction {
        private final int blockId;
        private final LuaValue finishReturnValue;

        private AddOreGenForBlock(int blockId, LuaValue finishReturnValue) {
            this.blockId = blockId;
            this.finishReturnValue = finishReturnValue;
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
            return new OreGenHandle(blockId, finishReturnValue, veinsPerChunk, veinSize, minY, maxY);
        }
    }

    private static final class AddOreGenForWorldGen extends VarArgFunction {
        private final WorldGenApi.WorldGenHandle handle;

        private AddOreGenForWorldGen(WorldGenApi.WorldGenHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            handle.ensureActive();
            int base = (args.narg() >= 5 && args.arg(1).istable()) ? 2 : 1;
            int blockId = resolveBlockId(args.arg(base));
            int veinsPerChunk = args.checkint(base + 1);
            int veinSize = args.checkint(base + 2);
            int minY = args.checkint(base + 3);
            int maxY = args.checkint(base + 4);
            if (minY < 0 || maxY < 0 || minY > maxY) {
                throw new LuaError("Invalid ore Y range: " + minY + " to " + maxY);
            }
            return new OreGenHandle(blockId, handle, veinsPerChunk, veinSize, minY, maxY);
        }
    }

    private static final class OreGenHandle extends LuaTable {
        private final int blockId;
        private final LuaValue finishReturnValue;
        private final int veinsPerChunk;
        private final int veinSize;
        private final int minY;
        private final int maxY;
        private boolean nether;
        private int targetBlockId = -1;
        private String[] biomeNames;
        private boolean finished;

        private OreGenHandle(int blockId, LuaValue finishReturnValue, int veinsPerChunk, int veinSize, int minY,
            int maxY) {
            this.blockId = blockId;
            this.finishReturnValue = finishReturnValue;
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
            if (len == 0) {
                handle.biomeNames = null;
                return handle;
            }
            String[] names = new String[len];
            for (int i = 1; i <= len; i++) {
                String name = table.get(i).checkjstring();
                names[i - 1] = name.trim().toLowerCase();
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
            WorldGenRegistry.addOreGen(handle.blockId, handle.veinsPerChunk, handle.veinSize,
                handle.minY, handle.maxY, handle.nether, targetBlockId,
                WorldGenRegistry.resolveBiomes(handle.biomeNames));
            return handle.finishReturnValue;
        }
    }

    private static void ensureNotFinished(OreGenHandle handle) {
        if (handle.finished) {
            throw new LuaError("Ore generation handle already finished.");
        }
    }

    private static int resolveBlockId(LuaValue value) {
        if (value.isnumber()) {
            int id = value.toint();
            if (id < 0 || id >= Block.blocksList.length) {
                throw new LuaError("Block id out of range: " + id);
            }
            return id;
        }
        if (value.istable()) {
            LuaValue idValue = value.get("id");
            if (!idValue.isnil()) {
                return resolveBlockId(idValue);
            }
            LuaValue getter = value.get("getId");
            if (!getter.isnil()) {
                return resolveBlockId(getter.call(value));
            }
        }
        throw new LuaError("Block must be a block id or block handle.");
    }
}

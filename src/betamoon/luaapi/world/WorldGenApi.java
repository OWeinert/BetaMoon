package betamoon.luaapi.world;

import betamoon.worldgen.BiomeGenRegistry;
import betamoon.worldgen.WorldGenRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.src.BiomeGenBase;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Lua entrypoint for world generation configuration.
 *
 * <p>Exposes a scoped handle so mods can group multiple world-gen registrations
 * and finish them explicitly.</p>
 */
public final class WorldGenApi {
    /**
     * Utility class that installs world-gen-related Lua bindings.
     */
    private WorldGenApi() {
    }

    public static void attach(LuaTable module) {
        module.set("startWorldGen", new StartWorldGen());
    }

    private static final class StartWorldGen extends VarArgFunction {
        public Varargs invoke(Varargs args) {
            return new WorldGenHandle();
        }
    }

    static final class WorldGenHandle extends LuaTable {
        private final List pendingOreEntries = new ArrayList();
        private final List pendingBiomeEntries = new ArrayList();

        private WorldGenHandle() {
            // Register world-gen sub-features on a single handle for Lua chaining.
            set("addOreGen", OreGenApi.createWorldGenAddOreGen(this));
            set("addBiomeGen", BiomeGenApi.createBiomeGen(this));
            set("addBiomeGenFromDefault", BiomeGenApi.createBiomeGenFromDefault(this));
            set("finishWorldGen", new FinishWorldGen(this));
        }

        void addOreGenEntry(int blockId, int veinsPerChunk, int veinSize, int minY, int maxY, boolean nether,
            int targetBlockId, BiomeGenBase[] allowedBiomes) {
            pendingOreEntries.add(new PendingOreEntry(blockId, veinsPerChunk, veinSize, minY, maxY, nether,
                targetBlockId, allowedBiomes));
        }

        void addBiomeGenEntry(BiomeGenBase biome, double minTemperature, double maxTemperature,
            double minHumidity, double maxHumidity) {
            pendingBiomeEntries.add(new PendingBiomeEntry(biome, minTemperature, maxTemperature, minHumidity,
                maxHumidity));
        }

        void flushPending() {
            for (int i = 0; i < pendingOreEntries.size(); i++) {
                PendingOreEntry entry = (PendingOreEntry) pendingOreEntries.get(i);
                WorldGenRegistry.addOreGen(entry.blockId, entry.veinsPerChunk, entry.veinSize, entry.minY, entry.maxY,
                    entry.nether, entry.targetBlockId, entry.allowedBiomes);
            }
            pendingOreEntries.clear();
            for (int i = 0; i < pendingBiomeEntries.size(); i++) {
                PendingBiomeEntry entry = (PendingBiomeEntry) pendingBiomeEntries.get(i);
                BiomeGenRegistry.registerBiomeGenerator(entry.biome, entry.minTemperature, entry.maxTemperature,
                    entry.minHumidity, entry.maxHumidity);
            }
            pendingBiomeEntries.clear();
        }
    }

    private static final class PendingOreEntry {
        private final int blockId;
        private final int veinsPerChunk;
        private final int veinSize;
        private final int minY;
        private final int maxY;
        private final boolean nether;
        private final int targetBlockId;
        private final BiomeGenBase[] allowedBiomes;

        private PendingOreEntry(int blockId, int veinsPerChunk, int veinSize, int minY, int maxY, boolean nether,
            int targetBlockId, BiomeGenBase[] allowedBiomes) {
            this.blockId = blockId;
            this.veinsPerChunk = veinsPerChunk;
            this.veinSize = veinSize;
            this.minY = minY;
            this.maxY = maxY;
            this.nether = nether;
            this.targetBlockId = targetBlockId;
            this.allowedBiomes = allowedBiomes;
        }
    }

    private static final class PendingBiomeEntry {
        private final BiomeGenBase biome;
        private final double minTemperature;
        private final double maxTemperature;
        private final double minHumidity;
        private final double maxHumidity;

        private PendingBiomeEntry(BiomeGenBase biome, double minTemperature, double maxTemperature,
            double minHumidity, double maxHumidity) {
            this.biome = biome;
            this.minTemperature = minTemperature;
            this.maxTemperature = maxTemperature;
            this.minHumidity = minHumidity;
            this.maxHumidity = maxHumidity;
        }
    }

    private static final class FinishWorldGen extends VarArgFunction {
        private final WorldGenHandle handle;

        private FinishWorldGen(WorldGenHandle handle) {
            this.handle = handle;
        }

        public Varargs invoke(Varargs args) {
            handle.flushPending();
            return LuaValue.NIL;
        }
    }
}

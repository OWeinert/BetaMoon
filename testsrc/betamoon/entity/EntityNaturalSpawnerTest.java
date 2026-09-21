package betamoon.entity;

import net.minecraft.src.Block;
import net.minecraft.src.Chunk;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.IProgressUpdate;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/** Verifies bounded natural-spawn rule parsing and environmental matching. */
public final class EntityNaturalSpawnerTest {
    private EntityNaturalSpawnerTest() {
    }

    public static void main(String[] arguments) {
        require(Block.stone != null, "Minecraft blocks must initialize");
        TestWorld world = new TestWorld();
        require(world.setBlockWithNotify(4, 63, 4, Block.stone.blockID),
                "The spawn substrate must enter the fixture chunk");
        String biome = world.getWorldChunkManager().getBiomeGenAt(4, 4).biomeName;
        Globals lua = JsePlatform.standardGlobals();
        lua.set("fixtureBiome", LuaValue.valueOf(biome));
        EntitySpawnDefinition rule = new EntitySpawnDefinition(lua.load("return {"
                + "category='hostile',weight=13,group={min=2,max=4},cap=7,"
                + "light={min=0,max=15},height={min=60,max=70},dimensions={0},"
                + "biomes={fixtureBiome},substrates={1},despawn='persistent'}").call(), true);
        require(rule.category == EntitySpawnDefinition.Category.HOSTILE && rule.weight == 13
                && rule.groupMin == 2 && rule.groupMax == 4 && rule.cap == 7 && !rule.nativeDespawn,
                "Spawn rules must retain category, weighting, group, cap, and despawn policy");
        require(EntityNaturalSpawner.matches(world, rule, 4, 64, 4),
                "A loaded position satisfying all spawn filters must match");
        require(!EntityNaturalSpawner.matches(world, rule, 4, 59, 4),
                "The height filter must reject positions outside its range");
        require(world.setBlockWithNotify(4, 63, 4, Block.dirt.blockID)
                        && !EntityNaturalSpawner.matches(world, rule, 4, 64, 4),
                "The substrate filter must reject a different solid block");
        world.unavailable = true;
        require(!EntityNaturalSpawner.matches(world, rule, 4, 64, 4),
                "Natural spawning must not force unavailable chunks to load");
        System.out.println("Entity natural spawning passed: rule parsing and loaded-world filters.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class TestWorld extends World {
        private final Chunk chunk;
        private boolean unavailable;

        private TestWorld() {
            super(null, "entity_spawn_test", new WorldProvider() {
            }, 0L);
            chunk = new Chunk(this, new byte[32768], 0, 0);
            chunk.isChunkLoaded = true;
        }

        @Override
        protected IChunkProvider getChunkProvider() {
            return new IChunkProvider() {
                public boolean chunkExists(int x, int z) {
                    return true;
                }

                public Chunk provideChunk(int x, int z) {
                    return chunk;
                }

                public Chunk prepareChunk(int x, int z) {
                    return chunk;
                }

                public void populate(IChunkProvider provider, int x, int z) {
                }

                public boolean saveChunks(boolean force, IProgressUpdate progress) {
                    return true;
                }

                public boolean unload100OldestChunks() {
                    return false;
                }

                public boolean canSave() {
                    return false;
                }

                public String makeString() {
                    return "entity spawn fixture";
                }
            };
        }

        @Override
        public Chunk getChunkFromChunkCoords(int x, int z) {
            return chunk;
        }

        @Override
        public boolean checkChunksExist(int x1, int y1, int z1, int x2, int y2, int z2) {
            return !unavailable;
        }
    }
}

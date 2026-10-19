package betamoon.loot;

import betamoon.assets.AssetKey;
import betamoon.worldgen.spatial.FieldContext;
import betamoon.worldgen.terrain.WorldGenerationRuntime;
import java.util.Map;
import net.minecraft.src.World;

/**
 * Detached position and pinned samplers passed unchanged through nested loot
 * tables.
 */
public final class LootContext {
    public final FieldContext fields;
    public final int x;
    public final int y;
    public final int z;
    private final Map<AssetKey, LootTableDefinition> definitions;

    public LootContext(FieldContext fields, int x, int y, int z) {
        this(fields, x, y, z, LootTableRegistry.captureDefinitions());
    }

    private LootContext(FieldContext fields, int x, int y, int z, Map<AssetKey, LootTableDefinition> definitions) {
        this.definitions = definitions;
        if (fields == null || Math.abs((long) x) > 30000000 || Math.abs((long) z) > 30000000 || y < 0 || y > 127) {
            throw new IllegalArgumentException("Loot context requires a valid world position");
        }
        this.fields = fields;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public LootTableDefinition table(AssetKey key) {
        return definitions.get(key);
    }

    public static LootContext at(World world, int x, int y, int z) {
        return new LootContext(WorldGenerationRuntime.fieldContext(world), x, y, z);
    }

    public static LootContext generationAt(World world, int x, int y, int z) {
        return new LootContext(WorldGenerationRuntime.fieldContext(world), x, y, z,
                WorldGenerationRuntime.lootDefinitions(world));
    }
}

package betamoon.worldgen.spatial;

import betamoon.dimension.DimensionKey;
import java.util.Map;

import static betamoon.worldgen.spatial.GenerationValues.error;
import static betamoon.worldgen.spatial.GenerationValues.fields;
import static betamoon.worldgen.spatial.GenerationValues.integer;
import static betamoon.worldgen.spatial.GenerationValues.required;
import static betamoon.worldgen.spatial.GenerationValues.string;

/**
 * Structural fixed anchor. Original spawn is supplied by the world manifest
 * instead.
 */
public final class AnchorDefinition {
    public final int x;
    public final int z;
    public final int dimension;
    public final String dimensionKey;

    public AnchorDefinition(String path, Map<String, Object> value) {
        fields(value, path, "key", "type", "dimension", "x", "z");
        if (!string(value, "type", "fixed", path).equals("fixed")) {
            throw error(path + ".type", "expected fixed");
        }
        dimensionKey = DimensionKey.parse(string(value, "dimension", "minecraft:overworld", path)).toString();
        dimension = dimensionKey.equals("minecraft:overworld") ? 0 : dimensionKey.equals("minecraft:nether") ? -1 : 2;
        x = integer(required(value, "x", path), path + ".x", -30000000, 30000000);
        z = integer(required(value, "z", path), path + ".z", -30000000, 30000000);
    }

    public static int dimension(Object value, String path) {
        DimensionKey key = DimensionKey.parse(string(value, path));
        if (key.equals(DimensionKey.OVERWORLD)) {
            return 0;
        }
        if (key.equals(DimensionKey.NETHER)) {
            return -1;
        }
        throw error(path, "custom dimension IDs are save-local; use canonical keys");
    }
}

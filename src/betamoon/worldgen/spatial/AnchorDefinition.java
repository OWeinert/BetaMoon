package betamoon.worldgen.spatial;

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

    public AnchorDefinition(String path, Map<String, Object> value) {
        fields(value, path, "key", "type", "dimension", "x", "z");
        if (!string(value, "type", "fixed", path).equals("fixed")) {
            throw error(path + ".type", "expected fixed");
        }
        dimension = value.containsKey("dimension") ? dimension(value.get("dimension"), path + ".dimension") : 0;
        x = integer(required(value, "x", path), path + ".x", -30000000, 30000000);
        z = integer(required(value, "z", path), path + ".z", -30000000, 30000000);
    }

    public static int dimension(Object value, String path) {
        if ("overworld".equals(value)) {
            return 0;
        }
        throw error(path, "only overworld is supported");
    }
}

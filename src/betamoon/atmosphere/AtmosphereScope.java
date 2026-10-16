package betamoon.atmosphere;

import betamoon.worldgen.WorldGenRegistry;
import betamoon.worldgen.terrain.WorldGenerationRuntime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.src.World;

import static betamoon.worldgen.spatial.GenerationValues.error;
import static betamoon.worldgen.spatial.GenerationValues.fields;
import static betamoon.worldgen.spatial.GenerationValues.integer;
import static betamoon.worldgen.spatial.GenerationValues.list;
import static betamoon.worldgen.spatial.GenerationValues.number;
import static betamoon.worldgen.spatial.GenerationValues.object;
import static betamoon.worldgen.spatial.GenerationValues.required;
import static betamoon.worldgen.spatial.GenerationValues.string;

/** Bounded spatial scopes queried without requesting world chunks. */
public final class AtmosphereScope {
    private final String type;
    private final int dimension;
    private final Set<String> biomes = new HashSet<String>();
    private final double[] center;
    private final double[] min;
    private final double[] max;
    private final double radius;
    private final String tag;

    public AtmosphereScope(Object declaration, boolean temporary, String path) {
        Map<String, Object> value = object(declaration, path);
        fields(value, path, "type", "id", "keys", "tags", "center", "radius", "min", "max", "tag");
        type = string(required(value, "type", path), path + ".type");
        dimension = value.containsKey("id") ? integer(value.get("id"), path + ".id", -128, 127) : 0;
        center = type.equals("sphere") ? point(required(value, "center", path), path + ".center") : null;
        min = type.equals("box") ? point(required(value, "min", path), path + ".min") : null;
        max = type.equals("box") ? point(required(value, "max", path), path + ".max") : null;
        radius = number(value, "radius", 1, path);
        tag = value.containsKey("tag") ? string(value.get("tag"), path + ".tag") : null;
        if (type.equals("sphere") && (radius <= 0 || radius > 4096)) {
            throw error(path, "sphere radius must be in (0,4096]");
        }
        if (type.equals("box")) {
            for (int i = 0; i < 3; i++) {
                if (min[i] >= max[i] || max[i] - min[i] > 8192) {
                    throw error(path, "box must have ordered bounds no more than 8192 blocks apart");
                }
            }
        }
        if (temporary
                ? !type.equals("sphere") && !type.equals("box")
                : !type.equals("dimension") && !type.equals("biome") && !type.equals("region_tag")) {
            throw error(path, "unsupported atmosphere scope type for this binding");
        }
        names(value.get("keys"), false, path);
        names(value.get("tags"), true, path);
        if (type.equals("biome") && biomes.isEmpty() || type.equals("region_tag") && tag == null) {
            throw error(path, "scope requires biome keys/tags or a region tag");
        }
    }

    public double weight(World world, double x, double y, double z, double blend) {
        if (type.equals("dimension")) {
            return world.worldProvider.worldType == dimension ? 1 : 0;
        }
        if (type.equals("biome")) {
            if (blend == 0) {
                return matches(world, x, z) ? 1 : 0;
            }
            int hits = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (matches(world, x + dx * blend, z + dz * blend)) {
                        hits++;
                    }
                }
            }
            return hits / 9.0;
        }
        if (type.equals("region_tag")) {
            return WorldGenRegistry.regionTagWeight(world, tag, x, y, z, blend);
        }
        double distance;
        if (type.equals("sphere")) {
            distance = StrictMath.sqrt(square(x - center[0]) + square(y - center[1]) + square(z - center[2])) - radius;
        } else {
            distance = StrictMath.sqrt(square(Math.max(min[0] - x, Math.max(0, x - max[0])))
                    + square(Math.max(min[1] - y, Math.max(0, y - max[1])))
                    + square(Math.max(min[2] - z, Math.max(0, z - max[2]))));
        }
        return distance <= 0 ? 1 : blend == 0 ? 0 : Math.max(0, 1 - distance / blend);
    }

    private boolean matches(World world, double x, double z) {
        return WorldGenerationRuntime.biomeMatches(world, (int) Math.floor(x), (int) Math.floor(z), biomes);
    }

    private void names(Object value, boolean tags, String path) {
        if (value != null) {
            for (Object name : list(value, path, 0, 64)) {
                biomes.add((tags ? "#" : "") + string(name, path));
            }
        }
    }

    private static double[] point(Object input, String path) {
        Map<String, Object> value = object(input, path);
        fields(value, path, "x", "y", "z");
        double[] point = {number(required(value, "x", path), path), number(required(value, "y", path), path),
                number(required(value, "z", path), path)};
        if (Math.abs(point[0]) > 30000000 || Math.abs(point[2]) > 30000000 || Math.abs(point[1]) > 4096) {
            throw error(path, "scope coordinates exceed world bounds");
        }
        return point;
    }

    private static double square(double value) {
        return value * value;
    }
}

package betamoon.dimension;

import betamoon.teleport.TeleportLocation;
import betamoon.teleport.TeleportPlatformDefinition;
import betamoon.worldgen.SeedMixer;
import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.WorldGenKind;
import betamoon.worldgen.spatial.GenerationValues;
import betamoon.worldgen.terrain.GenerationSignature;
import betamoon.worldgen.terrain.PopulationPolicy;
import betamoon.worldgen.terrain.TerrainRegistry;
import betamoon.worldgen.terrain.WorldGeneratorProfile;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Immutable structural declaration; resolved generator dependencies are pinned
 * separately.
 */
public final class DimensionDefinition {
    public final DimensionKey key;
    public final String owner;
    public final String displayName;
    public final String generatorType;
    public final String nativeBase;
    public final String profileKey;
    public final String populationKey;
    public final String seedType;
    public final String seedSalt;
    public final long fixedSeed;
    public final double coordinateScale;
    public final long initialTime;
    public final DimensionEnvironment environment;
    public final Map<String, Object> spawn;
    public final String respawnType;
    public final DimensionKey respawnDimension;
    public final Map<String, Object> declaration;
    private final String structuralSignature;

    @SuppressWarnings("unchecked")
    public DimensionDefinition(String owner, Map<String, Object> value) {
        this.owner = owner;
        key = DimensionKey
                .parse(GenerationValues.string(GenerationValues.required(value, "key", "dimension"), "dimension.key"));
        if (key.isNative()) {
            throw GenerationValues.error(key.toString(), "native dimensions cannot be replaced");
        }
        String path = key.toString();
        GenerationValues.fields(value, path, "key", "name", "displayName", "generator", "seed", "coordinateScale",
                "environment", "spawn", "respawn", "initialTime");
        displayName = value.containsKey("displayName")
                ? GenerationValues.string(value.get("displayName"), path + ".displayName")
                : GenerationValues.string(value, "name", path, path);
        if (displayName.length() > 128 || displayName.matches("(?s).*[\\p{Cntrl}\u00a7].*")) {
            throw GenerationValues.error(path + ".displayName", "expected 1..128 plain-text characters");
        }
        Map<String, Object> generator = GenerationValues.object(GenerationValues.required(value, "generator", path),
                path + ".generator");
        generatorType = DimensionEnvironment.choice(generator, "type", "builtin", path + ".generator", "builtin",
                "profile");
        if (generatorType.equals("builtin")) {
            GenerationValues.fields(generator, path + ".generator", "type", "name", "population");
            nativeBase = DimensionEnvironment.choice(generator, "name", "overworld", path + ".generator", "overworld",
                    "nether", "sky");
            profileKey = null;
            populationKey = generator.containsKey("population")
                    ? WorldGenKey
                            .parse(GenerationValues.string(generator.get("population"), path + ".generator.population"),
                                    WorldGenKind.POPULATION)
                            .toString()
                    : null;
        } else {
            GenerationValues.fields(generator, path + ".generator", "type", "profile");
            profileKey = WorldGenKey
                    .parse(GenerationValues.string(GenerationValues.required(generator, "profile", path + ".generator"),
                            path + ".generator.profile"), WorldGenKind.GENERATOR)
                    .toString();
            populationKey = null;
            nativeBase = "overworld";
        }
        Map<String, Object> seed = value.containsKey("seed")
                ? GenerationValues.object(value.get("seed"), path + ".seed")
                : Collections.<String, Object>emptyMap();
        GenerationValues.fields(seed, path + ".seed", "type", "salt", "value");
        seedType = DimensionEnvironment.choice(seed, "type", "derived", path + ".seed", "derived", "shared", "fixed");
        if (!seedType.equals("derived") && seed.containsKey("salt")
                || !seedType.equals("fixed") && seed.containsKey("value")) {
            throw GenerationValues.error(path + ".seed", "seed fields conflict with the chosen type");
        }
        Object declaredSalt = seed.get("salt");
        seedSalt = declaredSalt == null || "".equals(declaredSalt)
                ? ""
                : GenerationValues.string(declaredSalt, path + ".seed.salt");
        if (seedSalt.length() > 256) {
            throw GenerationValues.error(path + ".seed.salt", "expected at most 256 characters");
        }
        fixedSeed = seedType.equals("fixed")
                ? exactLong(GenerationValues.required(seed, "value", path + ".seed"), path + ".seed.value")
                : 0;
        coordinateScale = DimensionEnvironment.bounded(value, "coordinateScale", 1, 1.0 / 1024, 1024, path);
        initialTime = value.containsKey("initialTime") ? exactLong(value.get("initialTime"), path + ".initialTime") : 0;
        if (initialTime < 0) {
            throw GenerationValues.error(path + ".initialTime", "expected a nonnegative exact time");
        }
        environment = new DimensionEnvironment(value.containsKey("environment")
                ? GenerationValues.object(value.get("environment"), path + ".environment")
                : Collections.<String, Object>emptyMap(), path + ".environment");
        Map<String, Object> spawnValue = value.containsKey("spawn")
                ? GenerationValues.object(value.get("spawn"), path + ".spawn")
                : Collections.<String, Object>emptyMap();
        validateSpawn(spawnValue, path + ".spawn");
        spawn = (Map<String, Object>) GenerationValues.freeze(normalizedSpawn(spawnValue, path + ".spawn"));
        Map<String, Object> respawn = value.containsKey("respawn")
                ? GenerationValues.object(value.get("respawn"), path + ".respawn")
                : Collections.<String, Object>emptyMap();
        GenerationValues.fields(respawn, path + ".respawn", "type", "dimension", "fallbackDimension");
        respawnType = DimensionEnvironment.choice(respawn, "type", "dimension", path + ".respawn", "local", "dimension",
                "return");
        if (respawnType.equals("local")
                && (respawn.containsKey("dimension") || respawn.containsKey("fallbackDimension"))
                || respawnType.equals("return") && respawn.containsKey("dimension")
                || respawnType.equals("dimension") && respawn.containsKey("fallbackDimension")) {
            throw GenerationValues.error(path + ".respawn", "respawn fields conflict with the chosen type");
        }
        respawnDimension = DimensionKey.parse(GenerationValues.string(respawn,
                respawnType.equals("return") ? "fallbackDimension" : "dimension", "minecraft:overworld", path));
        declaration = (Map<String, Object>) GenerationValues.freeze(effectiveDeclaration());
        Map<String, Object> semantics = new LinkedHashMap<String, Object>(declaration);
        semantics.remove("displayName");
        Map<String, String> signature = new TreeMap<String, String>();
        signature.put("dimension_engine", "1");
        signature.put("declaration", GenerationValues.canonical(semantics));
        structuralSignature = GenerationSignature.of(signature);
    }

    private Map<String, Object> effectiveDeclaration() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("key", key.toString());
        result.put("displayName", displayName);
        Map<String, Object> generator = new LinkedHashMap<String, Object>();
        generator.put("type", generatorType);
        if (profileKey == null) {
            generator.put("name", nativeBase);
            if (populationKey != null) {
                generator.put("population", populationKey);
            }
        } else {
            generator.put("profile", profileKey);
        }
        result.put("generator", generator);
        Map<String, Object> seed = new LinkedHashMap<String, Object>();
        seed.put("type", seedType);
        if (seedType.equals("derived")) {
            seed.put("salt", seedSalt);
        } else if (seedType.equals("fixed")) {
            seed.put("value", Long.toString(fixedSeed));
        }
        result.put("seed", seed);
        result.put("coordinateScale", coordinateScale);
        result.put("initialTime", Long.toString(initialTime));
        result.put("environment", environment.describe());
        result.put("spawn", spawn);
        Map<String, Object> respawn = new LinkedHashMap<String, Object>();
        respawn.put("type", respawnType);
        if (!respawnType.equals("local")) {
            respawn.put(respawnType.equals("return") ? "fallbackDimension" : "dimension", respawnDimension.toString());
        }
        result.put("respawn", respawn);
        return result;
    }

    private static Map<String, Object> normalizedSpawn(Map<String, Object> value, String path) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        String type = DimensionEnvironment.choice(value, "type", "search", path, "search", "fixed");
        result.put("type", type);
        result.put("x", GenerationValues.number(value, "x", 0, path));
        result.put("z", GenerationValues.number(value, "z", 0, path));
        if (type.equals("fixed")) {
            result.put("y", GenerationValues.number(value.get("y"), path + ".y"));
        } else {
            result.put("radius",
                    value.containsKey("radius")
                            ? GenerationValues.integer(value.get("radius"), path + ".radius", 0, 256)
                            : 64);
        }
        result.put("yaw", TeleportLocation.normalizeYaw(GenerationValues.number(value, "yaw", 0, path)));
        result.put("pitch", GenerationValues.number(value, "pitch", 0, path));
        Map<String, Object> fallback = value.containsKey("fallback")
                ? GenerationValues.object(value.get("fallback"), path + ".fallback")
                : Collections.<String, Object>emptyMap();
        Map<String, Object> resolved = new LinkedHashMap<String, Object>();
        String fallbackType = DimensionEnvironment.choice(fallback, "type", "fail", path + ".fallback", "fail",
                "platform");
        resolved.put("type", fallbackType);
        if (fallbackType.equals("platform")) {
            TeleportPlatformDefinition platform = new TeleportPlatformDefinition(fallback, path + ".fallback");
            Map<String, Object> block = new LinkedHashMap<String, Object>();
            block.put("block", platform.block.id);
            block.put("metadata", platform.block.metadata);
            resolved.put("block", block);
            resolved.put("width", platform.width);
            resolved.put("depth", platform.depth);
            resolved.put("clearHeight", platform.clearHeight);
            resolved.put("y", platform.y);
        }
        result.put("fallback", resolved);
        return result;
    }

    private static void validateSpawn(Map<String, Object> value, String path) {
        String type = DimensionEnvironment.choice(value, "type", "search", path, "search", "fixed");
        GenerationValues.fields(value, path, "type", "x", "y", "z", "yaw", "pitch", "radius", "fallback");
        DimensionEnvironment.bounded(value, "x", 0, -29999999, 29999999, path);
        DimensionEnvironment.bounded(value, "z", 0, -29999999, 29999999, path);
        if (type.equals("fixed")) {
            GenerationValues.required(value, "x", path);
            GenerationValues.required(value, "y", path);
            GenerationValues.required(value, "z", path);
            DimensionEnvironment.bounded(value, "y", 65, 0, 127, path);
            if (value.containsKey("radius")) {
                throw GenerationValues.error(path + ".radius", "fixed spawn does not search a radius");
            }
        } else if (value.containsKey("y")) {
            throw GenerationValues.error(path + ".y", "search spawn resolves its height from terrain");
        }
        if (value.containsKey("radius")) {
            GenerationValues.integer(value.get("radius"), path + ".radius", 0, 256);
        }
        DimensionEnvironment.bounded(value, "yaw", 0, -1.0e12, 1.0e12, path);
        DimensionEnvironment.bounded(value, "pitch", 0, -90, 90, path);
        if (value.containsKey("fallback")) {
            Map<String, Object> fallback = GenerationValues.object(value.get("fallback"), path + ".fallback");
            String mode = DimensionEnvironment.choice(fallback, "type", "fail", path + ".fallback", "fail", "platform");
            if (mode.equals("fail")) {
                GenerationValues.fields(fallback, path + ".fallback", "type");
            } else {
                new TeleportPlatformDefinition(fallback, path + ".fallback");
            }
        }
    }

    public WorldGeneratorProfile resolveProfile(TerrainRegistry.Snapshot terrain) {
        return profileKey == null ? null : terrain.generator(profileKey);
    }

    public PopulationPolicy resolvePopulation(TerrainRegistry.Snapshot terrain) {
        return populationKey == null ? null : terrain.population(populationKey);
    }

    public String signature(WorldGeneratorProfile profile, PopulationPolicy policy) {
        Map<String, String> values = new TreeMap<String, String>();
        values.put("declaration", structuralSignature);
        if (profile != null) {
            values.put("generator", profile.signature);
        }
        if (policy != null) {
            values.put("population", policy.signature);
        }
        return GenerationSignature.of(values);
    }

    public long seed(long rootSeed) {
        if (seedType.equals("shared")) {
            return rootSeed;
        }
        if (seedType.equals("fixed")) {
            return fixedSeed;
        }
        return SeedMixer.derive(rootSeed, SeedMixer.hash("betamoon:dimension_seed_v1:" + key + ":" + seedSalt));
    }

    public static long exactLong(Object value, String path) {
        if (value instanceof String) {
            String text = (String) value;
            if (!text.matches("-?(0|[1-9][0-9]{0,18})")) {
                throw GenerationValues.error(path, "expected a signed decimal 64-bit string");
            }
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException exception) {
                throw GenerationValues.error(path, "integer exceeds signed 64-bit bounds");
            }
        }
        double number = GenerationValues.number(value, path);
        if (number != Math.floor(number) || Math.abs(number) > 9007199254740991L) {
            throw GenerationValues.error(path, "use a decimal string outside Lua's exact integer range");
        }
        return (long) number;
    }
}

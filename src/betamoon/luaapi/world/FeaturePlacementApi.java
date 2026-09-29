package betamoon.luaapi.world;

import betamoon.worldgen.BlockSet;
import betamoon.worldgen.BuiltInFeatures;
import betamoon.worldgen.GenerationStage;
import betamoon.worldgen.HeightProvider;
import betamoon.worldgen.IntRange;
import betamoon.worldgen.PlacementConditions;
import betamoon.worldgen.TerrainSurface;
import betamoon.worldgen.WorldFeature;
import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.WorldGenKind;
import betamoon.worldgen.WorldGenLimits;
import betamoon.worldgen.WorldGenRegistry;
import betamoon.worldgen.structure.SitePolicy;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.src.Block;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import static betamoon.luaapi.utils.LuaDeclarationValues.required;

/** Installs reusable feature and placement declaration registries. */
public final class FeaturePlacementApi {
    private FeaturePlacementApi() {
    }

    public static void attach(LuaTable worldgen) {
        final LuaTable features = new LuaTable();
        features.set("add", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                LuaValue definition = argument(arguments, features, 1);
                ParsedFeature parsed = feature(definition);
                WorldGenKey key = WorldGenRegistry.addFeature(parsed.key, parsed.type, parsed.feature,
                        parsed.dependencies, parsed.maxBlocks, parsed.maxRadius);
                return new FeatureReference(key);
            }
        });
        features.set("get", lookup(features, WorldGenKind.FEATURE, false));
        features.set("getRequired", lookup(features, WorldGenKind.FEATURE, true));
        worldgen.set("features", features);

        final LuaTable placements = new LuaTable();
        placements.set("add", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                ParsedPlacement parsed = placement(argument(arguments, placements, 1));
                WorldGenKey key = WorldGenRegistry.addPlacement(parsed.key, parsed.feature, parsed.stage,
                        parsed.dimensions, parsed.attempts, parsed.extraChance, parsed.probability, parsed.height,
                        parsed.conditions, parsed.before, parsed.after, parsed.priority, parsed.salt,
                        parsed.successLimit, parsed.horizontal, parsed.gridSpacing);
                return new PlacementReference(key, parsed.feature);
            }
        });
        placements.set("get", lookup(placements, WorldGenKind.PLACEMENT, false));
        placements.set("getRequired", lookup(placements, WorldGenKind.PLACEMENT, true));
        worldgen.set("placements", placements);
    }

    static void addBiomeDecorators(WorldGenKey biome, LuaValue decorators) {
        if (decorators.isnil()) {
            return;
        }
        table(decorators, "Biome.decorators");
        String biomePath = biome.getPath();
        int separator = biomePath.indexOf('/');
        String suffix = separator < 0 ? biomePath : biomePath.substring(separator + 1);
        for (int index = 1; index <= decorators.length(); index++) {
            String path = "Biome.decorators[" + index + "]";
            LuaValue source = decorators.get(index);
            String derivedKey = biome.getNamespace() + ":biome_decorators/" + suffix + "/"
                    + String.format(Locale.ROOT, "%02d", index);
            if (source instanceof PlacementReference || source.isstring()) {
                WorldGenKey placement = source instanceof PlacementReference
                        ? ((PlacementReference) source).key()
                        : key(source.checkjstring(), WorldGenKind.PLACEMENT, path);
                WorldGenRegistry.addBiomePlacement(derivedKey, placement, biome.toString());
                continue;
            }
            table(source, path);
            LuaTable definition = copy(source);
            if (definition.get("key").isnil()) {
                definition.set("key", derivedKey);
            }
            if (definition.get("stage").isnil()) {
                definition.set("stage", "surface_features");
            }
            LuaTable include = new LuaTable();
            include.set(1, biome.toString());
            LuaTable selectors = new LuaTable();
            selectors.set("include", include);
            definition.set("biomes", selectors);
            ParsedPlacement parsed = placement(definition);
            WorldGenRegistry.addPlacement(parsed.key, parsed.feature, parsed.stage, parsed.dimensions,
                    parsed.attempts, parsed.extraChance, parsed.probability, parsed.height, parsed.conditions,
                    parsed.before, parsed.after, parsed.priority, parsed.salt, parsed.successLimit,
                    parsed.horizontal, parsed.gridSpacing);
        }
    }

    private static LuaTable copy(LuaValue source) {
        LuaTable result = new LuaTable();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs entry = source.next(key);
            key = entry.arg1();
            if (key.isnil()) {
                return result;
            }
            result.set(key, entry.arg(2));
        }
    }

    private static VarArgFunction lookup(final LuaTable receiver, final WorldGenKind kind, final boolean required) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs arguments) {
                WorldGenKey key = key(argument(arguments, receiver, 1).checkjstring(), kind,
                        kind.getPath() + ".get");
                boolean exists = kind == WorldGenKind.FEATURE ? WorldGenRegistry.hasFeature(key)
                        : WorldGenRegistry.hasPlacement(key);
                if (!exists) {
                    if (required) {
                        throw new LuaError("World-generation key is not registered: " + key);
                    }
                    return NIL;
                }
                if (kind == WorldGenKind.FEATURE) {
                    return new FeatureReference(key);
                }
                WorldGenKey feature = WorldGenRegistry.featureKeyForPlacement(key);
                return new PlacementReference(key, feature);
            }
        };
    }

    private static ParsedFeature feature(LuaValue definition) {
        table(definition, "worldgen.features:add");
        String key = required(definition, "key").checkjstring();
        String type = required(definition, "type").checkjstring().trim().toLowerCase(Locale.ROOT);
        int blockId;
        int metadata;
        int maxBlocks;
        int maxRadius;
        WorldFeature feature;
        List<WorldGenKey> dependencies = new ArrayList<WorldGenKey>();
        if (type.equals("ore_vein")) {
            blockId = blockId(required(definition, "block"), "Feature.block");
            metadata = metadata(definition.get("metadata"), "Feature.metadata");
            int size = integer(required(definition, "size"), "Feature.size", 1, WorldGenLimits.MAX_ORE_VEIN_SIZE);
            BlockSet replace = blocks(required(definition, "replace"), "Feature.replace");
            feature = BuiltInFeatures.oreVein(blockId, metadata, size, replace);
            maxBlocks = Math.min(WorldGenLimits.MAX_BLOCK_CHANGES_PER_FEATURE, Math.max(64, size * 16));
            maxRadius = WorldGenLimits.MAX_FEATURE_RADIUS;
        } else if (type.equals("block_patch")) {
            blockId = blockId(required(definition, "block"), "Feature.block");
            metadata = metadata(definition.get("metadata"), "Feature.metadata");
            int radius = optionalInteger(definition.get("radius"), 4, "Feature.radius", 1,
                    WorldGenLimits.MAX_FEATURE_RADIUS);
            int tries = optionalInteger(definition.get("tries"), 64, "Feature.tries", 1,
                    WorldGenLimits.MAX_ATTEMPTS_PER_CHUNK);
            BlockSet replace = definition.get("replace").isnil() ? new BlockSet(0)
                    : blocks(definition.get("replace"), "Feature.replace");
            feature = BuiltInFeatures.patch(blockId, metadata, radius, tries, replace);
            maxBlocks = Math.min(tries, WorldGenLimits.MAX_BLOCK_CHANGES_PER_FEATURE);
            maxRadius = radius;
        } else if (type.equals("column")) {
            blockId = blockId(required(definition, "block"), "Feature.block");
            metadata = metadata(definition.get("metadata"), "Feature.metadata");
            IntRange height = range(required(definition, "height"), "Feature.height", 1,
                    WorldGenLimits.MAX_FEATURE_RADIUS);
            boolean downward = definition.get("direction").optjstring("up").equals("down");
            BlockSet replace = definition.get("replace").isnil() ? new BlockSet(0)
                    : blocks(definition.get("replace"), "Feature.replace");
            feature = BuiltInFeatures.column(blockId, metadata, height, downward, replace);
            maxBlocks = height.max;
            maxRadius = height.max;
        } else if (type.equals("disk")) {
            blockId = blockId(required(definition, "block"), "Feature.block");
            metadata = metadata(definition.get("metadata"), "Feature.metadata");
            IntRange radius = range(required(definition, "radius"), "Feature.radius", 1,
                    WorldGenLimits.MAX_FEATURE_RADIUS);
            int halfHeight = optionalInteger(definition.get("halfHeight"), 1, "Feature.halfHeight", 0, 8);
            BlockSet replace = blocks(required(definition, "replace"), "Feature.replace");
            feature = BuiltInFeatures.disk(blockId, metadata, radius, halfHeight, replace);
            maxBlocks = Math.min(WorldGenLimits.MAX_BLOCK_CHANGES_PER_FEATURE,
                    (radius.max * 2 + 1) * (radius.max * 2 + 1) * (halfHeight * 2 + 1));
            maxRadius = Math.max(radius.max, halfHeight);
        } else if (type.equals("weighted")) {
            LuaValue entries = required(definition, "features");
            table(entries, "Feature.features");
            int[] weights = new int[entries.length()];
            int total = 0;
            for (int index = 1; index <= entries.length(); index++) {
                LuaValue entry = entries.get(index);
                table(entry, "Feature.features[" + index + "]");
                dependencies.add(featureKey(required(entry, "feature"), "Feature.features[" + index + "].feature"));
                int weight = integer(required(entry, "weight"), "Feature.features[" + index + "].weight", 1,
                        1000000);
                if (total > Integer.MAX_VALUE - weight) {
                    throw new LuaError("Feature.features: total weight is too large");
                }
                total += weight;
                weights[index - 1] = total;
            }
            if (dependencies.isEmpty()) {
                throw new LuaError("Feature.features: expected at least one weighted feature");
            }
            feature = BuiltInFeatures.weighted(dependencies, weights);
            maxBlocks = optionalInteger(definition.get("maxBlocks"), WorldGenLimits.MAX_BLOCK_CHANGES_PER_FEATURE,
                    "Feature.maxBlocks", 1, WorldGenLimits.MAX_BLOCK_CHANGES_PER_FEATURE);
            maxRadius = optionalInteger(definition.get("maxRadius"), WorldGenLimits.MAX_FEATURE_RADIUS,
                    "Feature.maxRadius", 1, WorldGenLimits.MAX_FEATURE_RADIUS);
        } else if (type.equals("sequence")) {
            dependencies.addAll(featureKeys(required(definition, "features"), "Feature.features"));
            if (dependencies.isEmpty()) {
                throw new LuaError("Feature.features: expected at least one feature");
            }
            feature = BuiltInFeatures.sequence(dependencies);
            maxBlocks = optionalInteger(definition.get("maxBlocks"), WorldGenLimits.MAX_BLOCK_CHANGES_PER_FEATURE,
                    "Feature.maxBlocks", 1, WorldGenLimits.MAX_BLOCK_CHANGES_PER_FEATURE);
            maxRadius = optionalInteger(definition.get("maxRadius"), WorldGenLimits.MAX_FEATURE_RADIUS,
                    "Feature.maxRadius", 1, WorldGenLimits.MAX_FEATURE_RADIUS);
        } else if (type.equals("no_op")) {
            feature = BuiltInFeatures.noOp();
            maxBlocks = 1;
            maxRadius = 1;
        } else {
            throw new LuaError("Feature.type: unknown feature type: " + type);
        }
        return new ParsedFeature(key, type, feature, dependencies, maxBlocks, maxRadius);
    }

    private static ParsedPlacement placement(LuaValue definition) {
        table(definition, "worldgen.placements:add");
        String key = required(definition, "key").checkjstring();
        WorldGenKey feature = featureKey(required(definition, "feature"), "Placement.feature");
        GenerationStage stage = GenerationStage.parse(definition.get("stage").optjstring("surface_features"));
        Set<String> dimensions = dimensions(definition.get("dimensions"));
        LuaValue attemptsValue = definition.get("attempts");
        IntRange attempts;
        double extraChance = 0.0D;
        double probability = 1.0D;
        if (attemptsValue.isnil()) {
            attempts = new IntRange(1, 1);
        } else if (attemptsValue.isnumber()) {
            int count = integer(attemptsValue, "Placement.attempts", 0, WorldGenLimits.MAX_ATTEMPTS_PER_CHUNK);
            attempts = new IntRange(count, count);
        } else {
            table(attemptsValue, "Placement.attempts");
            LuaValue perChunk = attemptsValue.get("perChunk");
            attempts = perChunk.isnil() ? range(attemptsValue, "Placement.attempts", 0,
                    WorldGenLimits.MAX_ATTEMPTS_PER_CHUNK)
                    : range(perChunk, "Placement.attempts.perChunk", 0, WorldGenLimits.MAX_ATTEMPTS_PER_CHUNK);
            extraChance = optionalNumber(attemptsValue.get("extraChance"), 0.0D, "Placement.extraChance", 0.0D,
                    Math.nextDown(1.0D));
            if (!attemptsValue.get("probability").isnil()) {
                probability = optionalNumber(attemptsValue.get("probability"), 1.0D, "Placement.probability", 0.0D,
                        1.0D);
            } else if (!attemptsValue.get("rarity").isnil()) {
                int rarity = integer(attemptsValue.get("rarity"), "Placement.rarity", 1, 1000000);
                probability = 1.0D / rarity;
            }
        }
        LuaValue position = definition.get("position");
        HeightProvider height = height(position.istable() ? position.get("height") : LuaValue.NIL);
        String horizontal = position.istable() ? position.get("horizontal").optjstring("chunk") : "chunk";
        int gridSpacing = position.istable()
                ? optionalInteger(position.get("gridSpacing"), 4, "Placement.position.gridSpacing", 1, 16) : 4;
        PlacementConditions conditions = conditions(definition.get("conditions"), definition.get("biomes"));
        List<WorldGenKey> before = placementKeys(definition.get("before"), "Placement.before");
        List<WorldGenKey> after = placementKeys(definition.get("after"), "Placement.after");
        int priority = definition.get("priority").isnil() ? 0 : definition.get("priority").checkint();
        long salt = definition.get("salt").isnil() ? 0L : definition.get("salt").checklong();
        int successLimit = optionalInteger(definition.get("successLimit"), WorldGenLimits.MAX_ATTEMPTS_PER_CHUNK,
                "Placement.successLimit", 1, WorldGenLimits.MAX_ATTEMPTS_PER_CHUNK);
        return new ParsedPlacement(key, feature, stage, dimensions, attempts, extraChance, probability, height,
                conditions, before, after, priority, salt, successLimit, horizontal, gridSpacing);
    }

    private static HeightProvider height(LuaValue value) {
        if (value.isnil()) {
            return HeightProvider.uniform(0, 127);
        }
        if (value.isnumber()) {
            return HeightProvider.fixed(integer(value, "Placement.height", 0, 127));
        }
        table(value, "Placement.height");
        String type = value.get("type").optjstring("uniform");
        if (type.equals("surface") || type.equals("world_surface") || type.equals("solid_surface")
                || type.equals("ocean_floor") || type.equals("fluid_surface")) {
            int offset = value.get("offset").isnil() ? 0 : value.get("offset").checkint();
            try {
                return HeightProvider.surface(TerrainSurface.parse(type), offset);
            } catch (IllegalArgumentException error) {
                throw new LuaError("Placement.height.type: " + error.getMessage());
            }
        }
        if (type.equals("underground")) {
            int minDepth = optionalInteger(value.get("minDepth"), 8, "Placement.height.minDepth", 1, 127);
            int maxDepth = optionalInteger(value.get("maxDepth"), 32, "Placement.height.maxDepth", minDepth, 127);
            return HeightProvider.underground(minDepth, maxDepth);
        }
        if (type.equals("cave_floor")) {
            int minY = optionalInteger(value.get("min"), 1, "Placement.height.min", 1, 126);
            int maxY = optionalInteger(value.get("max"), 96, "Placement.height.max", minY, 126);
            int clearance = optionalInteger(value.get("minimumClearance"), 3,
                    "Placement.height.minimumClearance", 1, 32);
            return HeightProvider.caveFloor(minY, maxY, clearance);
        }
        int min = optionalInteger(value.get("min"), 0, "Placement.height.min", 0, 127);
        int max = optionalInteger(value.get("max"), 127, "Placement.height.max", min, 127);
        if (type.equals("uniform")) {
            return HeightProvider.uniform(min, max);
        }
        if (type.equals("triangular") || type.equals("normal")) {
            return HeightProvider.triangular(min, max);
        }
        if (type.equals("fixed")) {
            int fixed = optionalInteger(value.get("value"), min, "Placement.height.value", 0, 127);
            return HeightProvider.fixed(fixed);
        }
        if (type.equals("ceiling")) {
            return HeightProvider.ceiling(min, max);
        }
        throw new LuaError("Placement.height.type: unknown height provider: " + type);
    }

    private static PlacementConditions conditions(LuaValue value, LuaValue biomes) {
        if (!value.isnil()) {
            table(value, "Placement.conditions");
        }
        BlockSet ground = value.istable() && !value.get("ground").isnil()
                ? blocks(value.get("ground"), "Placement.conditions.ground") : null;
        boolean sky = value.istable() && value.get("requireSky").toboolean();
        Boolean air = optionalBoolean(value.istable() ? value.get("air") : LuaValue.NIL);
        Boolean water = optionalBoolean(value.istable() ? value.get("water") : LuaValue.NIL);
        Boolean lava = optionalBoolean(value.istable() ? value.get("lava") : LuaValue.NIL);
        int minLight = value.istable()
                ? optionalInteger(value.get("minLight"), 0, "Placement.conditions.minLight", 0, 15) : 0;
        int maxLight = value.istable()
                ? optionalInteger(value.get("maxLight"), 15, "Placement.conditions.maxLight", minLight, 15) : 15;
        Set<String> include = new HashSet<String>();
        Set<String> exclude = new HashSet<String>();
        if (biomes.istable()) {
            LuaValue includes = biomes.get("include").isnil() ? biomes : biomes.get("include");
            addStrings(include, includes, "Placement.biomes.include");
            addStrings(exclude, biomes.get("exclude"), "Placement.biomes.exclude");
            addTags(include, biomes.get("includeTags"), "Placement.biomes.includeTags");
            addTags(exclude, biomes.get("excludeTags"), "Placement.biomes.excludeTags");
        } else if (!biomes.isnil()) {
            throw new LuaError("Placement.biomes: expected a table");
        }
        SitePolicy site = value.istable() ? site(value.get("site"), "Placement.conditions.site") : SitePolicy.ANY;
        return new PlacementConditions(ground, sky, air, water, lava, minLight, maxLight, include, exclude, site);
    }

    static SitePolicy site(LuaValue value, String path) {
        if (value.isnil()) {
            return SitePolicy.ANY;
        }
        if (value.isstring()) {
            value = tableWithType(value.checkjstring());
        }
        table(value, path);
        SitePolicy.Type type;
        SitePolicy.Scope defaultScope;
        SitePolicy.Medium defaultMedium;
        try {
            type = SitePolicy.Type.parse(value.get("type").optjstring("any"));
            defaultScope = type == SitePolicy.Type.CAVE ? SitePolicy.Scope.CLEARANCE_MASK
                    : type == SitePolicy.Type.UNDERGROUND ? SitePolicy.Scope.FULL_BOUNDS
                    : type == SitePolicy.Type.ANY ? SitePolicy.Scope.ORIGIN : SitePolicy.Scope.SUPPORT_FOOTPRINT;
            defaultMedium = type == SitePolicy.Type.CAVE ? SitePolicy.Medium.AIR
                    : type == SitePolicy.Type.UNDERWATER ? SitePolicy.Medium.WATER : SitePolicy.Medium.ANY;
            SitePolicy.Scope scope = SitePolicy.Scope.parse(value.get("scope").optjstring(defaultScope.luaName()));
            SitePolicy.Medium medium = SitePolicy.Medium.parse(
                    value.get("medium").optjstring(defaultMedium.luaName()));
            int defaultMinDepth = type == SitePolicy.Type.CAVE ? 8
                    : type == SitePolicy.Type.UNDERGROUND ? 1 : 0;
            int minDepth = optionalInteger(value.get("minDepthBelowSurface"), defaultMinDepth,
                    path + ".minDepthBelowSurface", 0, 127);
            int maxDepth = optionalInteger(value.get("maxDepthBelowSurface"), 127,
                    path + ".maxDepthBelowSurface", minDepth, 127);
            int minFluidDepth = optionalInteger(value.get("minFluidDepth"),
                    type == SitePolicy.Type.UNDERWATER ? 1 : 0, path + ".minFluidDepth", 0, 127);
            int maxFluidDepth = optionalInteger(value.get("maxFluidDepth"), 127,
                    path + ".maxFluidDepth", minFluidDepth, 127);
            double defaultMinCoverage = type == SitePolicy.Type.UNDERWATER ? 1.0D : 0.0D;
            double defaultMaxCoverage = type == SitePolicy.Type.LAND_SURFACE ? 0.0D : 1.0D;
            double minCoverage = optionalNumber(value.get("minFluidCoverage"), defaultMinCoverage,
                    path + ".minFluidCoverage", 0.0D, 1.0D);
            double maxCoverage = optionalNumber(value.get("maxFluidCoverage"), defaultMaxCoverage,
                    path + ".maxFluidCoverage", minCoverage, 1.0D);
            double airRatio = optionalNumber(value.get("minExistingAirRatio"),
                    type == SitePolicy.Type.CAVE ? 0.9D : 0.0D, path + ".minExistingAirRatio", 0.0D, 1.0D);
            int minCover = optionalInteger(value.get("minSolidCover"), type == SitePolicy.Type.CAVE ? 3 : 0,
                    path + ".minSolidCover", 0, 127);
            int maxCover = optionalInteger(value.get("maxSolidCover"), 127, path + ".maxSolidCover", minCover, 127);
            Boolean requireSky = optionalBoolean(value.get("requireSky"));
            if (requireSky == null && type == SitePolicy.Type.CAVE) {
                requireSky = Boolean.FALSE;
            }
            if (type == SitePolicy.Type.UNDERWATER && medium != SitePolicy.Medium.WATER
                    && medium != SitePolicy.Medium.ANY_FLUID && medium != SitePolicy.Medium.ANY) {
                throw new LuaError(path + ".medium: underwater sites require water or a fluid medium");
            }
            if (type == SitePolicy.Type.LAND_SURFACE && minCoverage > 0.0D) {
                throw new LuaError(path + ": land_surface cannot require positive fluid coverage");
            }
            return new SitePolicy(type, scope, medium, minDepth, maxDepth, minFluidDepth, maxFluidDepth,
                    minCoverage, maxCoverage, airRatio, minCover, maxCover, requireSky);
        } catch (IllegalArgumentException error) {
            throw new LuaError(path + ": " + error.getMessage());
        }
    }

    private static LuaTable tableWithType(String type) {
        LuaTable result = new LuaTable();
        result.set("type", type);
        return result;
    }

    private static Set<String> dimensions(LuaValue value) {
        Set<String> result = new HashSet<String>();
        if (value.isnil()) {
            result.add("minecraft:overworld");
            return result;
        }
        table(value, "Placement.dimensions");
        for (int index = 1; index <= value.length(); index++) {
            String dimension = value.get(index).checkjstring().trim().toLowerCase(Locale.ROOT);
            if (dimension.equals("overworld")) {
                dimension = "minecraft:overworld";
            } else if (dimension.equals("nether") || dimension.equals("hell")) {
                dimension = "minecraft:nether";
            } else if (dimension.equals("both") || dimension.equals("all")) {
                result.add("minecraft:overworld");
                result.add("minecraft:nether");
                continue;
            }
            if (!dimension.equals("minecraft:overworld") && !dimension.equals("minecraft:nether")) {
                throw new LuaError("Placement.dimensions: unsupported dimension: " + dimension);
            }
            result.add(dimension);
        }
        if (result.isEmpty()) {
            throw new LuaError("Placement.dimensions: expected at least one dimension");
        }
        return result;
    }

    static BlockSet blocks(LuaValue value, String path) {
        if (!value.istable() || value.get("id").isnumber() || !value.get("getId").isnil()) {
            return new BlockSet(blockId(value, path));
        }
        if (value.length() == 0) {
            throw new LuaError(path + ": expected at least one block");
        }
        int[] result = new int[value.length()];
        for (int index = 0; index < result.length; index++) {
            result[index] = blockId(value.get(index + 1), path + "[" + (index + 1) + "]");
        }
        return new BlockSet(result);
    }

    static int blockId(LuaValue value, String path) {
        if (value.isnumber()) {
            int id = value.checkint();
            if (id < 0 || id >= Block.blocksList.length || id != 0 && Block.blocksList[id] == null) {
                throw new LuaError(path + ": unknown block id: " + id);
            }
            return id;
        }
        if (value.isstring()) {
            String name = value.tojstring();
            if (name.equals("minecraft:air") || name.equals("air")) {
                return 0;
            }
            String fieldName = name.startsWith("minecraft:") ? name.substring("minecraft:".length()) : name;
            try {
                Field field = Block.class.getField(fieldName);
                if (Modifier.isStatic(field.getModifiers()) && Block.class.isAssignableFrom(field.getType())) {
                    return ((Block) field.get(null)).blockID;
                }
            } catch (Exception ignored) {
            }
            throw new LuaError(path + ": unknown block: " + name);
        }
        if (value.istable()) {
            if (!value.get("id").isnil()) {
                return blockId(value.get("id"), path);
            }
            if (!value.get("getId").isnil()) {
                return blockId(value.get("getId").call(value), path);
            }
        }
        throw new LuaError(path + ": expected a block id, name, or handle");
    }

    private static List<WorldGenKey> featureKeys(LuaValue value, String path) {
        table(value, path);
        List<WorldGenKey> result = new ArrayList<WorldGenKey>();
        for (int index = 1; index <= value.length(); index++) {
            result.add(featureKey(value.get(index), path + "[" + index + "]"));
        }
        return result;
    }

    static WorldGenKey featureKey(LuaValue value, String path) {
        if (value instanceof FeatureReference) {
            return ((FeatureReference) value).key();
        }
        if (!value.isstring()) {
            throw new LuaError(path + ": expected a feature reference or key");
        }
        try {
            return WorldGenKey.parseFeature(value.tojstring().trim());
        } catch (IllegalArgumentException error) {
            throw new LuaError(path + ": " + error.getMessage());
        }
    }

    private static List<WorldGenKey> placementKeys(LuaValue value, String path) {
        if (value.isnil()) {
            return Collections.emptyList();
        }
        table(value, path);
        List<WorldGenKey> result = new ArrayList<WorldGenKey>();
        for (int index = 1; index <= value.length(); index++) {
            LuaValue entry = value.get(index);
            result.add(entry instanceof PlacementReference ? ((PlacementReference) entry).key()
                    : key(entry.checkjstring(), WorldGenKind.PLACEMENT, path));
        }
        return result;
    }

    private static WorldGenKey key(String value, WorldGenKind kind, String path) {
        try {
            return WorldGenKey.parse(value.trim(), kind);
        } catch (IllegalArgumentException error) {
            throw new LuaError(path + ": " + error.getMessage());
        }
    }

    static IntRange range(LuaValue value, String path, int minimum, int maximum) {
        if (value.isnumber()) {
            int fixed = integer(value, path, minimum, maximum);
            return new IntRange(fixed, fixed);
        }
        table(value, path);
        int min = integer(value.get("min").isnil() ? value.get(1) : value.get("min"), path + ".min", minimum,
                maximum);
        int max = integer(value.get("max").isnil() ? value.get(2) : value.get("max"), path + ".max", min,
                maximum);
        return new IntRange(min, max);
    }

    static int metadata(LuaValue value, String path) {
        return value.isnil() ? 0 : integer(value, path, 0, 15);
    }

    static int optionalInteger(LuaValue value, int fallback, String path, int min, int max) {
        return value.isnil() ? fallback : integer(value, path, min, max);
    }

    private static int integer(LuaValue value, String path, int min, int max) {
        if (!value.isint()) {
            throw new LuaError(path + ": expected an integer");
        }
        int result = value.toint();
        if (result < min || result > max) {
            throw new LuaError(path + ": expected " + min + ".." + max);
        }
        return result;
    }

    static double optionalNumber(LuaValue value, double fallback, String path, double min, double max) {
        double result = value.isnil() ? fallback : value.checkdouble();
        if (!Double.isFinite(result) || result < min || result > max) {
            throw new LuaError(path + ": expected a finite value between " + min + " and " + max);
        }
        return result;
    }

    private static Boolean optionalBoolean(LuaValue value) {
        return value.isnil() ? null : Boolean.valueOf(value.checkboolean());
    }

    private static void addStrings(Set<String> output, LuaValue value, String path) {
        if (value.isnil()) {
            return;
        }
        table(value, path);
        for (int index = 1; index <= value.length(); index++) {
            output.add(value.get(index).checkjstring().trim().toLowerCase(Locale.ROOT));
        }
    }

    private static void addTags(Set<String> output, LuaValue value, String path) {
        if (value.isnil()) {
            return;
        }
        table(value, path);
        for (int index = 1; index <= value.length(); index++) {
            String tag = value.get(index).checkjstring().trim().toLowerCase(Locale.ROOT);
            if (tag.isEmpty() || tag.equals("#") || tag.length() > 128) {
                throw new LuaError(path + "[" + index + "]: expected a tag with 1..128 characters");
            }
            output.add(tag.startsWith("#") ? tag : "#" + tag);
        }
    }

    static void table(LuaValue value, String path) {
        if (!value.istable()) {
            throw new LuaError(path + ": expected a table");
        }
    }

    private static LuaValue argument(Varargs arguments, LuaValue receiver, int index) {
        return arguments.arg(index + (arguments.arg1() == receiver ? 1 : 0));
    }

    private static final class ParsedFeature {
        private final String key;
        private final String type;
        private final WorldFeature feature;
        private final List<WorldGenKey> dependencies;
        private final int maxBlocks;
        private final int maxRadius;

        private ParsedFeature(String key, String type, WorldFeature feature, List<WorldGenKey> dependencies,
                int maxBlocks, int maxRadius) {
            this.key = key;
            this.type = type;
            this.feature = feature;
            this.dependencies = dependencies;
            this.maxBlocks = maxBlocks;
            this.maxRadius = maxRadius;
        }
    }

    private static final class ParsedPlacement {
        private final String key;
        private final WorldGenKey feature;
        private final GenerationStage stage;
        private final Set<String> dimensions;
        private final IntRange attempts;
        private final double extraChance;
        private final double probability;
        private final HeightProvider height;
        private final PlacementConditions conditions;
        private final List<WorldGenKey> before;
        private final List<WorldGenKey> after;
        private final int priority;
        private final long salt;
        private final int successLimit;
        private final String horizontal;
        private final int gridSpacing;

        private ParsedPlacement(String key, WorldGenKey feature, GenerationStage stage, Set<String> dimensions,
                IntRange attempts, double extraChance, double probability, HeightProvider height,
                PlacementConditions conditions, List<WorldGenKey> before, List<WorldGenKey> after, int priority,
                long salt, int successLimit, String horizontal, int gridSpacing) {
            this.key = key;
            this.feature = feature;
            this.stage = stage;
            this.dimensions = dimensions;
            this.attempts = attempts;
            this.extraChance = extraChance;
            this.probability = probability;
            this.height = height;
            this.conditions = conditions;
            this.before = before;
            this.after = after;
            this.priority = priority;
            this.salt = salt;
            this.successLimit = successLimit;
            this.horizontal = horizontal;
            this.gridSpacing = gridSpacing;
        }
    }
}

package betamoon.worldgen;

import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.worldgen.biome.BiomeDefinition;
import betamoon.worldgen.biome.BiomeSourceDefinition;
import betamoon.worldgen.surface.SurfaceRuleSet;
import betamoon.wrappers.BiomeGenWrapper;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.World;
import org.luaj.vm2.LuaError;

/** Atomic keyed biome, biome-source, tag, and surface catalog. */
public final class BiomeGenRegistry {
    private static final String[] FIELD_BIOME_LOOKUP_TABLE = new String[] { "biomeLookupTable", "x" };
    private static final ThreadLocal<PublicationBatch> CURRENT_BATCH = new ThreadLocal<PublicationBatch>();
    private static volatile Snapshot active = Snapshot.empty();

    private BiomeGenRegistry() {
    }

    public static PublicationBatch beginPublication(String resourceOwner, String owner) {
        if (CURRENT_BATCH.get() != null) {
            throw new IllegalStateException("Nested biome publication is not supported");
        }
        PublicationBatch batch = new PublicationBatch(resourceOwner, owner);
        CURRENT_BATCH.set(batch);
        return batch;
    }

    public static synchronized void clear() {
        active = Snapshot.empty();
        getBiomeLookupTable();
    }

    public static synchronized void retainOwners(Set<String> owners) {
        List<BiomeDefinition> biomes = new ArrayList<BiomeDefinition>();
        List<SurfaceRuleSet> surfaces = new ArrayList<SurfaceRuleSet>();
        List<BiomeSourceDefinition> sources = new ArrayList<BiomeSourceDefinition>();
        for (BiomeDefinition definition : active.biomes.values()) {
            if (owners.contains(definition.resourceOwner)) {
                biomes.add(definition);
            }
        }
        for (SurfaceRuleSet definition : active.surfaces.values()) {
            if (owners.contains(definition.resourceOwner)) {
                surfaces.add(definition);
            }
        }
        for (BiomeSourceDefinition definition : active.sources.values()) {
            if (owners.contains(definition.resourceOwner)) {
                sources.add(definition);
            }
        }
        active = Snapshot.compile(biomes, surfaces, sources);
    }

    public static WorldGenKey registerBiomeGenerator(String declaredKey, BiomeGenWrapper biome, Set<String> tags,
            WorldGenKey surface, double minTemperature, double maxTemperature, double minHumidity,
            double maxHumidity, boolean legacyClimateRange, int decoratorCount) {
        PublicationBatch batch = CURRENT_BATCH.get();
        if (batch != null) {
            return batch.addBiome(declaredKey, biome, tags, surface, minTemperature, maxTemperature, minHumidity,
                    maxHumidity, legacyClimateRange, decoratorCount);
        }
        String resourceOwner = required(LuaScriptRegistry.getCurrentScriptFile());
        String owner = required(LuaScriptRegistry.getCurrentScriptIdentity());
        WorldGenKey key = biomeKey(declaredKey, resourceOwner, biome.biomeName);
        publishAddition(new BiomeDefinition(key, resourceOwner, owner, resourceOwner + ":biome", biome,
                normalizeTags(tags), surface, minTemperature, maxTemperature, minHumidity, maxHumidity,
                legacyClimateRange, decoratorCount), null, null);
        return key;
    }

    /** Compatibility registration used by older internal callers. */
    public static void registerBiomeGenerator(BiomeGenBase biome, double minTemperature, double maxTemperature,
            double minHumidity, double maxHumidity) {
        if (!(biome instanceof BiomeGenWrapper)) {
            return;
        }
        registerBiomeGenerator(null, (BiomeGenWrapper) biome, Collections.<String>emptySet(), null, minTemperature,
                maxTemperature, minHumidity, maxHumidity, true, 0);
    }

    public static WorldGenKey registerSurface(String declaredKey, SurfaceFactory factory) {
        PublicationBatch batch = CURRENT_BATCH.get();
        if (batch != null) {
            return batch.addSurface(declaredKey, factory);
        }
        String resourceOwner = required(LuaScriptRegistry.getCurrentScriptFile());
        String owner = required(LuaScriptRegistry.getCurrentScriptIdentity());
        WorldGenKey key = parse(declaredKey, WorldGenKind.SURFACE, "Surface.key");
        publishAddition(null, factory.create(key, resourceOwner, owner, resourceOwner + ":surface"), null);
        return key;
    }

    public static WorldGenKey registerSource(String declaredKey, SourceFactory factory) {
        PublicationBatch batch = CURRENT_BATCH.get();
        if (batch != null) {
            return batch.addSource(declaredKey, factory);
        }
        String resourceOwner = required(LuaScriptRegistry.getCurrentScriptFile());
        String owner = required(LuaScriptRegistry.getCurrentScriptIdentity());
        WorldGenKey key = parse(declaredKey, WorldGenKind.BIOME_SOURCE, "BiomeSource.key");
        publishAddition(null, null, factory.create(key, resourceOwner, owner, resourceOwner + ":biome_source"));
        return key;
    }

    public static boolean hasBiome(WorldGenKey key) {
        PublicationBatch batch = CURRENT_BATCH.get();
        if (batch != null && batch.hasBiome(key)) {
            return true;
        }
        return active.biomes.containsKey(key);
    }

    public static boolean hasSurface(WorldGenKey key) {
        PublicationBatch batch = CURRENT_BATCH.get();
        if (batch != null && batch.hasSurface(key)) {
            return true;
        }
        return active.surfaces.containsKey(key);
    }

    public static boolean hasSource(WorldGenKey key) {
        PublicationBatch batch = CURRENT_BATCH.get();
        if (batch != null && batch.hasSource(key)) {
            return true;
        }
        return active.sources.containsKey(key);
    }

    public static WorldGenKey keyFor(BiomeGenBase biome) {
        return active.keysByBiome.get(biome);
    }

    static BiomeGenBase biomeFor(WorldGenKey key) {
        BiomeDefinition definition = active.biomes.get(key);
        return definition == null ? null : definition.biome;
    }

    public static boolean matchesSelectors(BiomeGenBase biome, Set<String> selectors) {
        if (selectors.isEmpty()) {
            return true;
        }
        BiomeDefinition definition = active.byBiome.get(biome);
        String name = biome == null || biome.biomeName == null ? "" : biome.biomeName.toLowerCase(Locale.ROOT);
        for (String raw : selectors) {
            String selector = raw.toLowerCase(Locale.ROOT);
            if (selector.startsWith("#")) {
                if (definition != null && definition.tags.contains(selector.substring(1))) {
                    return true;
                }
            } else if (selector.equals(name) || definition != null && selector.equals(definition.key.toString())) {
                return true;
            }
        }
        return false;
    }

    public static void applySurfaces(World world, int chunkX, int chunkZ, byte[] blocks, BiomeGenBase[] biomes) {
        Snapshot snapshot = active;
        if (world == null || blocks == null || blocks.length != 32768 || biomes == null || biomes.length < 256) {
            return;
        }
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                BiomeDefinition biome = snapshot.byBiome.get(biomes[x + z * 16]);
                SurfaceRuleSet surface = biome == null || biome.surface == null ? null
                        : snapshot.surfaces.get(biome.surface);
                if (surface != null) {
                    surface.apply(world.getRandomSeed(), chunkX, chunkZ, blocks, x, z);
                }
            }
        }
    }

    public static void applyBiomeGenerators() {
        Snapshot snapshot = active;
        BiomeGenBase[] table = getBiomeLookupTable();
        if (table == null || table.length == 0) {
            return;
        }
        for (BiomeDefinition definition : snapshot.biomes.values()) {
            if (definition.legacyClimateRange) {
                applyRange(table, definition.biome, definition.minTemperature, definition.maxTemperature,
                        definition.minHumidity, definition.maxHumidity);
            }
        }
        BiomeSourceDefinition source = snapshot.selectedSource;
        if (source == null) {
            return;
        }
        if (source.type.equals("fixed")) {
            BiomeDefinition biome = snapshot.biomes.get(source.fixedBiome);
            for (int index = 0; index < table.length; index++) {
                table[index] = biome.biome;
            }
            return;
        }
        for (int temperatureIndex = 0; temperatureIndex < 64; temperatureIndex++) {
            double temperature = temperatureIndex / 63.0D;
            for (int humidityIndex = 0; humidityIndex < 64; humidityIndex++) {
                double humidity = humidityIndex / 63.0D;
                BiomeSourceDefinition.ClimateEntry selected = select(source.entries, temperature, humidity);
                if (selected != null) {
                    table[temperatureIndex + humidityIndex * 64] = snapshot.biomes.get(selected.biome).biome;
                }
            }
        }
    }

    public static List<Description> snapshot() {
        List<Description> result = new ArrayList<Description>();
        for (BiomeDefinition entry : active.biomes.values()) {
            result.add(new Description(entry));
        }
        return Collections.unmodifiableList(result);
    }

    public static List<SurfaceDescription> surfaceSnapshot() {
        List<SurfaceDescription> result = new ArrayList<SurfaceDescription>();
        for (SurfaceRuleSet entry : active.surfaces.values()) {
            result.add(new SurfaceDescription(entry));
        }
        return Collections.unmodifiableList(result);
    }

    public static List<SourceDescription> sourceSnapshot() {
        List<SourceDescription> result = new ArrayList<SourceDescription>();
        for (BiomeSourceDefinition entry : active.sources.values()) {
            result.add(new SourceDescription(entry, entry == active.selectedSource));
        }
        return Collections.unmodifiableList(result);
    }

    private static synchronized void publishAddition(BiomeDefinition biome, SurfaceRuleSet surface,
            BiomeSourceDefinition source) {
        List<BiomeDefinition> biomes = new ArrayList<BiomeDefinition>(active.biomes.values());
        List<SurfaceRuleSet> surfaces = new ArrayList<SurfaceRuleSet>(active.surfaces.values());
        List<BiomeSourceDefinition> sources = new ArrayList<BiomeSourceDefinition>(active.sources.values());
        if (biome != null) {
            biomes.add(biome);
        }
        if (surface != null) {
            surfaces.add(surface);
        }
        if (source != null) {
            sources.add(source);
        }
        active = Snapshot.compile(biomes, surfaces, sources);
    }

    private static synchronized void publishOwner(PublicationBatch batch) {
        active = ownerSnapshot(batch);
    }

    private static synchronized void validateOwner(PublicationBatch batch) {
        ownerSnapshot(batch);
    }

    private static Snapshot ownerSnapshot(PublicationBatch batch) {
        List<BiomeDefinition> biomes = new ArrayList<BiomeDefinition>();
        List<SurfaceRuleSet> surfaces = new ArrayList<SurfaceRuleSet>();
        List<BiomeSourceDefinition> sources = new ArrayList<BiomeSourceDefinition>();
        for (BiomeDefinition definition : active.biomes.values()) {
            if (!batch.resourceOwner.equals(definition.resourceOwner)) {
                biomes.add(definition);
            }
        }
        for (SurfaceRuleSet definition : active.surfaces.values()) {
            if (!batch.resourceOwner.equals(definition.resourceOwner)) {
                surfaces.add(definition);
            }
        }
        for (BiomeSourceDefinition definition : active.sources.values()) {
            if (!batch.resourceOwner.equals(definition.resourceOwner)) {
                sources.add(definition);
            }
        }
        biomes.addAll(batch.biomes);
        surfaces.addAll(batch.surfaces);
        sources.addAll(batch.sources);
        return Snapshot.compile(biomes, surfaces, sources);
    }

    private static BiomeSourceDefinition.ClimateEntry select(List<BiomeSourceDefinition.ClimateEntry> entries,
            double temperature, double humidity) {
        BiomeSourceDefinition.ClimateEntry result = null;
        for (BiomeSourceDefinition.ClimateEntry entry : entries) {
            if (entry.contains(temperature, humidity) && (result == null || entry.priority > result.priority
                    || entry.priority == result.priority && entry.biome.compareTo(result.biome) < 0)) {
                result = entry;
            }
        }
        return result;
    }

    private static void applyRange(BiomeGenBase[] table, BiomeGenBase biome, double minTemperature,
            double maxTemperature, double minHumidity, double maxHumidity) {
        for (int temperatureIndex = 0; temperatureIndex < 64; temperatureIndex++) {
            double temperature = temperatureIndex / 63.0D;
            if (temperature < minTemperature || temperature > maxTemperature) {
                continue;
            }
            for (int humidityIndex = 0; humidityIndex < 64; humidityIndex++) {
                double humidity = humidityIndex / 63.0D;
                if (humidity >= minHumidity && humidity <= maxHumidity) {
                    table[temperatureIndex + humidityIndex * 64] = biome;
                }
            }
        }
    }

    private static WorldGenKey biomeKey(String declared, String owner, String name) {
        if (declared != null && !declared.trim().isEmpty()) {
            return parse(declared, WorldGenKind.BIOME, "Biome.key");
        }
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._/-]+", "_");
        if (slug.isEmpty()) {
            slug = "unnamed";
        }
        return WorldGenKey.parse("betamoon:biome/legacy/" + Long.toHexString(SeedMixer.hash(owner)) + "/" + slug,
                WorldGenKind.BIOME);
    }

    private static WorldGenKey parse(String value, WorldGenKind kind, String field) {
        try {
            return WorldGenKey.parse(value, kind);
        } catch (IllegalArgumentException error) {
            throw new LuaError(field + ": " + error.getMessage());
        }
    }

    private static Set<String> normalizeTags(Set<String> tags) {
        Set<String> result = new TreeSet<String>();
        for (String tag : tags) {
            String normalized = tag.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith("#")) {
                normalized = normalized.substring(1);
            }
            if (normalized.isEmpty() || normalized.length() > 128) {
                throw new LuaError("Biome.tags: expected names with 1..128 characters");
            }
            result.add(normalized);
        }
        return result;
    }

    private static String required(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Biome declarations require an active script owner");
        }
        return value;
    }

    private static BiomeGenBase[] getBiomeLookupTable() {
        try {
            BiomeGenBase.generateBiomeLookup();
            Field field = resolveField(BiomeGenBase.class, FIELD_BIOME_LOOKUP_TABLE);
            return (BiomeGenBase[]) field.get(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Field resolveField(Class<?> owner, String[] fieldNames) throws Exception {
        Exception last = null;
        for (String fieldName : fieldNames) {
            try {
                Field field = owner.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (Exception error) {
                last = error;
            }
        }
        throw last == null ? new NoSuchFieldException("No matching biome lookup field") : last;
    }

    public interface SurfaceFactory {
        SurfaceRuleSet create(WorldGenKey key, String resourceOwner, String owner, String source);
    }

    public interface SourceFactory {
        BiomeSourceDefinition create(WorldGenKey key, String resourceOwner, String owner, String source);
    }

    public static final class PublicationBatch implements AutoCloseable {
        private final String resourceOwner;
        private final String owner;
        private final List<BiomeDefinition> biomes = new ArrayList<BiomeDefinition>();
        private final List<SurfaceRuleSet> surfaces = new ArrayList<SurfaceRuleSet>();
        private final List<BiomeSourceDefinition> sources = new ArrayList<BiomeSourceDefinition>();
        private int index;
        private boolean published;
        private boolean closed;

        private PublicationBatch(String resourceOwner, String owner) {
            this.resourceOwner = required(resourceOwner);
            this.owner = required(owner);
        }

        private WorldGenKey addBiome(String declaredKey, BiomeGenWrapper biome, Set<String> tags,
                WorldGenKey surface, double minTemperature, double maxTemperature, double minHumidity,
                double maxHumidity, boolean legacyClimateRange, int decoratorCount) {
            WorldGenKey key = biomeKey(declaredKey, resourceOwner, biome.biomeName);
            biomes.add(new BiomeDefinition(key, resourceOwner, owner, source("biome"), biome, normalizeTags(tags),
                    surface, minTemperature, maxTemperature, minHumidity, maxHumidity, legacyClimateRange,
                    decoratorCount));
            return key;
        }

        private WorldGenKey addSurface(String declaredKey, SurfaceFactory factory) {
            WorldGenKey key = parse(declaredKey, WorldGenKind.SURFACE, "Surface.key");
            surfaces.add(factory.create(key, resourceOwner, owner, source("surface")));
            return key;
        }

        private WorldGenKey addSource(String declaredKey, SourceFactory factory) {
            WorldGenKey key = parse(declaredKey, WorldGenKind.BIOME_SOURCE, "BiomeSource.key");
            sources.add(factory.create(key, resourceOwner, owner, source("biome_source")));
            return key;
        }

        private String source(String kind) {
            return resourceOwner + ":" + kind + "[" + (++index) + "]";
        }

        private boolean hasBiome(WorldGenKey key) {
            for (BiomeDefinition definition : biomes) {
                if (definition.key.equals(key)) {
                    return true;
                }
            }
            return false;
        }

        private boolean hasSurface(WorldGenKey key) {
            for (SurfaceRuleSet definition : surfaces) {
                if (definition.key.equals(key)) {
                    return true;
                }
            }
            return false;
        }

        private boolean hasSource(WorldGenKey key) {
            for (BiomeSourceDefinition definition : sources) {
                if (definition.key.equals(key)) {
                    return true;
                }
            }
            return false;
        }

        public void publish() {
            ensureOpen();
            publishOwner(this);
            published = true;
        }

        public void validate() {
            ensureOpen();
            validateOwner(this);
        }

        private void ensureOpen() {
            if (closed) {
                throw new IllegalStateException("Biome publication is closed");
            }
            if (published) {
                throw new IllegalStateException("Biome publication was already published");
            }
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                CURRENT_BATCH.remove();
            }
        }
    }

    private static final class Snapshot {
        private final Map<WorldGenKey, BiomeDefinition> biomes;
        private final Map<WorldGenKey, SurfaceRuleSet> surfaces;
        private final Map<WorldGenKey, BiomeSourceDefinition> sources;
        private final Map<BiomeGenBase, BiomeDefinition> byBiome;
        private final Map<BiomeGenBase, WorldGenKey> keysByBiome;
        private final BiomeSourceDefinition selectedSource;

        private Snapshot(Map<WorldGenKey, BiomeDefinition> biomes, Map<WorldGenKey, SurfaceRuleSet> surfaces,
                Map<WorldGenKey, BiomeSourceDefinition> sources, BiomeSourceDefinition selectedSource) {
            this.biomes = Collections.unmodifiableMap(biomes);
            this.surfaces = Collections.unmodifiableMap(surfaces);
            this.sources = Collections.unmodifiableMap(sources);
            this.selectedSource = selectedSource;
            Map<BiomeGenBase, BiomeDefinition> definitions = new IdentityHashMap<BiomeGenBase, BiomeDefinition>();
            Map<BiomeGenBase, WorldGenKey> keys = new IdentityHashMap<BiomeGenBase, WorldGenKey>();
            for (BiomeDefinition definition : biomes.values()) {
                definitions.put(definition.biome, definition);
                keys.put(definition.biome, definition.key);
            }
            byBiome = Collections.unmodifiableMap(definitions);
            keysByBiome = Collections.unmodifiableMap(keys);
        }

        private static Snapshot empty() {
            return compile(Collections.<BiomeDefinition>emptyList(), Collections.<SurfaceRuleSet>emptyList(),
                    Collections.<BiomeSourceDefinition>emptyList());
        }

        private static Snapshot compile(List<BiomeDefinition> biomeValues, List<SurfaceRuleSet> surfaceValues,
                List<BiomeSourceDefinition> sourceValues) {
            Map<WorldGenKey, BiomeDefinition> biomes = keyedBiomes(biomeValues);
            Map<WorldGenKey, SurfaceRuleSet> surfaces = keyedSurfaces(surfaceValues);
            Map<WorldGenKey, BiomeSourceDefinition> sources = keyedSources(sourceValues);
            for (BiomeDefinition biome : biomes.values()) {
                if (biome.surface != null && !surfaces.containsKey(biome.surface)) {
                    throw new LuaError("Biome " + biome.key + " references unknown surface " + biome.surface);
                }
            }
            BiomeSourceDefinition selected = null;
            for (BiomeSourceDefinition source : sources.values()) {
                validateSource(source, biomes);
                if (source.active && (selected == null || source.priority > selected.priority
                        || source.priority == selected.priority && source.key.compareTo(selected.key) < 0)) {
                    if (selected != null && source.priority == selected.priority) {
                        throw new LuaError("Active biome sources " + selected.key + " and " + source.key
                                + " have the same priority " + source.priority);
                    }
                    selected = source;
                }
            }
            return new Snapshot(biomes, surfaces, sources, selected);
        }

        private static Map<WorldGenKey, BiomeDefinition> keyedBiomes(List<BiomeDefinition> values) {
            Map<WorldGenKey, BiomeDefinition> result = new LinkedHashMap<WorldGenKey, BiomeDefinition>();
            List<BiomeDefinition> sorted = new ArrayList<BiomeDefinition>(values);
            Collections.sort(sorted, Comparator.comparing(value -> value.key));
            for (BiomeDefinition value : sorted) {
                if (result.put(value.key, value) != null) {
                    throw new LuaError("Duplicate biome key: " + value.key);
                }
            }
            return result;
        }

        private static Map<WorldGenKey, SurfaceRuleSet> keyedSurfaces(List<SurfaceRuleSet> values) {
            Map<WorldGenKey, SurfaceRuleSet> result = new LinkedHashMap<WorldGenKey, SurfaceRuleSet>();
            List<SurfaceRuleSet> sorted = new ArrayList<SurfaceRuleSet>(values);
            Collections.sort(sorted, Comparator.comparing(value -> value.key));
            for (SurfaceRuleSet value : sorted) {
                if (result.put(value.key, value) != null) {
                    throw new LuaError("Duplicate surface key: " + value.key);
                }
            }
            return result;
        }

        private static Map<WorldGenKey, BiomeSourceDefinition> keyedSources(List<BiomeSourceDefinition> values) {
            Map<WorldGenKey, BiomeSourceDefinition> result = new LinkedHashMap<WorldGenKey, BiomeSourceDefinition>();
            List<BiomeSourceDefinition> sorted = new ArrayList<BiomeSourceDefinition>(values);
            Collections.sort(sorted, Comparator.comparing(value -> value.key));
            for (BiomeSourceDefinition value : sorted) {
                if (result.put(value.key, value) != null) {
                    throw new LuaError("Duplicate biome-source key: " + value.key);
                }
            }
            return result;
        }

        private static void validateSource(BiomeSourceDefinition source, Map<WorldGenKey, BiomeDefinition> biomes) {
            if (source.fixedBiome != null && !biomes.containsKey(source.fixedBiome)) {
                throw new LuaError("Biome source " + source.key + " references unknown biome " + source.fixedBiome);
            }
            for (BiomeSourceDefinition.ClimateEntry entry : source.entries) {
                if (!biomes.containsKey(entry.biome)) {
                    throw new LuaError("Biome source " + source.key + " references unknown biome " + entry.biome);
                }
            }
        }
    }

    public static final class Description {
        public final String key;
        public final String owner;
        public final String name;
        public final String implementation;
        public final int topBlock;
        public final int fillerBlock;
        public final Set<String> tags;
        public final String surface;
        public final int decorators;
        public final double minTemperature;
        public final double maxTemperature;
        public final double minHumidity;
        public final double maxHumidity;
        public final boolean legacyClimateRange;

        private Description(BiomeDefinition entry) {
            key = entry.key.toString();
            owner = entry.owner;
            name = entry.biome.biomeName;
            implementation = entry.biome.getClass().getName();
            topBlock = entry.biome.topBlock & 255;
            fillerBlock = entry.biome.fillerBlock & 255;
            tags = entry.tags;
            surface = entry.surface == null ? null : entry.surface.toString();
            decorators = entry.decoratorCount;
            minTemperature = entry.minTemperature;
            maxTemperature = entry.maxTemperature;
            minHumidity = entry.minHumidity;
            maxHumidity = entry.maxHumidity;
            legacyClimateRange = entry.legacyClimateRange;
        }
    }

    public static final class SurfaceDescription {
        public final String key;
        public final String owner;
        public final int layers;
        public final int seaLevel;
        public final Integer underwaterBlock;

        private SurfaceDescription(SurfaceRuleSet entry) {
            key = entry.key.toString();
            owner = entry.owner;
            layers = entry.layers.size();
            seaLevel = entry.seaLevel;
            underwaterBlock = entry.underwaterBlock;
        }
    }

    public static final class SourceDescription {
        public final String key;
        public final String owner;
        public final String type;
        public final boolean active;
        public final int priority;
        public final int entries;
        public final int overlapCells;
        public final int uncoveredCells;

        private SourceDescription(BiomeSourceDefinition source, boolean selected) {
            key = source.key.toString();
            owner = source.owner;
            type = source.type;
            active = selected;
            priority = source.priority;
            entries = source.entries.size();
            int overlaps = 0;
            int uncovered = 0;
            if (source.type.equals("vanilla_climate")) {
                for (int x = 0; x < 64; x++) {
                    for (int z = 0; z < 64; z++) {
                        int matches = 0;
                        for (BiomeSourceDefinition.ClimateEntry entry : source.entries) {
                            if (entry.contains(x / 63.0D, z / 63.0D)) {
                                matches++;
                            }
                        }
                        if (matches == 0) {
                            uncovered++;
                        } else if (matches > 1) {
                            overlaps++;
                        }
                    }
                }
            }
            overlapCells = overlaps;
            uncoveredCells = source.type.equals("fixed") ? 0 : uncovered;
        }
    }
}

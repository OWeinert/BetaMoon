package betamoon.luaapi.world;

import betamoon.luaapi.resource.OverrideManager;
import betamoon.worldgen.BiomeGenRegistry;
import betamoon.worldgen.BiomeSpawnGroup;
import betamoon.worldgen.BiomeTreeMode;
import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.biome.BiomeDefinition;
import betamoon.wrappers.BiomeGenWrapper;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.src.BiomeGenBase;
import net.minecraft.src.SpawnListEntry;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

/** Stable live reference for a native or BetaMoon biome. */
public final class BiomeReference extends LuaTable {
    private static final String[] SNOW = {"enableSnow", "v"};
    private static final String[] RAIN = {"enableRain", "w"};
    private static final String[] MONSTERS = {"spawnableMonsterList", "s"};
    private static final String[] CREATURES = {"spawnableCreatureList", "t"};
    private static final String[] WATER = {"spawnableWaterCreatureList", "u"};

    private final BiomeGenBase biome;
    private final BiomeDefinition entry;
    private final WorldGenKey key;
    private final String identity;

    BiomeReference(BiomeGenBase biome, BiomeDefinition entry) {
        this.biome = biome;
        this.entry = entry;
        this.key = entry == null ? null : entry.key;
        identity = key == null ? "biome:" + System.identityHashCode(biome) : "biome:" + key;
        set("getKey", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return valueOf(BiomeReference.this.key == null
                        ? BiomeReference.this.biome.biomeName
                        : BiomeReference.this.key.toString());
            }
        });
        set("override", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                return override(args.arg(args.arg1() == BiomeReference.this ? 2 : 1));
            }
        });
    }

    public BiomeReference(WorldGenKey key) {
        this(requiredBiome(key), BiomeGenRegistry.definitionFor(key));
    }

    WorldGenKey key() {
        return key;
    }

    @Override
    public LuaValue get(LuaValue key) {
        if (!key.isstring()) {
            return super.get(key);
        }
        String name = key.tojstring();
        if (name.equals("key")) {
            return valueOf(key == null ? biome.biomeName : key.toString());
        }
        if (name.equals("name") || name.equals("displayName")) {
            return valueOf(biome.biomeName);
        }
        if (name.equals("owner")) {
            return valueOf(entry == null || entry.owner == null ? "minecraft" : entry.owner);
        }
        if (name.equals("isVanilla")) {
            return valueOf(entry == null);
        }
        if (name.equals("isBetaMoon")) {
            return valueOf(entry != null);
        }
        if (name.equals("exists")) {
            return TRUE;
        }
        if (name.equals("color")) {
            return valueOf(biome.color);
        }
        if (name.equals("foliageColor")) {
            return valueOf(biome.field_6502_q);
        }
        if (name.equals("surface")) {
            LuaTable surface = new LuaTable();
            surface.set("top", biome.topBlock & 255);
            surface.set("filler", biome.fillerBlock & 255);
            return surface;
        }
        if (name.equals("weather")) {
            LuaTable weather = new LuaTable();
            weather.set("snow", valueOf(flag(biome, SNOW)));
            weather.set("rain", valueOf(flag(biome, RAIN)));
            return weather;
        }
        if (name.equals("range") && entry != null) {
            LuaTable range = new LuaTable();
            range.set("temperature", range(entry.minTemperature, entry.maxTemperature));
            range.set("humidity", range(entry.minHumidity, entry.maxHumidity));
            return range;
        }
        if (name.equals("trees") && biome instanceof BiomeGenWrapper) {
            BiomeGenWrapper wrapper = (BiomeGenWrapper) biome;
            LuaTable trees = new LuaTable();
            trees.set("type", wrapper.treeMode().name().toLowerCase());
            trees.set("bigTreeChance", wrapper.bigTreeChance());
            return trees;
        }
        if (name.equals("spawns")) {
            return spawnTable(readSpawns(biome));
        }
        return super.get(key);
    }

    boolean matches(LuaValue query) {
        if (!query.get("owner").isnil() && !get("owner").raweq(query.get("owner"))) {
            return false;
        }
        if (!query.get("name").isnil() && !biome.biomeName.equalsIgnoreCase(query.get("name").checkjstring())) {
            return false;
        }
        return query.get("vanilla").isnil() || (entry == null) == query.get("vanilla").checkboolean();
    }

    LuaValue override(LuaValue definition) {
        if (!definition.istable()) {
            throw new LuaError("Biome override expects a table.");
        }
        LuaValue when = definition.get("when");
        if (!when.isnil() && !when.istable()) {
            throw new LuaError("Biome override when must be a table.");
        }
        LuaTable handle = new LuaTable();
        handle.set("target", this);
        if (!when.isnil() && !when.get("owner").isnil() && !get("owner").raweq(when.get("owner"))) {
            handle.set("active", FALSE);
            handle.set("reason", "target owner did not match");
            return handle;
        }
        LuaValue changes = definition.get("changes");
        if (changes.isnil()) {
            changes = definition;
        }
        if (!changes.istable()) {
            throw new LuaError("Biome override changes must be a table.");
        }
        int priority = definition.get("priority").optint(0);
        List<OverrideManager.Request<?, ?>> requests = new ArrayList<OverrideManager.Request<?, ?>>();
        collect(changes, "", priority, requests);
        final List<OverrideManager.Layer<?, ?>> layers = OverrideManager.applyAll(requests);
        handle.set("active", TRUE);
        handle.set("remove", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                if (handle.get("active").toboolean()) {
                    for (int index = layers.size() - 1; index >= 0; index--) {
                        layers.get(index).remove();
                    }
                    handle.set("active", FALSE);
                }
                return NIL;
            }
        });
        return handle;
    }

    private void collect(LuaValue changes, String prefix, int priority, List<OverrideManager.Request<?, ?>> requests) {
        LuaValue key = NIL;
        while (true) {
            Varargs next = changes.next(key);
            key = next.arg1();
            if (key.isnil()) {
                return;
            }
            String field = key.checkjstring();
            if (prefix.length() == 0 && (field.equals("when") || field.equals("priority") || field.equals("target")
                    || field.equals("changes"))) {
                continue;
            }
            String path = prefix.length() == 0 ? field : prefix + "." + field;
            LuaValue value = next.arg(2);
            OverrideManager.Request<?, ?> request = request(path, value, priority);
            if (request != null) {
                requests.add(request);
            } else if (value.istable() && !path.equals("spawns")) {
                collect(value, path, priority, requests);
            } else {
                throw new LuaError("Property '" + path + "' cannot be overridden on biome " + biome.biomeName + ".");
            }
        }
    }

    private OverrideManager.Request<?, ?> request(String path, LuaValue value, int priority) {
        if (path.equals("name") || path.equals("displayName")) {
            return request(path, stringProperty(path, new StringAccess() {
                public String read() {
                    return biome.biomeName;
                }

                public void write(String value) {
                    biome.biomeName = value;
                }
            }), value.checkjstring(), priority);
        }
        if (path.equals("color")) {
            return request(path, intProperty(path, () -> biome.color, value1 -> biome.color = value1), value.checkint(),
                    priority);
        }
        if (path.equals("foliageColor")) {
            return request(path, intProperty(path, () -> biome.field_6502_q, value1 -> biome.field_6502_q = value1),
                    value.checkint(), priority);
        }
        if (path.equals("surface.top") || path.equals("surface.filler")) {
            final boolean top = path.endsWith("top");
            int block = BiomeDeclaration.resolveBlockId(value);
            OverrideManager.Property<BiomeGenBase, Integer> property = intProperty(path,
                    () -> top ? biome.topBlock & 255 : biome.fillerBlock & 255, value1 -> {
                        if (top) {
                            biome.topBlock = (byte) value1;
                        } else {
                            biome.fillerBlock = (byte) value1;
                        }
                    });
            return request(path, property, block, priority);
        }
        if (path.equals("weather.snow") || path.equals("weather.rain")) {
            final String[] names = path.endsWith("snow") ? SNOW : RAIN;
            OverrideManager.Property<BiomeGenBase, Boolean> property = new OverrideManager.Property<BiomeGenBase, Boolean>(
                    path, new OverrideManager.PropertyAdapter<BiomeGenBase, Boolean>() {
                        public Boolean read(BiomeGenBase target) {
                            return Boolean.valueOf(flag(target, names));
                        }

                        public void write(BiomeGenBase target, Boolean value) {
                            flag(target, names, value.booleanValue());
                        }
                    });
            return request(path, property, Boolean.valueOf(value.checkboolean()), priority);
        }
        if (path.equals("range.temperature") || path.equals("range.humidity")) {
            if (entry == null) {
                throw new LuaError("Native biome climate selection ranges are not discrete registrations.");
            }
            final boolean temperature = path.endsWith("temperature");
            Range parsed = parseRange(value, path);
            OverrideManager.Property<BiomeDefinition, Range> property = new OverrideManager.Property<BiomeDefinition, Range>(
                    path, new OverrideManager.PropertyAdapter<BiomeDefinition, Range>() {
                        public Range read(BiomeDefinition target) {
                            return temperature
                                    ? new Range(target.minTemperature, target.maxTemperature)
                                    : new Range(target.minHumidity, target.maxHumidity);
                        }

                        public void write(BiomeDefinition target, Range range) {
                            if (temperature) {
                                target.minTemperature = range.min;
                                target.maxTemperature = range.max;
                            } else {
                                target.minHumidity = range.min;
                                target.maxHumidity = range.max;
                            }
                            BiomeGenRegistry.applyBiomeGenerators();
                        }
                    });
            return OverrideManager.request(identity, entry, property, parsed, priority);
        }
        if (path.equals("trees.type") || path.equals("trees.bigTreeChance")) {
            if (!(biome instanceof BiomeGenWrapper)) {
                throw new LuaError("Tree mode overrides require a BetaMoon biome.");
            }
            final BiomeGenWrapper wrapper = (BiomeGenWrapper) biome;
            if (path.endsWith("type")) {
                OverrideManager.Property<BiomeGenBase, BiomeTreeMode> property = new OverrideManager.Property<BiomeGenBase, BiomeTreeMode>(
                        path, new OverrideManager.PropertyAdapter<BiomeGenBase, BiomeTreeMode>() {
                            public BiomeTreeMode read(BiomeGenBase target) {
                                return wrapper.treeMode();
                            }

                            public void write(BiomeGenBase target, BiomeTreeMode mode) {
                                wrapper.applyTreeMode(mode);
                            }
                        });
                return request(path, property, BiomeTreeMode.parse(value.checkjstring()), priority);
            }
            int chance = value.checkint();
            if (chance < 1) {
                throw new LuaError("trees.bigTreeChance must be at least 1.");
            }
            return request(path, intProperty(path, wrapper::bigTreeChance, wrapper::applyBigTreeChance), chance,
                    priority);
        }
        if (path.equals("spawns")) {
            final SpawnLists parsed = SpawnLists.fromDeclaration(BiomeDeclaration.readSpawns(value));
            OverrideManager.Property<BiomeGenBase, SpawnLists> property = new OverrideManager.Property<BiomeGenBase, SpawnLists>(
                    path, new OverrideManager.PropertyAdapter<BiomeGenBase, SpawnLists>() {
                        public SpawnLists read(BiomeGenBase target) {
                            return readSpawns(target);
                        }

                        public void write(BiomeGenBase target, SpawnLists spawns) {
                            spawns.write(target);
                        }
                    });
            return request(path, property, parsed, priority);
        }
        return null;
    }

    private static BiomeGenBase requiredBiome(WorldGenKey key) {
        BiomeGenBase biome = BiomeGenRegistry.biomeFor(key);
        if (biome == null) {
            throw new LuaError("Biome is not registered: " + key);
        }
        return biome;
    }

    private <V> OverrideManager.Request<BiomeGenBase, V> request(String path,
            OverrideManager.Property<BiomeGenBase, V> property, V value, int priority) {
        return OverrideManager.request(identity, biome, property, value, priority);
    }

    private OverrideManager.Property<BiomeGenBase, Integer> intProperty(String name, IntRead read, IntWrite write) {
        return new OverrideManager.Property<BiomeGenBase, Integer>(name,
                new OverrideManager.PropertyAdapter<BiomeGenBase, Integer>() {
                    public Integer read(BiomeGenBase target) {
                        return Integer.valueOf(read.read());
                    }

                    public void write(BiomeGenBase target, Integer value) {
                        write.write(value.intValue());
                    }
                });
    }

    private OverrideManager.Property<BiomeGenBase, String> stringProperty(String name, StringAccess access) {
        return new OverrideManager.Property<BiomeGenBase, String>(name,
                new OverrideManager.PropertyAdapter<BiomeGenBase, String>() {
                    public String read(BiomeGenBase target) {
                        return access.read();
                    }

                    public void write(BiomeGenBase target, String value) {
                        access.write(value);
                    }
                });
    }

    private static LuaTable range(double min, double max) {
        LuaTable result = new LuaTable();
        result.set("min", min);
        result.set("max", max);
        return result;
    }

    private static Range parseRange(LuaValue value, String path) {
        if (!value.istable()) {
            throw new LuaError(path + " must be a range table.");
        }
        double min = (value.get("min").isnil() ? value.get(1) : value.get("min")).checkdouble();
        double max = (value.get("max").isnil() ? value.get(2) : value.get("max")).checkdouble();
        if (!Double.isFinite(min) || !Double.isFinite(max) || min < 0 || max > 1 || min > max) {
            throw new LuaError(path + " must satisfy 0 <= min <= max <= 1.");
        }
        return new Range(min, max);
    }

    private static boolean flag(BiomeGenBase biome, String[] names) {
        try {
            return field(names).getBoolean(biome);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException(error);
        }
    }

    private static void flag(BiomeGenBase biome, String[] names, boolean value) {
        try {
            field(names).setBoolean(biome, value);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException(error);
        }
    }

    private static Field field(String[] names) {
        for (String name : names) {
            try {
                Field field = BiomeGenBase.class.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new IllegalStateException("Biome field is unavailable: " + names[0]);
    }

    @SuppressWarnings("unchecked")
    private static List<SpawnListEntry> list(BiomeGenBase biome, String[] names) {
        try {
            return (List<SpawnListEntry>) field(names).get(biome);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException(error);
        }
    }

    private static SpawnLists readSpawns(BiomeGenBase biome) {
        return new SpawnLists(copy(list(biome, MONSTERS)), copy(list(biome, CREATURES)), copy(list(biome, WATER)));
    }

    private static List<SpawnListEntry> copy(List<SpawnListEntry> source) {
        List<SpawnListEntry> result = new ArrayList<SpawnListEntry>();
        for (SpawnListEntry entry : source) {
            result.add(new SpawnListEntry(entry.entityClass, entry.spawnRarityRate));
        }
        return result;
    }

    private static LuaTable spawnTable(SpawnLists groups) {
        LuaTable result = new LuaTable();
        result.set("monsters", spawnList(groups.monsters));
        result.set("creatures", spawnList(groups.creatures));
        result.set("water", spawnList(groups.water));
        return result;
    }

    private static LuaTable spawnList(List<SpawnListEntry> entries) {
        LuaTable result = new LuaTable();
        for (int index = 0; index < entries.size(); index++) {
            SpawnListEntry entry = entries.get(index);
            LuaTable value = new LuaTable();
            value.set("entity", entry.entityClass.getSimpleName());
            value.set("weight", entry.spawnRarityRate);
            result.set(index + 1, value);
        }
        return result;
    }

    private interface IntRead {
        int read();
    }

    private interface IntWrite {
        void write(int value);
    }

    private interface StringAccess {
        String read();

        void write(String value);
    }

    private static final class Range {
        private final double min;
        private final double max;

        private Range(double min, double max) {
            this.min = min;
            this.max = max;
        }
    }

    private static final class SpawnLists {
        private final List<SpawnListEntry> monsters;
        private final List<SpawnListEntry> creatures;
        private final List<SpawnListEntry> water;

        private SpawnLists(List<SpawnListEntry> monsters, List<SpawnListEntry> creatures, List<SpawnListEntry> water) {
            this.monsters = Collections.unmodifiableList(monsters);
            this.creatures = Collections.unmodifiableList(creatures);
            this.water = Collections.unmodifiableList(water);
        }

        private static SpawnLists fromDeclaration(Map<BiomeSpawnGroup, List<BiomeDeclaration.Spawn>> groups) {
            Map<BiomeSpawnGroup, List<SpawnListEntry>> values = new LinkedHashMap<BiomeSpawnGroup, List<SpawnListEntry>>();
            for (BiomeSpawnGroup group : BiomeSpawnGroup.values()) {
                List<SpawnListEntry> entries = new ArrayList<SpawnListEntry>();
                List<BiomeDeclaration.Spawn> declared = groups.get(group);
                if (declared != null) {
                    for (BiomeDeclaration.Spawn spawn : declared) {
                        entries.add(new SpawnListEntry(spawn.entity, spawn.weight));
                    }
                }
                values.put(group, entries);
            }
            return new SpawnLists(values.get(BiomeSpawnGroup.MONSTER), values.get(BiomeSpawnGroup.CREATURE),
                    values.get(BiomeSpawnGroup.WATER));
        }

        private void write(BiomeGenBase biome) {
            replace(list(biome, MONSTERS), monsters);
            replace(list(biome, CREATURES), creatures);
            replace(list(biome, WATER), water);
        }

        private static void replace(List<SpawnListEntry> target, List<SpawnListEntry> source) {
            target.clear();
            target.addAll(copy(source));
        }
    }
}

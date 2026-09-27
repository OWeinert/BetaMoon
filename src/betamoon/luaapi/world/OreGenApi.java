package betamoon.luaapi.world;

import betamoon.assets.AssetKey;
import betamoon.luaapi.resource.LuaResultList;
import betamoon.luaapi.resource.OverrideManager;
import betamoon.worldgen.GenerationDimension;
import betamoon.worldgen.WorldGenRegistry;
import betamoon.worldgen.WorldGenRegistry.OreGenEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.src.BiomeGenBase;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Installs queryable, layered ore-generation registrations. */
public final class OreGenApi {
    private static final Map<OreGenEntry, OreReference> REFERENCES = new IdentityHashMap<OreGenEntry, OreReference>();

    private OreGenApi() {
    }

    public static void attach(LuaTable worldgen) {
        final LuaTable ores = new LuaTable();
        ores.set("add", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                OreDeclaration declaration = new OreDeclaration(argument(args, ores));
                OreGenEntry entry = WorldGenRegistry.addOreGen(declaration.key, declaration.blockId,
                        declaration.veinsPerChunk,
                        declaration.veinSize, declaration.minY, declaration.maxY, declaration.dimension,
                        declaration.targetBlockId, declaration.getAllowedBiomes());
                return reference(entry);
            }
        });
        ores.set("get", lookup(ores, false));
        ores.set("getRequired", lookup(ores, true));
        ores.set("find", new Find(ores, 0));
        ores.set("first", new Find(ores, 1));
        ores.set("one", new Find(ores, 2));
        worldgen.set("ores", ores);
    }

    private static VarArgFunction lookup(final LuaTable registry, final boolean required) {
        return new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                AssetKey key;
                try {
                    key = AssetKey.parse(argument(args, registry).checkjstring());
                } catch (IllegalArgumentException error) {
                    throw new LuaError("ore key: " + error.getMessage());
                }
                for (OreGenEntry entry : WorldGenRegistry.oreEntries()) {
                    if (key.equals(entry.key)) {
                        return reference(entry);
                    }
                }
                if (required) {
                    throw new LuaError("Ore rule is not registered: " + key);
                }
                return NIL;
            }
        };
    }

    private static final class Find extends VarArgFunction {
        private final LuaTable registry;
        private final int mode;

        private Find(LuaTable registry, int mode) {
            this.registry = registry;
            this.mode = mode;
        }

        @Override
        public Varargs invoke(Varargs args) {
            LuaValue criteria = argument(args, registry);
            if (criteria.isnil()) {
                criteria = new LuaTable();
            }
            if (!criteria.istable()) {
                throw new LuaError("ore query must be a table.");
            }
            List<LuaValue> references = new ArrayList<LuaValue>();
            for (OreGenEntry entry : WorldGenRegistry.oreEntries()) {
                OreReference reference = reference(entry);
                if (reference.matches(criteria)) {
                    references.add(reference);
                }
            }
            if (mode == 1) {
                return references.isEmpty() ? NIL : references.get(0);
            }
            if (mode == 2) {
                if (references.isEmpty()) {
                    return NIL;
                }
                if (references.size() != 1) {
                    throw new LuaError("Expected exactly one ore rule, found " + references.size() + ".");
                }
                return references.get(0);
            }
            return new LuaResultList(references, (reference, definition, index) ->
                    ((OreReference) reference).applyOverride(definition));
        }
    }

    private static final class OreReference extends LuaTable {
        private final OreGenEntry entry;

        private OreReference(OreGenEntry entry) {
            this.entry = entry;
            set("override", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    return applyOverride(argument(args, OreReference.this));
                }
            });
        }

        @Override
        public LuaValue get(LuaValue key) {
            if (key.isstring()) {
                String property = key.tojstring();
                if (property.equals("id")) {
                    return valueOf(entry.id);
                }
                if (property.equals("key")) {
                    return entry.key == null ? NIL : valueOf(entry.key.toString());
                }
                if (property.equals("owner")) {
                    return valueOf(entry.owner);
                }
                if (property.equals("exists")) {
                    return TRUE;
                }
                if (property.equals("enabled")) {
                    return valueOf(entry.enabled);
                }
                if (property.equals("block")) {
                    return valueOf(entry.blockId);
                }
                if (property.equals("veinsPerChunk")) {
                    return valueOf(entry.veinsPerChunk);
                }
                if (property.equals("veinSize")) {
                    return valueOf(entry.veinSize);
                }
                if (property.equals("height")) {
                    LuaTable height = new LuaTable();
                    height.set("min", entry.minY);
                    height.set("max", entry.maxY);
                    return height;
                }
                if (property.equals("dimension")) {
                    return valueOf(entry.dimension.getLuaName());
                }
                if (property.equals("replace")) {
                    return entry.targetBlockId == null ? NIL : valueOf(entry.targetBlockId.intValue());
                }
                if (property.equals("biomes")) {
                    LuaTable biomes = new LuaTable();
                    BiomeGenBase[] values = entry.getAllowedBiomes();
                    if (values != null) {
                        for (int i = 0; i < values.length; i++) {
                            biomes.set(i + 1, values[i].biomeName);
                        }
                    }
                    return biomes;
                }
            }
            return super.get(key);
        }

        private boolean matches(LuaValue criteria) {
            if (!criteria.get("key").isnil()
                    && (entry.key == null || !entry.key.toString().equals(criteria.get("key").checkjstring()))) {
                return false;
            }
            if (!criteria.get("owner").isnil()
                    && !entry.owner.equals(criteria.get("owner").checkjstring())) {
                return false;
            }
            if (!criteria.get("enabled").isnil()
                    && entry.enabled != criteria.get("enabled").checkboolean()) {
                return false;
            }
            if (!criteria.get("block").isnil()
                    && entry.blockId != OreDeclaration.resolveBlockId(criteria.get("block"))) {
                return false;
            }
            if (!criteria.get("dimension").isnil()
                    && entry.dimension != GenerationDimension.parse(criteria.get("dimension").checkjstring())) {
                return false;
            }
            return true;
        }

        private LuaValue applyOverride(LuaValue definition) {
            if (!definition.istable()) {
                throw new LuaError("ore override expects a table.");
            }
            final LuaTable handle = new LuaTable();
            handle.set("target", this);
            LuaValue when = definition.get("when");
            if (!when.isnil()) {
                if (!when.istable()) {
                    throw new LuaError("ore override when must be a table.");
                }
                if (!when.get("owner").isnil() && !entry.owner.equals(when.get("owner").checkjstring())) {
                    handle.set("active", FALSE);
                    handle.set("reason", "target owner did not match");
                    return handle;
                }
            }
            LuaValue changes = definition.get("changes");
            if (changes.isnil()) {
                changes = definition;
            }
            if (!changes.istable()) {
                throw new LuaError("ore override changes must be a table.");
            }
            int priority = definition.get("priority").optint(0);
            List<OverrideManager.Request<?, ?>> requests = new ArrayList<OverrideManager.Request<?, ?>>();
            LuaValue key = NIL;
            while (!(key = changes.next(key).arg1()).isnil()) {
                String property = key.checkjstring();
                if (isControlProperty(property)) {
                    continue;
                }
                requests.add(request(property, changes.get(key), priority));
            }
            final List<OverrideManager.Layer<?, ?>> layers = OverrideManager.applyAll(requests);
            handle.set("active", TRUE);
            handle.set("remove", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    if (handle.get("active").toboolean()) {
                        for (int i = layers.size() - 1; i >= 0; i--) {
                            layers.get(i).remove();
                        }
                        handle.set("active", FALSE);
                    }
                    return NIL;
                }
            });
            return handle;
        }

        private OverrideManager.Request<?, ?> request(String property, LuaValue value, int priority) {
            String target = "ore:" + entry.id;
            if (property.equals("enabled")) {
                return OverrideManager.request(target, entry, booleanProperty("enabled", new BooleanAccess() {
                    public boolean get(OreGenEntry value) {
                        return value.enabled;
                    }

                    public void set(OreGenEntry target, boolean value) {
                        target.enabled = value;
                    }
                }), Boolean.valueOf(value.checkboolean()), priority);
            }
            if (property.equals("block")) {
                return OverrideManager.request(target, entry, integerProperty("block", new IntegerAccess() {
                    public int get(OreGenEntry value) {
                        return value.blockId;
                    }

                    public void set(OreGenEntry target, int value) {
                        target.blockId = value;
                    }
                }), Integer.valueOf(OreDeclaration.resolveBlockId(value)), priority);
            }
            if (property.equals("veinsPerChunk") || property.equals("veinSize")) {
                int parsed = positive(value, property);
                IntegerAccess access = property.equals("veinsPerChunk") ? new IntegerAccess() {
                    public int get(OreGenEntry value) {
                        return value.veinsPerChunk;
                    }

                    public void set(OreGenEntry target, int value) {
                        target.veinsPerChunk = value;
                    }
                } : new IntegerAccess() {
                    public int get(OreGenEntry value) {
                        return value.veinSize;
                    }

                    public void set(OreGenEntry target, int value) {
                        target.veinSize = value;
                    }
                };
                return OverrideManager.request(target, entry, integerProperty(property, access),
                        Integer.valueOf(parsed), priority);
            }
            if (property.equals("height")) {
                Height height = Height.parse(value);
                OverrideManager.Property<OreGenEntry, Height> definition = new OverrideManager.Property<OreGenEntry, Height>(
                        "height", new OverrideManager.PropertyAdapter<OreGenEntry, Height>() {
                            public Height read(OreGenEntry target) {
                                return new Height(target.minY, target.maxY);
                            }

                            public void write(OreGenEntry target, Height value) {
                                target.minY = value.min;
                                target.maxY = value.max;
                            }
                        });
                return OverrideManager.request(target, entry, definition, height, priority);
            }
            if (property.equals("dimension")) {
                OverrideManager.Property<OreGenEntry, GenerationDimension> definition = new OverrideManager.Property<OreGenEntry, GenerationDimension>(
                        "dimension", new OverrideManager.PropertyAdapter<OreGenEntry, GenerationDimension>() {
                            public GenerationDimension read(OreGenEntry target) {
                                return target.dimension;
                            }

                            public void write(OreGenEntry target, GenerationDimension value) {
                                target.dimension = value;
                            }
                        });
                return OverrideManager.request(target, entry, definition,
                        GenerationDimension.parse(value.checkjstring()), priority);
            }
            if (property.equals("replace")) {
                Integer replacement = value.isboolean() && !value.toboolean()
                        ? null : Integer.valueOf(OreDeclaration.resolveBlockId(value));
                OverrideManager.Property<OreGenEntry, Integer> definition = new OverrideManager.Property<OreGenEntry, Integer>(
                        "replace", new OverrideManager.PropertyAdapter<OreGenEntry, Integer>() {
                            public Integer read(OreGenEntry target) {
                                return target.targetBlockId;
                            }

                            public void write(OreGenEntry target, Integer value) {
                                target.targetBlockId = value;
                            }
                        });
                return OverrideManager.request(target, entry, definition, replacement, priority);
            }
            if (property.equals("biomes")) {
                Biomes biomes = Biomes.parse(value);
                OverrideManager.Property<OreGenEntry, Biomes> definition = new OverrideManager.Property<OreGenEntry, Biomes>(
                        "biomes", new OverrideManager.PropertyAdapter<OreGenEntry, Biomes>() {
                            public Biomes read(OreGenEntry target) {
                                return new Biomes(target.getAllowedBiomes());
                            }

                            public void write(OreGenEntry target, Biomes value) {
                                target.setAllowedBiomes(value.values);
                            }
                        });
                return OverrideManager.request(target, entry, definition, biomes, priority);
            }
            throw new LuaError("Property '" + property + "' cannot be overridden on an ore rule.");
        }
    }

    private static synchronized OreReference reference(OreGenEntry entry) {
        OreReference reference = REFERENCES.get(entry);
        if (reference == null) {
            reference = new OreReference(entry);
            REFERENCES.put(entry, reference);
        }
        return reference;
    }

    private interface IntegerAccess {
        int get(OreGenEntry target);

        void set(OreGenEntry target, int value);
    }

    private interface BooleanAccess {
        boolean get(OreGenEntry target);

        void set(OreGenEntry target, boolean value);
    }

    private static OverrideManager.Property<OreGenEntry, Integer> integerProperty(String name,
            final IntegerAccess access) {
        return new OverrideManager.Property<OreGenEntry, Integer>(name,
                new OverrideManager.PropertyAdapter<OreGenEntry, Integer>() {
                    public Integer read(OreGenEntry target) {
                        return Integer.valueOf(access.get(target));
                    }

                    public void write(OreGenEntry target, Integer value) {
                        access.set(target, value.intValue());
                    }
                });
    }

    private static OverrideManager.Property<OreGenEntry, Boolean> booleanProperty(String name,
            final BooleanAccess access) {
        return new OverrideManager.Property<OreGenEntry, Boolean>(name,
                new OverrideManager.PropertyAdapter<OreGenEntry, Boolean>() {
                    public Boolean read(OreGenEntry target) {
                        return Boolean.valueOf(access.get(target));
                    }

                    public void write(OreGenEntry target, Boolean value) {
                        access.set(target, value.booleanValue());
                    }
                });
    }

    private static final class Height {
        private final int min;
        private final int max;

        private Height(int min, int max) {
            this.min = min;
            this.max = max;
        }

        private static Height parse(LuaValue value) {
            if (!value.istable()) {
                throw new LuaError("ore override height must be a table.");
            }
            int min = value.get("min").checkint();
            int max = value.get("max").checkint();
            if (min < 0 || max < min || max > 127) {
                throw new LuaError("ore override height must satisfy 0 <= min <= max <= 127.");
            }
            return new Height(min, max);
        }
    }

    private static final class Biomes {
        private final BiomeGenBase[] values;

        private Biomes(BiomeGenBase[] values) {
            this.values = values == null ? null : values.clone();
        }

        private static Biomes parse(LuaValue value) {
            if (value.isboolean() && !value.toboolean()) {
                return new Biomes(null);
            }
            return new Biomes(WorldGenRegistry.resolveBiomes(OreDeclaration.biomeNames(value)));
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Biomes && Arrays.equals(values, ((Biomes) other).values);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(values);
        }
    }

    private static int positive(LuaValue value, String property) {
        int parsed = value.checkint();
        if (parsed <= 0) {
            throw new LuaError(property + " must be positive.");
        }
        return parsed;
    }

    private static boolean isControlProperty(String property) {
        return property.equals("when") || property.equals("priority") || property.equals("target")
                || property.equals("changes");
    }

    private static LuaValue argument(Varargs args, LuaValue receiver) {
        return args.arg(args.arg1() == receiver ? 2 : 1);
    }
}

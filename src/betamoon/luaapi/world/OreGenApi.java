package betamoon.luaapi.world;

import betamoon.luaapi.resource.LuaResultList;
import betamoon.luaapi.resource.OverrideManager;
import betamoon.worldgen.GenerationDimension;
import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.WorldGenKind;
import betamoon.worldgen.WorldGenLimits;
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

/**
 * Installs declarative ore generation, lookup, queries, and reversible
 * overrides.
 */
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
                WorldGenKey key = WorldGenRegistry.addOreGen(declaration.key, declaration.blockId,
                        declaration.veinsPerChunk, declaration.veinSize, declaration.minY, declaration.maxY,
                        declaration.dimension, declaration.targetBlockId, declaration.getAllowedBiomes(),
                        declaration.salt);
                return reference(WorldGenRegistry.oreEntry(key));
            }
        });
        ores.set("get", lookup(ores, false));
        ores.set("getRequired", lookup(ores, true));
        ores.set("find", query(ores, 0));
        ores.set("first", query(ores, 1));
        ores.set("one", query(ores, 2));
        worldgen.set("ores", ores);
    }

    private static VarArgFunction lookup(final LuaTable receiver, final boolean required) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                WorldGenKey key = key(argument(args, receiver));
                OreGenEntry entry = WorldGenRegistry.oreEntry(key);
                if (entry == null && required) {
                    throw new LuaError("Ore rule is not registered: " + key);
                }
                return entry == null ? NIL : reference(entry);
            }
        };
    }

    private static VarArgFunction query(final LuaTable receiver, final int mode) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaValue criteria = argument(args, receiver);
                if (criteria.isnil()) {
                    criteria = new LuaTable();
                }
                if (!criteria.istable()) {
                    throw new LuaError("Ore query must be a table.");
                }
                List<LuaValue> matches = new ArrayList<LuaValue>();
                for (OreGenEntry entry : WorldGenRegistry.oreEntries()) {
                    OreReference reference = reference(entry);
                    if (reference.matches(criteria)) {
                        matches.add(reference);
                    }
                }
                if (mode == 1) {
                    return matches.isEmpty() ? NIL : matches.get(0);
                }
                if (mode == 2) {
                    if (matches.isEmpty()) {
                        return NIL;
                    }
                    if (matches.size() != 1) {
                        throw new LuaError("Expected exactly one ore rule, found " + matches.size() + ".");
                    }
                    return matches.get(0);
                }
                return new LuaResultList(matches,
                        (reference, definition, index) -> ((OreReference) reference).override(definition));
            }
        };
    }

    private static WorldGenKey key(LuaValue value) {
        if (value instanceof OreReference) {
            return ((OreReference) value).key();
        }
        try {
            return WorldGenKey.parse(value.checkjstring(), WorldGenKind.PLACEMENT);
        } catch (IllegalArgumentException error) {
            throw new LuaError("Ore key: " + error.getMessage());
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

    private static final class OreReference extends PlacementReference {
        private final OreGenEntry entry;

        private OreReference(OreGenEntry entry) {
            super(entry.key, entry.featureKey);
            this.entry = entry;
            set("override", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    return override(argument(args, OreReference.this));
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
                    return valueOf(entry.key.toString());
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
                    for (int index = 0; index < values.length; index++) {
                        biomes.set(index + 1, values[index].biomeName);
                    }
                    return biomes;
                }
            }
            return super.get(key);
        }

        private boolean matches(LuaValue criteria) {
            if (!criteria.get("key").isnil() && !entry.key.toString().equals(criteria.get("key").checkjstring())) {
                return false;
            }
            if (!criteria.get("owner").isnil() && !entry.owner.equals(criteria.get("owner").checkjstring())) {
                return false;
            }
            if (!criteria.get("enabled").isnil() && entry.enabled != criteria.get("enabled").checkboolean()) {
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

        private LuaValue override(LuaValue definition) {
            if (!definition.istable()) {
                throw new LuaError("Ore override expects a table.");
            }
            LuaTable handle = new LuaTable();
            handle.set("target", this);
            LuaValue when = definition.get("when");
            if (!when.isnil()) {
                if (!when.istable()) {
                    throw new LuaError("Ore override when must be a table.");
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
                throw new LuaError("Ore override changes must be a table.");
            }
            int priority = definition.get("priority").optint(0);
            List<OverrideManager.Request<?, ?>> requests = new ArrayList<OverrideManager.Request<?, ?>>();
            LuaValue field = NIL;
            while (!(field = changes.next(field).arg1()).isnil()) {
                String property = field.checkjstring();
                if (!isControlProperty(property)) {
                    requests.add(request(property, changes.get(field), priority));
                }
            }
            final List<OverrideManager.Layer<?, ?>> layers = OverrideManager.applyAll(requests);
            handle.set("active", TRUE);
            handle.set("remove", new VarArgFunction() {
                @Override
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

        private OverrideManager.Request<?, ?> request(String property, LuaValue value, int priority) {
            if (property.equals("enabled")) {
                return request(property, valueOf(value.checkboolean()), priority, target -> valueOf(target.enabled),
                        (target, next) -> target.enabled = next.checkboolean());
            }
            if (property.equals("block")) {
                int parsed = OreDeclaration.resolveBlockId(value);
                return request(property, valueOf(parsed), priority, target -> valueOf(target.blockId),
                        (target, next) -> target.blockId = next.checkint());
            }
            if (property.equals("veinsPerChunk")) {
                int parsed = integer(value, property, 0, WorldGenLimits.MAX_ATTEMPTS_PER_CHUNK);
                return request(property, valueOf(parsed), priority, target -> valueOf(target.veinsPerChunk),
                        (target, next) -> target.veinsPerChunk = next.checkint());
            }
            if (property.equals("veinSize")) {
                int parsed = integer(value, property, 1, WorldGenLimits.MAX_ORE_VEIN_SIZE);
                return request(property, valueOf(parsed), priority, target -> valueOf(target.veinSize),
                        (target, next) -> target.veinSize = next.checkint());
            }
            if (property.equals("height")) {
                Height parsed = Height.parse(value);
                return typedRequest(property, parsed, priority, target -> new Height(target.minY, target.maxY),
                        (target, next) -> {
                            target.minY = next.min;
                            target.maxY = next.max;
                        });
            }
            if (property.equals("dimension")) {
                GenerationDimension parsed = GenerationDimension.parse(value.checkjstring());
                return typedRequest(property, parsed, priority, target -> target.dimension,
                        (target, next) -> target.dimension = next);
            }
            if (property.equals("replace")) {
                Integer parsed = value.isboolean() && !value.toboolean()
                        ? null
                        : Integer.valueOf(OreDeclaration.resolveBlockId(value));
                return typedRequest(property, parsed, priority, target -> target.targetBlockId,
                        (target, next) -> target.targetBlockId = next);
            }
            if (property.equals("biomes")) {
                Biomes parsed = Biomes.parse(value);
                return typedRequest(property, parsed, priority, target -> new Biomes(target.getAllowedBiomes()),
                        (target, next) -> target.setAllowedBiomes(next.values));
            }
            throw new LuaError("Property '" + property + "' cannot be overridden on an ore rule.");
        }

        private OverrideManager.Request<OreGenEntry, LuaValue> request(String property, LuaValue value, int priority,
                Reader<LuaValue> reader, Writer<LuaValue> writer) {
            return typedRequest(property, value, priority, reader, writer);
        }

        private <T> OverrideManager.Request<OreGenEntry, T> typedRequest(String property, T value, int priority,
                final Reader<T> reader, final Writer<T> writer) {
            OverrideManager.Property<OreGenEntry, T> definition = new OverrideManager.Property<OreGenEntry, T>(property,
                    new OverrideManager.PropertyAdapter<OreGenEntry, T>() {
                        @Override
                        public T read(OreGenEntry target) {
                            return reader.read(target);
                        }

                        @Override
                        public void write(OreGenEntry target, T next) {
                            writer.write(target, next);
                            WorldGenRegistry.refreshOre(target);
                        }
                    });
            return OverrideManager.request("ore:" + entry.key, entry, definition, value, priority);
        }
    }

    private interface Reader<T> {
        T read(OreGenEntry target);
    }

    private interface Writer<T> {
        void write(OreGenEntry target, T value);
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
                throw new LuaError("Ore override height must be a table.");
            }
            int min = value.get("min").checkint();
            int max = value.get("max").checkint();
            if (min < WorldGenLimits.MIN_HEIGHT || max > WorldGenLimits.MAX_HEIGHT || min > max) {
                throw new LuaError("Ore override height must satisfy 0 <= min <= max <= 127.");
            }
            return new Height(min, max);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Height && min == ((Height) other).min && max == ((Height) other).max;
        }

        @Override
        public int hashCode() {
            return min * 31 + max;
        }
    }

    private static final class Biomes {
        private final BiomeGenBase[] values;

        private Biomes(BiomeGenBase[] values) {
            this.values = values == null ? new BiomeGenBase[0] : values.clone();
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

    private static int integer(LuaValue value, String property, int min, int max) {
        int parsed = value.checkint();
        if (parsed < min || parsed > max) {
            throw new LuaError(property + " must be between " + min + " and " + max + ".");
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

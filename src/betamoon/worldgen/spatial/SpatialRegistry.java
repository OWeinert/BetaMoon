package betamoon.worldgen.spatial;

import betamoon.worldgen.WorldGenKey;
import betamoon.worldgen.WorldGenKind;
import betamoon.worldgen.terrain.GenerationSignature;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static betamoon.worldgen.spatial.GenerationValues.error;
import static betamoon.worldgen.spatial.GenerationValues.fields;
import static betamoon.worldgen.spatial.GenerationValues.freeze;
import static betamoon.worldgen.spatial.GenerationValues.number;
import static betamoon.worldgen.spatial.GenerationValues.object;
import static betamoon.worldgen.spatial.GenerationValues.required;
import static betamoon.worldgen.spatial.GenerationValues.string;

/**
 * Owner-scoped spatial publication; each world can pin one immutable compiled
 * snapshot.
 */
public final class SpatialRegistry {
    private static final ThreadLocal<Batch> CURRENT = new ThreadLocal<Batch>();
    private static volatile Snapshot active = new Snapshot(Collections.<String, Declaration>emptyMap());

    private SpatialRegistry() {
    }

    public static Batch begin(String owner) {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("Nested spatial declaration batches are unsupported");
        }
        Batch batch = new Batch(owner);
        CURRENT.set(batch);
        return batch;
    }

    public static String add(WorldGenKind kind, Map<String, Object> document) {
        Batch batch = CURRENT.get();
        if (batch == null) {
            throw error("worldgen.spatial", "declare during modInit/modReload");
        }
        document = GenerationDocuments.resolve(batch.owner, document);
        String key = WorldGenKey.parse(string(required(document, "key", "spatial"), "spatial.key"), kind).toString();
        if (key.startsWith("minecraft:") || key.startsWith("betamoon:")) {
            throw error(key, "reserved namespace");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> frozen = (Map<String, Object>) freeze(document);
        if (batch.declarations.put(key, new Declaration(key, batch.owner, kind, frozen)) != null) {
            throw error(key, "duplicate spatial declaration");
        }
        if (batch.declarations.size() > 256) {
            throw error(key, "owner exceeds spatial declaration budget");
        }
        batch.validated = null;
        return key;
    }

    public static boolean exists(WorldGenKind kind, String key) {
        String typed = WorldGenKey.parse(key, kind).toString();
        Batch pending = CURRENT.get();
        return pending != null && pending.declarations.containsKey(typed) || active.declarations.containsKey(typed);
    }

    public static Snapshot snapshot() {
        return active;
    }

    public static synchronized void retainOwners(Set<String> owners) {
        Map<String, Declaration> retained = new LinkedHashMap<String, Declaration>();
        for (Declaration declaration : active.declarations.values()) {
            if (owners.contains(declaration.owner)) {
                retained.put(declaration.key, declaration);
            }
        }
        boolean changed;
        do {
            changed = false;
            for (Declaration declaration : new ArrayList<Declaration>(retained.values())) {
                for (String dependency : active.referencesFor(declaration.key)) {
                    if (!retained.containsKey(dependency)) {
                        retained.remove(declaration.key);
                        changed = true;
                        break;
                    }
                }
            }
        } while (changed);
        active = new Snapshot(retained);
    }

    public static final class Batch implements AutoCloseable {
        private final String owner;
        private final Map<String, Declaration> declarations = new LinkedHashMap<String, Declaration>();
        private Snapshot validated;

        private Batch(String owner) {
            this.owner = owner;
        }

        public void validate() {
            Map<String, Declaration> combined = new LinkedHashMap<String, Declaration>();
            for (Declaration declaration : active.declarations.values()) {
                if (!declaration.owner.equals(owner)) {
                    combined.put(declaration.key, declaration);
                }
            }
            for (Declaration declaration : declarations.values()) {
                if (combined.put(declaration.key, declaration) != null) {
                    throw error(declaration.key, "belongs to another script");
                }
            }
            validated = new Snapshot(combined);
        }

        public Snapshot resolved() {
            if (validated == null) {
                validate();
            }
            return validated;
        }

        public void publish() {
            active = resolved();
        }

        @Override
        public void close() {
            CURRENT.remove();
        }
    }

    public static List<Map<String, Object>> describeDefinitions() {
        Snapshot snapshot = active;
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Declaration declaration : snapshot.declarations.values()) {
            Map<String, Object> row = new TreeMap<String, Object>();
            row.put("key", declaration.key);
            row.put("owner", declaration.owner);
            row.put("kind", declaration.kind.getPath());
            row.put("signature", snapshot.signature(declaration.key, declaration.kind));
            if (declaration.kind == WorldGenKind.FIELD) {
                ScalarField field = snapshot.field(declaration.key);
                row.put("min", field.min);
                row.put("max", field.max);
                row.put("dependencies", field.dependencies);
                row.put("nodes", field.nodeCount);
                row.put("sampleWork", field.sampleWork);
            }
            result.add(Collections.unmodifiableMap(row));
        }
        return Collections.unmodifiableList(result);
    }

    public static Snapshot pendingSnapshot() {
        Batch batch = CURRENT.get();
        return batch == null ? active : batch.resolved();
    }

    public static final class Snapshot implements ScalarFieldCompiler.Resolver {
        private final Map<String, Declaration> declarations;
        private final Map<String, ScalarField> fields = new LinkedHashMap<String, ScalarField>();
        private final Map<String, NoiseDefinition> noises = new LinkedHashMap<String, NoiseDefinition>();
        private final Map<String, AnchorDefinition> anchors = new LinkedHashMap<String, AnchorDefinition>();
        private final Map<String, String> signatures = new TreeMap<String, String>();
        private final Set<String> resolving = new LinkedHashSet<String>();
        private final Map<String, Set<String>> references = new LinkedHashMap<String, Set<String>>();
        public final String signature;

        private Snapshot(Map<String, Declaration> values) {
            declarations = Collections.unmodifiableMap(new TreeMap<String, Declaration>(values));
            if (declarations.size() > 4096) {
                throw error("spatial", "global declaration budget exceeded");
            }
            for (Declaration declaration : declarations.values()) {
                if (declaration.kind == WorldGenKind.NOISE) {
                    noises.put(declaration.key, new NoiseDefinition(declaration.key, declaration.value));
                } else if (declaration.kind == WorldGenKind.ANCHOR) {
                    anchors.put(declaration.key, new AnchorDefinition(declaration.key, declaration.value));
                }
                signatures.put(declaration.key, GenerationValues.canonical(declaration.value));
            }
            for (Declaration declaration : declarations.values()) {
                if (declaration.kind == WorldGenKind.FIELD) {
                    field(declaration.key);
                }
            }
            signature = GenerationSignature.of(signatures);
        }

        @Override
        public ScalarField field(String key) {
            key = WorldGenKey.parse(key, WorldGenKind.FIELD).toString();
            reference(key);
            ScalarField existing = fields.get(key);
            if (existing != null) {
                return existing;
            }
            Declaration declaration = declarations.get(key);
            if (declaration == null || declaration.kind != WorldGenKind.FIELD) {
                throw error(key, "unknown field reference");
            }
            if (!resolving.add(key) || resolving.size() > ScalarFieldCompiler.MAX_DEPTH) {
                throw error(key, "cyclic or excessively deep field references: " + resolving);
            }
            try {
                fields(declaration.value, key, "key", "node", "range", "dimension");
                if (declaration.value.containsKey("dimension")) {
                    AnchorDefinition.dimension(declaration.value.get("dimension"), key + ".dimension");
                }
                ScalarField compiled = new ScalarFieldCompiler(key, this)
                        .compile(required(declaration.value, "node", key));
                if (declaration.value.containsKey("range")) {
                    Map<String, Object> range = object(declaration.value.get("range"), key + ".range");
                    fields(range, key + ".range", "min", "max");
                    double min = number(required(range, "min", key), key + ".range.min");
                    double max = number(required(range, "max", key), key + ".range.max");
                    if (min > max || compiled.min < min || compiled.max > max) {
                        throw error(key + ".range", "declared range does not contain every possible sample: "
                                + compiled.min + ".." + compiled.max);
                    }
                }
                fields.put(key, compiled);
                return compiled;
            } finally {
                resolving.remove(key);
            }
        }

        @Override
        public NoiseDefinition noise(String key) {
            key = WorldGenKey.parse(key, WorldGenKind.NOISE).toString();
            reference(key);
            NoiseDefinition value = noises.get(key);
            if (value == null) {
                throw error(key, "unknown noise reference");
            }
            return value;
        }

        @Override
        public AnchorDefinition anchor(String key) {
            key = WorldGenKey.parse(key, WorldGenKind.ANCHOR).toString();
            reference(key);
            AnchorDefinition value = anchors.get(key);
            if (value == null) {
                throw error(key, "unknown anchor reference");
            }
            return value;
        }

        public ScalarField fieldIfPresent(String canonicalKey) {
            return fields.get(canonicalKey);
        }

        public String signature(String key, WorldGenKind kind) {
            key = WorldGenKey.parse(key, kind).toString();
            Map<String, String> transitive = new TreeMap<String, String>();
            collect(key, transitive);
            return GenerationSignature.of(transitive);
        }

        public Map<String, String> dependenciesIn(Object value) {
            Map<String, String> result = new TreeMap<String, String>();
            dependenciesIn(value, result);
            return result;
        }

        private void dependenciesIn(Object value, Map<String, String> result) {
            if (value instanceof Map) {
                for (Object child : ((Map<?, ?>) value).values()) {
                    dependenciesIn(child, result);
                }
            } else if (value instanceof List) {
                for (Object child : (List<?>) value) {
                    dependenciesIn(child, result);
                }
            } else if (value instanceof String && ((String) value).contains(":")) {
                for (WorldGenKind kind : new WorldGenKind[]{WorldGenKind.FIELD, WorldGenKind.NOISE,
                        WorldGenKind.ANCHOR}) {
                    try {
                        String key = WorldGenKey.parse((String) value, kind).toString();
                        if (declarations.containsKey(key)) {
                            collect(key, result);
                        }
                    } catch (IllegalArgumentException ignored) {
                        // A different typed key is handled by its owning registry.
                    }
                }
            }
        }

        private void collect(String key, Map<String, String> result) {
            String value = signatures.get(key);
            if (value == null) {
                throw error(key, "unknown spatial dependency");
            }
            if (result.put(key, value) != null) {
                return;
            }
            for (String dependency : references.getOrDefault(key, Collections.<String>emptySet())) {
                collect(dependency, result);
            }
        }

        private void reference(String key) {
            for (String parent : resolving) {
                references.computeIfAbsent(parent, ignored -> new LinkedHashSet<String>()).add(key);
            }
        }

        private Set<String> referencesFor(String key) {
            return references.getOrDefault(key, Collections.<String>emptySet());
        }
    }

    private static final class Declaration {
        private final String key;
        private final String owner;
        private final WorldGenKind kind;
        private final Map<String, Object> value;

        private Declaration(String key, String owner, WorldGenKind kind, Map<String, Object> value) {
            this.key = key;
            this.owner = owner;
            this.kind = kind;
            this.value = value;
        }
    }
}

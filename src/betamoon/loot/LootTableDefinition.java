package betamoon.loot;

import betamoon.assets.AssetKey;
import betamoon.assets.model.ModelJson;
import betamoon.loot.LootStackDefinition.LootStack;
import betamoon.worldgen.SeedMixer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Immutable deterministic loot table compiled from JSON-compatible data. */
public final class LootTableDefinition {
    public static final int MAX_NESTING = 8;
    public static final int MAX_EMITTED_STACKS = 128;

    private final List<Pool> pools;
    private final String semanticHash;

    LootTableDefinition(List<Pool> pools) {
        this.pools = Collections.unmodifiableList(new ArrayList<Pool>(pools));
        StringBuilder semantics = new StringBuilder("betamoon_loot_table");
        for (Pool pool : pools) {
            semantics.append('|').append(pool.semantics());
        }
        semanticHash = sha256(semantics.toString());
    }

    public static LootTableDefinition read(byte[] bytes) throws IOException {
        return LootTableReader.document(ModelJson.read(bytes), "loot_table");
    }

    public static LootTableDefinition read(Map<String, Object> document) throws IOException {
        return LootTableReader.document(document, "loot_table");
    }

    public static LootTableDefinition readInline(Object pools, String path) throws IOException {
        return LootTableReader.inline(pools, path);
    }

    public String semanticHash() {
        return semanticHash;
    }

    public List<LootStack> generate(long seed) {
        List<LootStack> output = new ArrayList<LootStack>();
        generate(seed, output, new LinkedHashSet<AssetKey>(), 0);
        return Collections.unmodifiableList(output);
    }

    void validateReferences(Set<AssetKey> visiting, int depth) throws IOException {
        if (depth > MAX_NESTING) {
            throw new IOException("loot table nesting exceeds " + MAX_NESTING);
        }
        for (Pool pool : pools) {
            for (Entry entry : pool.entries) {
                if (entry.table == null) {
                    continue;
                }
                LootTableDefinition nested = LootTableRegistry.find(entry.table);
                if (nested == null) {
                    throw new IOException("unknown nested loot table " + entry.table);
                }
                if (!visiting.add(entry.table)) {
                    throw new IOException("loot table reference cycle at " + entry.table);
                }
                nested.validateReferences(visiting, depth + 1);
                visiting.remove(entry.table);
            }
        }
    }

    void validateEmissionBudget(Set<AssetKey> visiting, int depth) throws IOException {
        long maximum = maximumEmittedStacks(visiting, depth);
        if (maximum > MAX_EMITTED_STACKS) {
            throw new IOException("loot table can emit up to " + maximum + " stacks; maximum is "
                    + MAX_EMITTED_STACKS);
        }
    }

    /** Validates references and worst-case output for an inline table. */
    public void validate() throws IOException {
        validateReferences(new LinkedHashSet<AssetKey>(), 0);
        validateEmissionBudget(new LinkedHashSet<AssetKey>(), 0);
    }

    public String dependencySignature() {
        StringBuilder result = new StringBuilder(semanticHash);
        appendDependencies(result, new LinkedHashSet<AssetKey>(), 0);
        return sha256(result.toString());
    }

    private void appendDependencies(StringBuilder output, Set<AssetKey> visiting, int depth) {
        if (depth > MAX_NESTING) {
            throw new IllegalStateException("loot table nesting exceeds " + MAX_NESTING);
        }
        for (Pool pool : pools) {
            for (Entry entry : pool.entries) {
                if (entry.table == null) {
                    continue;
                }
                LootTableDefinition nested = LootTableRegistry.find(entry.table);
                if (nested == null) {
                    throw new IllegalStateException("unknown nested loot table " + entry.table);
                }
                if (!visiting.add(entry.table)) {
                    throw new IllegalStateException("loot table reference cycle at " + entry.table);
                }
                output.append('|').append(entry.table).append('=').append(nested.semanticHash);
                nested.appendDependencies(output, visiting, depth + 1);
                visiting.remove(entry.table);
            }
        }
    }

    private void generate(long seed, List<LootStack> output, Set<AssetKey> visiting, int depth) {
        if (depth > MAX_NESTING) {
            throw new IllegalStateException("loot table nesting exceeds " + MAX_NESTING);
        }
        for (Pool pool : pools) {
            long poolSeed = SeedMixer.derive(seed, SeedMixer.hash("pool:" + pool.key));
            int rolls = pool.rolls.sample(poolSeed, "rolls");
            for (int roll = 0; roll < rolls; roll++) {
                long rollSeed = SeedMixer.derive(poolSeed, roll + 1L);
                Entry selected = pool.select(rollSeed);
                if (selected.stack != null) {
                    if (output.size() >= MAX_EMITTED_STACKS) {
                        throw new IllegalStateException("loot table emitted more than " + MAX_EMITTED_STACKS
                                + " stacks");
                    }
                    output.add(selected.stack.generate(rollSeed));
                } else if (selected.table != null) {
                    LootTableDefinition nested = LootTableRegistry.find(selected.table);
                    if (nested == null) {
                        throw new IllegalStateException("unknown nested loot table " + selected.table);
                    }
                    if (!visiting.add(selected.table)) {
                        throw new IllegalStateException("loot table reference cycle at " + selected.table);
                    }
                    nested.generate(SeedMixer.derive(rollSeed, SeedMixer.hash(selected.table.toString())), output,
                            visiting, depth + 1);
                    visiting.remove(selected.table);
                }
            }
        }
    }

    private long maximumEmittedStacks(Set<AssetKey> visiting, int depth) throws IOException {
        if (depth > MAX_NESTING) {
            throw new IOException("loot table nesting exceeds " + MAX_NESTING);
        }
        long result = 0L;
        for (Pool pool : pools) {
            long maximumEntry = 0L;
            for (Entry entry : pool.entries) {
                long emitted = entry.stack == null ? 0L : 1L;
                if (entry.table != null) {
                    LootTableDefinition nested = LootTableRegistry.find(entry.table);
                    if (nested == null) {
                        throw new IOException("unknown nested loot table " + entry.table);
                    }
                    if (!visiting.add(entry.table)) {
                        throw new IOException("loot table reference cycle at " + entry.table);
                    }
                    emitted = nested.maximumEmittedStacks(visiting, depth + 1);
                    visiting.remove(entry.table);
                }
                maximumEntry = Math.max(maximumEntry, emitted);
            }
            result += (long) pool.rolls.maximum * maximumEntry;
            if (result > MAX_EMITTED_STACKS) {
                return result;
            }
        }
        return result;
    }

    static final class Pool {
        private final String key;
        private final LootStackDefinition.IntRange rolls;
        private final List<Entry> entries;
        private final int totalWeight;

        Pool(String key, LootStackDefinition.IntRange rolls, List<Entry> entries, int totalWeight) {
            this.key = key;
            this.rolls = rolls;
            this.entries = Collections.unmodifiableList(new ArrayList<Entry>(entries));
            this.totalWeight = totalWeight;
        }

        private Entry select(long seed) {
            int selected = new Random(SeedMixer.derive(seed, SeedMixer.hash("entry"))).nextInt(totalWeight) + 1;
            for (Entry entry : entries) {
                if (selected <= entry.cumulativeWeight) {
                    return entry;
                }
            }
            return entries.get(entries.size() - 1);
        }

        private String semantics() {
            StringBuilder result = new StringBuilder(key).append(':').append(rolls.semantics());
            for (Entry entry : entries) {
                result.append(':').append(entry.semantics());
            }
            return result.toString();
        }
    }

    static final class Entry {
        private final int cumulativeWeight;
        private final LootStackDefinition stack;
        private final AssetKey table;

        private Entry(int cumulativeWeight, LootStackDefinition stack, AssetKey table) {
            this.cumulativeWeight = cumulativeWeight;
            this.stack = stack;
            this.table = table;
        }

        static Entry item(int cumulativeWeight, LootStackDefinition stack) {
            return new Entry(cumulativeWeight, stack, null);
        }

        static Entry empty(int cumulativeWeight) {
            return new Entry(cumulativeWeight, null, null);
        }

        static Entry table(int cumulativeWeight, AssetKey table) {
            return new Entry(cumulativeWeight, null, table);
        }

        private String semantics() {
            return cumulativeWeight + ":" + (stack == null ? "" : stack.semantics()) + ":"
                    + (table == null ? "" : table.toString());
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte part : digest) {
                result.append(String.format(java.util.Locale.ROOT, "%02x", part & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}

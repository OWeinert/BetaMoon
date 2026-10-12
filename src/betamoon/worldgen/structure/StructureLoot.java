package betamoon.worldgen.structure;

import betamoon.assets.AssetKey;
import betamoon.assets.model.ModelJson;
import betamoon.loot.InventoryLootOperation;
import betamoon.loot.InventoryLootOperation.ExistingPolicy;
import betamoon.loot.InventoryLootOperation.FixedStack;
import betamoon.loot.InventoryLootOperation.OverflowPolicy;
import betamoon.loot.InventoryLootOperation.SlotMode;
import betamoon.loot.LootStackDefinition;
import betamoon.loot.LootStackDefinition.LootStack;
import betamoon.loot.LootTableDefinition;
import betamoon.loot.LootTableRegistry;
import betamoon.worldgen.BlockPosition;
import betamoon.worldgen.SeedMixer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Typed structure loot element, independent of generic marker behavior. */
public final class StructureLoot {
    final BlockPosition position;
    final String key;
    private final List<FixedStack> fixed;
    private final LootTableDefinition inline;
    private final AssetKey table;
    private final SlotMode slots;
    private final List<Integer> explicitSlots;
    private final ExistingPolicy existing;
    private final OverflowPolicy overflow;

    private StructureLoot(BlockPosition position, String key, List<FixedStack> fixed,
            LootTableDefinition inline, AssetKey table, SlotMode slots, List<Integer> explicitSlots,
            ExistingPolicy existing, OverflowPolicy overflow) {
        this.position = position;
        this.key = key;
        this.fixed = Collections.unmodifiableList(new ArrayList<FixedStack>(fixed));
        this.inline = inline;
        this.table = table;
        this.slots = slots;
        this.explicitSlots = Collections.unmodifiableList(new ArrayList<Integer>(explicitSlots));
        this.existing = existing;
        this.overflow = overflow;
    }

    static StructureLoot read(Map<String, Object> value, BlockPosition position, String effectiveKey, String path)
            throws IOException {
        Object itemsValue = value.get("items");
        Object poolsValue = value.get("pools");
        Object tableValue = value.get("table");
        int sources = (itemsValue == null ? 0 : 1) + (poolsValue == null ? 0 : 1) + (tableValue == null ? 0 : 1);
        if (sources != 1) {
            throw new IOException(path + ": expected exactly one of items, pools, or table");
        }
        ExistingPolicy existing = existing(value.get("existing"), path + ".existing");
        if (itemsValue != null) {
            if (value.get("slots") != null || value.get("overflow") != null) {
                throw new IOException(path + ": fixed items do not accept slots or overflow");
            }
            if (value.get("key") != null) {
                throw new IOException(path + ": fixed items do not use a random key");
            }
            return new StructureLoot(position, null, fixed(itemsValue, path + ".items"), null, null,
                    SlotMode.EXPLICIT, Collections.<Integer>emptyList(), existing, OverflowPolicy.REJECT);
        }
        if (effectiveKey == null) {
            throw new IOException(path + ".key: randomized loot requires a stable key");
        }
        SlotSelection selection = slots(value.get("slots"), path + ".slots");
        OverflowPolicy overflow = overflow(value.get("overflow"), path + ".overflow");
        LootTableDefinition inline = poolsValue == null ? null
                : LootTableDefinition.readInline(poolsValue, path + ".pools");
        AssetKey table = tableValue == null ? null : assetKey(tableValue, path + ".table");
        return new StructureLoot(position, effectiveKey, Collections.<FixedStack>emptyList(), inline, table,
                selection.mode, selection.slots, existing, overflow);
    }

    InventoryLootOperation plan(long seed) {
        if (!fixed.isEmpty()) {
            return InventoryLootOperation.fixed(fixed, existing);
        }
        LootTableDefinition definition = inline == null ? LootTableRegistry.find(table) : inline;
        if (definition == null) {
            throw new IllegalStateException("Unknown loot table " + table);
        }
        String identity = inline == null ? "table:" + table : "inline:" + inline.semanticHash();
        long tableSeed = SeedMixer.derive(seed, SeedMixer.hash(identity));
        List<LootStack> generated = definition.generate(tableSeed);
        return InventoryLootOperation.random(generated, slots, explicitSlots, existing, overflow,
                SeedMixer.derive(seed, SeedMixer.hash("slots")));
    }

    String semantics() {
        StringBuilder result = new StringBuilder();
        result.append(position.x).append(',').append(position.y).append(',').append(position.z).append(':');
        result.append(key == null ? "fixed" : key).append(':').append(existing).append(':').append(slots)
                .append(':').append(explicitSlots).append(':').append(overflow);
        for (FixedStack entry : fixed) {
            result.append(":f:").append(entry.slot).append(':').append(entry.stack.itemId).append(':')
                    .append(entry.stack.count).append(':').append(entry.stack.damage);
        }
        if (inline != null) {
            result.append(":inline:").append(inline.semanticHash());
        }
        if (table != null) {
            result.append(":table:").append(table);
        }
        return result.toString();
    }

    String dependencySignature() {
        if (inline != null) {
            return inline.dependencySignature();
        }
        return table == null ? "fixed" : LootTableRegistry.dependencySignature(table);
    }

    void validateReferences() throws IOException {
        if (inline != null) {
            inline.validate();
        } else if (table != null) {
            LootTableDefinition definition = LootTableRegistry.find(table);
            if (definition == null) {
                throw new IOException("unknown loot table " + table);
            }
            definition.validate();
        }
    }

    private static List<FixedStack> fixed(Object input, String path) throws IOException {
        List<Object> values = ModelJson.array(input, path);
        if (values.isEmpty() || values.size() > LootTableDefinition.MAX_EMITTED_STACKS) {
            throw new IOException(path + ": expected 1.." + LootTableDefinition.MAX_EMITTED_STACKS + " entries");
        }
        List<FixedStack> result = new ArrayList<FixedStack>();
        Set<Integer> slots = new LinkedHashSet<Integer>();
        for (int index = 0; index < values.size(); index++) {
            String entryPath = path + "[" + index + "]";
            Map<String, Object> value = ModelJson.object(values.get(index), entryPath);
            fields(value, entryPath, "slot", "stack");
            int slot = integer(value.get("slot"), entryPath + ".slot", 0, 255);
            if (!slots.add(Integer.valueOf(slot))) {
                throw new IOException(entryPath + ".slot: duplicate fixed slot " + slot);
            }
            LootStackDefinition stack = LootStackDefinition.read(value.get("stack"), entryPath + ".stack", false);
            result.add(new FixedStack(slot, stack.exact()));
        }
        return result;
    }

    private static SlotSelection slots(Object input, String path) throws IOException {
        if (input == null || "random_empty".equals(input)) {
            return new SlotSelection(SlotMode.RANDOM_EMPTY, Collections.<Integer>emptyList());
        }
        if ("ordered_empty".equals(input)) {
            return new SlotSelection(SlotMode.ORDERED_EMPTY, Collections.<Integer>emptyList());
        }
        if (!(input instanceof List)) {
            throw new IOException(path + ": expected random_empty, ordered_empty, or an array of slots");
        }
        List<?> values = (List<?>) input;
        if (values.isEmpty() || values.size() > 256) {
            throw new IOException(path + ": expected 1..256 explicit slots");
        }
        Set<Integer> unique = new LinkedHashSet<Integer>();
        for (int index = 0; index < values.size(); index++) {
            int slot = integer(values.get(index), path + "[" + index + "]", 0, 255);
            if (!unique.add(Integer.valueOf(slot))) {
                throw new IOException(path + "[" + index + "]: duplicate slot " + slot);
            }
        }
        return new SlotSelection(SlotMode.EXPLICIT, new ArrayList<Integer>(unique));
    }

    private static ExistingPolicy existing(Object input, String path) throws IOException {
        String value = input == null ? "require_empty" : ModelJson.name(input, path);
        if (value.equals("require_empty")) {
            return ExistingPolicy.REQUIRE_EMPTY;
        }
        if (value.equals("preserve")) {
            return ExistingPolicy.PRESERVE;
        }
        if (value.equals("replace")) {
            return ExistingPolicy.REPLACE;
        }
        throw new IOException(path + ": expected require_empty, preserve, or replace");
    }

    private static OverflowPolicy overflow(Object input, String path) throws IOException {
        String value = input == null ? "discard" : ModelJson.name(input, path);
        if (value.equals("discard")) {
            return OverflowPolicy.DISCARD;
        }
        if (value.equals("reject")) {
            return OverflowPolicy.REJECT;
        }
        throw new IOException(path + ": expected discard or reject");
    }

    private static AssetKey assetKey(Object input, String path) throws IOException {
        try {
            return AssetKey.parse(ModelJson.name(input, path));
        } catch (IllegalArgumentException error) {
            throw new IOException(path + ": " + error.getMessage());
        }
    }

    private static int integer(Object input, String path, int minimum, int maximum) throws IOException {
        double value = ModelJson.number(input, path);
        if (value != Math.rint(value) || value < minimum || value > maximum) {
            throw new IOException(path + ": expected integer from " + minimum + " to " + maximum);
        }
        return (int) value;
    }

    private static void fields(Map<String, Object> value, String path, String... supported) throws IOException {
        ModelJson.fields(value, path, supported);
    }

    private static final class SlotSelection {
        private final SlotMode mode;
        private final List<Integer> slots;

        private SlotSelection(SlotMode mode, List<Integer> slots) {
            this.mode = mode;
            this.slots = slots;
        }
    }
}

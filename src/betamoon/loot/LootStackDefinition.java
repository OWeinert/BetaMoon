package betamoon.loot;

import java.io.IOException;
import java.util.Map;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;

/** Immutable item-stack template shared by fixed and randomized loot. */
public final class LootStackDefinition {
    private final int itemId;
    private final IntRange count;
    private final IntRange damage;

    LootStackDefinition(int itemId, IntRange count, IntRange damage) {
        this.itemId = itemId;
        this.count = count;
        this.damage = damage;
    }

    public static LootStackDefinition read(Object input, String path, boolean ranges) throws IOException {
        if (!(input instanceof Map)) {
            throw new IOException(path + ": expected item-stack object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> value = (Map<String, Object>) input;
        LootTableReader.fields(value, path, "item", "count", "damage");
        int item = resolveItem(value.get("item"), path + ".item");
        IntRange count = LootTableReader.range(value.get("count"), path + ".count", 1,
                Item.itemsList[item].getItemStackLimit(), 1, ranges);
        IntRange damage = LootTableReader.range(value.get("damage"), path + ".damage", 0, 32767, 0, ranges);
        return new LootStackDefinition(item, count, damage);
    }

    LootStack generate(long seed) {
        return new LootStack(itemId, count.sample(seed, "count"), damage.sample(seed, "damage"));
    }

    public LootStack exact() {
        return new LootStack(itemId, count.minimum, damage.minimum);
    }

    String semantics() {
        return itemId + ":" + count.semantics() + ":" + damage.semantics();
    }

    private static int resolveItem(Object input, String path) throws IOException {
        if (input instanceof Number) {
            int id = LootTableReader.integer(input, path, 1, Item.itemsList.length - 1);
            if (Item.itemsList[id] == null) {
                throw new IOException(path + ": item ID is not registered: " + id);
            }
            return id;
        }
        if (!(input instanceof String) || ((String) input).trim().isEmpty()) {
            throw new IOException(path + ": expected a supported item name or numeric ID");
        }
        String requested = ((String) input).trim();
        int separator = requested.indexOf(':');
        String itemPath = separator >= 0 ? requested.substring(separator + 1) : requested;
        String shortPath = stripContentType(itemPath);
        String normalized = normalize(shortPath);
        for (int id = 1; id < Item.itemsList.length; id++) {
            Item item = Item.itemsList[id];
            if (item == null) {
                continue;
            }
            String name = item.getItemName();
            String bare = name != null && (name.startsWith("item.") || name.startsWith("tile."))
                    ? name.substring(5) : name;
            String shortBare = stripContentType(bare);
            if (requested.equals(name) || itemPath.equals(name) || itemPath.equals(bare)
                    || shortPath.equals(name) || shortPath.equals(bare) || shortPath.equals(shortBare)
                    || normalized.equals(normalize(shortBare))) {
                return id;
            }
        }
        throw new IOException(path + ": item is not registered: " + requested);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = Character.toLowerCase(value.charAt(index));
            if (Character.isLetterOrDigit(character)) {
                result.append(character);
            }
        }
        return result.toString();
    }

    private static String stripContentType(String value) {
        if (value == null) {
            return null;
        }
        return value.startsWith("item/") ? value.substring("item/".length())
                : value.startsWith("block/") ? value.substring("block/".length()) : value;
    }

    static final class IntRange {
        final int minimum;
        final int maximum;

        IntRange(int minimum, int maximum) {
            this.minimum = minimum;
            this.maximum = maximum;
        }

        int sample(long seed, String phase) {
            if (minimum == maximum) {
                return minimum;
            }
            long mixed = betamoon.worldgen.SeedMixer.derive(seed,
                    betamoon.worldgen.SeedMixer.hash(phase));
            java.util.Random random = new java.util.Random(mixed);
            return minimum + random.nextInt(maximum - minimum + 1);
        }

        String semantics() {
            return minimum + ".." + maximum;
        }
    }

    /** Concrete immutable stack result created during structure planning. */
    public static final class LootStack {
        public final int itemId;
        public final int count;
        public final int damage;

        public LootStack(int itemId, int count, int damage) {
            this.itemId = itemId;
            this.count = count;
            this.damage = damage;
        }

        public ItemStack create() {
            return new ItemStack(itemId, count, damage);
        }
    }
}

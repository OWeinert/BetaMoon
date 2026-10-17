package betamoon.gui;

import betamoon.worldgen.creation.GenerationCatalog;
import betamoon.worldgen.creation.GenerationOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Presentation hierarchy; generation dependencies remain owned by the saved
 * catalog.
 */
final class GenerationSettingsTree {
    static final class Entry {
        final String key;
        final String label;
        final GenerationOption option;
        final List<Entry> children = new ArrayList<Entry>();

        Entry(String key, String label, GenerationOption option) {
            this.key = key;
            this.label = label;
            this.option = option;
        }
    }

    static final class Row {
        final Entry entry;
        final int depth;
        final boolean open;

        Row(Entry entry, int depth, boolean open) {
            this.entry = entry;
            this.depth = depth;
            this.open = open;
        }
    }

    private final List<Entry> roots = new ArrayList<Entry>();
    private final Set<String> expanded = new LinkedHashSet<String>();

    GenerationSettingsTree(GenerationCatalog catalog) {
        Map<String, Entry> entries = new LinkedHashMap<String, Entry>();
        Map<String, Entry> categories = new LinkedHashMap<String, Entry>();
        for (GenerationOption option : catalog.options.values()) {
            entries.put(option.key, new Entry(option.key, option.label, option));
        }
        for (Entry entry : entries.values()) {
            Entry parent = null;
            // Choose one visible parent; all other dependency parents still govern
            // effective state.
            for (String key : entry.option.parents) {
                if (parent == null || key.startsWith("group/")) {
                    parent = entries.get(key);
                }
            }
            if (parent != null) {
                parent.children.add(entry);
            } else {
                String category = entry.option.category;
                Entry group = categories.get(category);
                if (group == null) {
                    group = new Entry("category/" + category, category, null);
                    categories.put(category, group);
                    roots.add(group);
                    expanded.add(group.key);
                }
                group.children.add(entry);
            }
        }
    }

    void toggle(Entry entry) {
        if (!entry.children.isEmpty() && !expanded.remove(entry.key)) {
            expanded.add(entry.key);
        }
    }

    List<Row> rows(String query) {
        List<Row> result = new ArrayList<Row>();
        String normalized = query.toLowerCase(Locale.ROOT).trim();
        for (Entry entry : roots) {
            append(result, entry, 0, normalized, false);
        }
        return result;
    }

    private boolean matches(Entry entry, String query) {
        if ((entry.label + " " + entry.key).toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        for (Entry child : entry.children) {
            if (matches(child, query)) {
                return true;
            }
        }
        return false;
    }

    private void append(List<Row> result, Entry entry, int depth, String query, boolean parentMatched) {
        boolean ownMatch = parentMatched || (entry.label + " " + entry.key).toLowerCase(Locale.ROOT).contains(query);
        if (!ownMatch && !matches(entry, query)) {
            return;
        }
        boolean open = expanded.contains(entry.key) || !query.isEmpty();
        result.add(new Row(entry, depth, open));
        if (open) {
            for (Entry child : entry.children) {
                append(result, child, depth + 1, query, ownMatch);
            }
        }
    }
}

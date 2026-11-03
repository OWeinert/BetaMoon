package betamoon.gui.menu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Deterministic bounded contribution ordering; rejects cycles before native UI
 * changes.
 */
public final class GuiMenuOrder {
    private GuiMenuOrder() {
    }

    public static List<GuiMenuDefinition> order(Map<String, GuiMenuDefinition> definitions, String target,
            String region, boolean requireAnchors) {
        Map<String, GuiMenuDefinition> group = new LinkedHashMap<>();
        for (GuiMenuDefinition definition : definitions.values()) {
            if (definition.target.equals(target) && definition.region.equals(region)) {
                group.put(definition.key, definition);
            }
        }
        Map<String, Set<String>> edges = new HashMap<>();
        Map<String, Integer> incoming = new HashMap<>();
        for (String key : group.keySet()) {
            edges.put(key, new LinkedHashSet<>());
            incoming.put(key, 0);
        }
        for (GuiMenuDefinition definition : group.values()) {
            for (String key : definition.before) {
                edge(definitions, group, edges, incoming, definition, key, true, requireAnchors);
            }
            for (String key : definition.after) {
                edge(definitions, group, edges, incoming, definition, key, false, requireAnchors);
            }
        }
        PriorityQueue<String> ready = new PriorityQueue<>();
        for (String key : incoming.keySet()) {
            if (incoming.get(key) == 0) {
                ready.add(key);
            }
        }
        List<GuiMenuDefinition> result = new ArrayList<>();
        while (!ready.isEmpty()) {
            String key = ready.remove();
            result.add(group.get(key));
            for (String next : edges.get(key)) {
                int count = incoming.get(next) - 1;
                incoming.put(next, count);
                if (count == 0) {
                    ready.add(next);
                }
            }
        }
        if (result.size() != group.size()) {
            throw new IllegalArgumentException("Menu ordering contains a cycle");
        }
        return Collections.unmodifiableList(result);
    }

    private static void edge(Map<String, GuiMenuDefinition> definitions, Map<String, GuiMenuDefinition> group,
            Map<String, Set<String>> edges, Map<String, Integer> incoming, GuiMenuDefinition owner, String key,
            boolean before, boolean required) {
        if (!definitions.containsKey(key)) {
            if (required) {
                throw new IllegalArgumentException("Unknown menu ordering anchor: " + key);
            }
            return;
        }
        if (!group.containsKey(key)) {
            throw new IllegalArgumentException("Menu ordering anchors must share target and region: " + key);
        }
        String from = before ? owner.key : key;
        String to = before ? key : owner.key;
        if (edges.get(from).add(to)) {
            incoming.put(to, incoming.get(to) + 1);
        }
    }
}

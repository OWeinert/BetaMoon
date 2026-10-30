package betamoon.gui.style;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Finite style inheritance and precompiled interaction states; no per-paint map
 * resolution.
 */
public final class GuiStyleSheet {
    public static final int HOVERED = 1;
    public static final int PRESSED = 2;
    public static final int FOCUSED = 4;
    public static final int SELECTED = 8;
    public static final int INVALID = 16;
    public static final int DISABLED = 32;
    public static final GuiStyleSheet MENU = preset(true, false);
    public static final GuiStyleSheet MINIMAL = preset(false, false);
    public static final GuiStyleSheet CONTAINER_LEGACY = preset(true, true);

    public static final class Rule {
        public final String parent;
        public final GuiControlStyle values;
        public final Map<Integer, GuiControlStyle> states;

        public Rule(String parent, GuiControlStyle values, Map<Integer, GuiControlStyle> states) {
            if (values == null || states == null) {
                throw new IllegalArgumentException("Style rule values and states are required");
            }
            this.parent = parent;
            this.values = values;
            Map<Integer, GuiControlStyle> copy = new LinkedHashMap<>();
            for (Map.Entry<Integer, GuiControlStyle> entry : states.entrySet()) {
                if (entry.getKey() == null || entry.getKey() <= 0 || entry.getKey() >= 64 || entry.getValue() == null
                        || entry.getValue().affectsMeasurement()) {
                    throw new IllegalArgumentException("Invalid interaction style selector");
                }
                copy.put(entry.getKey(), entry.getValue());
            }
            this.states = Collections.unmodifiableMap(copy);
        }
    }

    private final Map<String, GuiControlStyle[]> compiled;
    private final GuiStyleSheet inherited;
    private final GuiControlStyle inline;
    private final Map<String, GuiControlStyle[]> inlineVariants = new LinkedHashMap<String, GuiControlStyle[]>(4, 0.75F,
            true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, GuiControlStyle[]> entry) {
            return size() > 4;
        }
    };

    public GuiStyleSheet(Map<String, Rule> rules) {
        if (rules == null || rules.isEmpty() || rules.size() > 128) {
            throw new IllegalArgumentException("Style sheets require 1..128 named variants");
        }
        Map<String, GuiControlStyle[]> result = new LinkedHashMap<>();
        for (String name : rules.keySet()) {
            if (name == null || !name.matches("[A-Za-z][A-Za-z0-9_.-]{0,127}")) {
                throw new IllegalArgumentException("Invalid style variant name");
            }
            compile(name, rules, result, new LinkedHashSet<String>());
        }
        compiled = Collections.unmodifiableMap(result);
        inherited = null;
        inline = null;
    }

    public GuiControlStyle resolve(String variant, int states) {
        if (states < 0 || states >= 64) {
            throw new IllegalArgumentException("Invalid style state: " + states);
        }
        GuiControlStyle[] values;
        if (inherited == null) {
            values = compiled.get(variant);
        } else {
            values = inlineVariants.get(variant);
            if (values == null) {
                values = new GuiControlStyle[64];
                for (int state = 0; state < 64; state++) {
                    values[state] = inherited.resolve(variant, state).merge(inline);
                }
                inlineVariants.put(variant, values);
            }
        }
        if (values == null) {
            throw new IllegalArgumentException("Unknown style variant or state: " + variant);
        }
        return values[states];
    }

    private static GuiControlStyle[] compile(String key, Map<String, Rule> rules,
            Map<String, GuiControlStyle[]> compiled, Set<String> visiting) {
        if (compiled.containsKey(key)) {
            return compiled.get(key);
        }
        Rule rule = rules.get(key);
        if (rule == null || !visiting.add(key) || visiting.size() > 32) {
            throw new IllegalArgumentException("Missing or cyclic style variant: " + key);
        }
        GuiControlStyle[] parent = rule.parent == null ? null : compile(rule.parent, rules, compiled, visiting);
        GuiControlStyle[] values = states(rule, parent);
        visiting.remove(key);
        compiled.put(key, values);
        return values;
    }

    private GuiStyleSheet(Map<String, GuiControlStyle[]> compiled, boolean prepared) {
        this.compiled = Collections.unmodifiableMap(new LinkedHashMap<>(compiled));
        inherited = null;
        inline = null;
    }

    private GuiStyleSheet(GuiStyleSheet inherited, GuiControlStyle inline) {
        compiled = inherited.compiled;
        this.inherited = inherited;
        this.inline = inline;
    }

    public GuiStyleSheet withInline(GuiControlStyle values) {
        return new GuiStyleSheet(this, values);
    }

    /**
     * Overlay named variants on a prepared preset, preserving its finite Java state
     * resolution.
     */
    public GuiStyleSheet overlay(Map<String, Rule> rules) {
        Map<String, GuiControlStyle[]> overrides = new LinkedHashMap<>();
        for (String name : rules.keySet()) {
            if (name == null || !name.matches("[A-Za-z][A-Za-z0-9_.-]{0,127}")) {
                throw new IllegalArgumentException("Invalid style variant name");
            }
            compileOverlay(name, rules, overrides, new LinkedHashSet<>());
        }
        Map<String, GuiControlStyle[]> combined = new LinkedHashMap<>(compiled);
        combined.putAll(overrides);
        if (combined.size() > 128) {
            throw new IllegalArgumentException("Style sheet exceeds 128 variants");
        }
        return new GuiStyleSheet(combined, true);
    }

    private GuiControlStyle[] compileOverlay(String name, Map<String, Rule> rules,
            Map<String, GuiControlStyle[]> output, Set<String> visiting) {
        if (output.containsKey(name)) {
            return output.get(name);
        }
        Rule rule = rules.get(name);
        if (rule == null || !visiting.add(name) || visiting.size() > 32) {
            throw new IllegalArgumentException("Missing/cyclic style variant: " + name);
        }
        GuiControlStyle[] parent;
        if (rule.parent == null) {
            parent = compiled.getOrDefault(name, compiled.get("default"));
        } else {
            parent = rules.containsKey(rule.parent)
                    ? compileOverlay(rule.parent, rules, output, visiting)
                    : compiled.get(rule.parent);
        }
        if (parent == null) {
            throw new IllegalArgumentException("Unknown parent style variant: " + rule.parent);
        }
        GuiControlStyle[] values = states(rule, parent);
        visiting.remove(name);
        output.put(name, values);
        return values;
    }

    private static GuiControlStyle[] states(Rule rule, GuiControlStyle[] parent) {
        GuiControlStyle[] values = new GuiControlStyle[64];
        for (int state = 0; state < 64; state++) {
            GuiControlStyle value = parent == null ? rule.values : parent[state].merge(rule.values);
            int effective = (state & DISABLED) != 0 ? state & ~(HOVERED | PRESSED) : state;
            for (int bit : new int[]{HOVERED, PRESSED, SELECTED, FOCUSED, INVALID, DISABLED}) {
                GuiControlStyle override = rule.states.get(bit);
                if ((effective & bit) != 0 && override != null) {
                    value = value.merge(override);
                }
            }
            for (Map.Entry<Integer, GuiControlStyle> entry : rule.states.entrySet()) {
                int selector = entry.getKey();
                if (Integer.bitCount(selector) > 1 && (effective & selector) == selector) {
                    value = value.merge(entry.getValue());
                }
            }
            values[state] = value;
            value.requireActive();
        }
        return values;
    }

    private static GuiStyleSheet preset(boolean nativeButton, boolean container) {
        GuiControlStyle base = GuiControlStyle.of("background", nativeButton ? 0xFF3A3A3A : 0xE5162030, "foreground",
                0xFFE0E0E0, "border", 0xFF697889, "accent", 0xFF55AAFF, "focus", 0xFFFFFFA0, "selection", 0x8855AAFF,
                "padding", container ? 4 : 6, "borderWidth", 1, "shadow", nativeButton, "nativeButton", nativeButton,
                "font", GuiFontFace.NATIVE);
        Map<Integer, GuiControlStyle> states = new LinkedHashMap<>();
        states.put(HOVERED,
                nativeButton
                        ? GuiControlStyle.of("background", 0xFF304C68, "foreground", 0xFFFFFFA0)
                        : GuiControlStyle.of("background", 0xFF304C68));
        states.put(PRESSED, GuiControlStyle.of("background", 0xFF132536));
        states.put(DISABLED,
                GuiControlStyle.of("foreground", nativeButton ? 0xFFA0A0A0 : 0xFF777777, "background", 0xFF20252B));
        return new GuiStyleSheet(Collections.singletonMap("default", new Rule(null, base, states)));
    }
}

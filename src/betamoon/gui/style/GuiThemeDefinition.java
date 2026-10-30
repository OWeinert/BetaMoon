package betamoon.gui.style;

import betamoon.gui.component.GuiComponentDefinition;
import betamoon.gui.component.GuiComponentLimits;
import betamoon.luaapi.client.ui.GuiPaintCompiler;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.luaj.vm2.LuaValue;

/**
 * Reusable appearance metadata and explicit prepared-template builders,
 * independent from live resources.
 */
public final class GuiThemeDefinition {
    public static final class Rule {
        public final String parent;
        public final GuiStyleValues values;
        public final Map<Integer, GuiStyleValues> states;
        public Rule(String parent, GuiStyleValues values, Map<Integer, GuiStyleValues> states) {
            this.parent = parent;
            this.values = values;
            Map<Integer, GuiStyleValues> copy = new LinkedHashMap<>();
            for (Map.Entry<Integer, GuiStyleValues> state : states.entrySet()) {
                if (state.getKey() <= 0 || state.getKey() >= 64 || state.getValue().affectsMeasurement()) {
                    throw new IllegalArgumentException("Interaction styles cannot change font/padding metrics");
                }
                copy.put(state.getKey(), state.getValue());
            }
            this.states = Collections.unmodifiableMap(copy);
        }
    }
    public static final class Template {
        public final String owner;
        public final LuaValue paint;
        public final Set<String> parts;
        public Template(String owner, LuaValue paint, Set<String> parts) {
            if (owner == null || paint == null || !paint.isfunction() || parts == null || parts.size() > 32) {
                throw new IllegalArgumentException(
                        "Template requires an owner, paint builder and at most 32 named parts");
            }
            for (String part : parts) {
                GuiComponentDefinition.name(part);
            }
            this.owner = owner;
            this.paint = paint;
            this.parts = Collections.unmodifiableSet(new LinkedHashSet<>(parts));
        }
    }
    public final String key;
    public final String owner;
    public final String base;
    public final Map<String, Rule> variants;
    public final Map<String, Template> templates;
    public final long metadataBytes;
    private final ScriptResourceTracker.OwnerGeneration ownerState;
    private final long generation;

    public GuiThemeDefinition(String key, String owner, String base, Map<String, Rule> variants,
            Map<String, Template> templates) {
        this.key = GuiComponentDefinition.key(key);
        this.owner = owner;
        this.base = base;
        if (owner == null || variants.size() > 128 || templates.size() > 64) {
            throw new IllegalArgumentException("Theme declaration budget exceeded");
        }
        preset(base);
        this.variants = Collections.unmodifiableMap(new LinkedHashMap<>(variants));
        this.templates = Collections.unmodifiableMap(new LinkedHashMap<>(templates));
        GuiComponentLimits.Values budget = new GuiComponentLimits.Values(1024 * 1024, 16384);
        for (Map.Entry<String, Rule> entry : variants.entrySet()) {
            budget.text(entry.getKey());
            budget.add(entry.getValue().values.values);
            for (GuiStyleValues state : entry.getValue().states.values()) {
                budget.add(state.values);
            }
        }
        for (Map.Entry<String, Template> entry : templates.entrySet()) {
            budget.text(entry.getKey());
            for (String part : entry.getValue().parts) {
                budget.text(part);
            }
        }
        metadataBytes = budget.estimatedBytes() + variants.size() * 128L + templates.size() * 256L;
        ownerState = ScriptResourceTracker.ownerToken(owner);
        generation = ownerState.current();
        prepare(new GuiPaintCompiler.Resources() {
            public GuiFontFace font(String key) {
                return GuiFontFace.NATIVE;
            }

            public GuiDrawable image(String key) {
                return GuiDrawable.solid(0);
            }
        });
    }

    public GuiStyleSheet prepare(GuiPaintCompiler.Resources resources) {
        requireActive();
        Map<String, GuiStyleSheet.Rule> rules = new LinkedHashMap<>();
        for (Map.Entry<String, Rule> entry : variants.entrySet()) {
            Map<Integer, GuiControlStyle> states = new LinkedHashMap<>();
            for (Map.Entry<Integer, GuiStyleValues> state : entry.getValue().states.entrySet()) {
                states.put(state.getKey(), state.getValue().prepare(resources));
            }
            Rule rule = entry.getValue();
            rules.put(entry.getKey(), new GuiStyleSheet.Rule(rule.parent, rule.values.prepare(resources), states));
        }
        return preset(base).overlay(rules);
    }

    public boolean available() {
        return ownerState.matches(generation);
    }

    public void requireActive() {
        if (!available()) {
            throw new IllegalStateException("Theme owner expired: " + key);
        }
    }

    public static GuiStyleSheet preset(String key) {
        switch (key) {
            case "menu":
                return GuiStyleSheet.MENU;
            case "minimal":
                return GuiStyleSheet.MINIMAL;
            case "container_legacy":
                return GuiStyleSheet.CONTAINER_LEGACY;
            default:
                throw new IllegalArgumentException("Unknown built-in theme: " + key);
        }
    }
}

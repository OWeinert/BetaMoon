package betamoon.gui.component;

import betamoon.content.ContentKey;
import betamoon.content.ContentType;
import betamoon.gui.presentation.GuiCapability;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.luaj.vm2.LuaValue;

/**
 * Immutable registered author component. Definitions own contracts; instances
 * own state and retained views.
 */
public final class GuiComponentDefinition {
    public static final class SizeHint {
        public final int width;
        public final int height;
        public final int minimumWidth;
        public final int minimumHeight;
        public SizeHint(int width, int height, int minimumWidth, int minimumHeight) {
            if (width < 0 || height < 0 || minimumWidth < 0 || minimumHeight < 0 || width > 16384 || height > 16384
                    || minimumWidth > 16384 || minimumHeight > 16384) {
                throw new IllegalArgumentException("Component size hints require dimensions within 0..16384");
            }
            this.width = width;
            this.height = height;
            this.minimumWidth = minimumWidth;
            this.minimumHeight = minimumHeight;
        }
    }

    public static final class Slot {
        public final int minimum;
        public final int maximum;
        public Slot(int minimum, int maximum) {
            if (minimum < 0 || maximum < minimum || maximum > 256) {
                throw new IllegalArgumentException("Content slots require bounds within 0..256");
            }
            this.minimum = minimum;
            this.maximum = maximum;
        }
    }

    public final String key;
    public final String owner;
    public final GuiComponentSchema props;
    public final GuiComponentSchema state;
    public final Map<String, GuiComponentSchema> events;
    public final Map<String, Slot> slots;
    public final Map<String, String> exports;
    public final Set<GuiCapability> requirements;
    public final Set<String> dependencies;
    public final LuaValue build;
    public final LuaValue paint;
    public final LuaValue rawDraw;
    public final LuaValue hit;
    public final LuaValue input;
    public final boolean focusable;
    public final String label;
    public final SizeHint size;
    public final long metadataBytes;
    private final ScriptResourceTracker.OwnerGeneration ownerState;
    private final long generation;

    public GuiComponentDefinition(String key, String owner, GuiComponentSchema props, GuiComponentSchema state,
            Map<String, GuiComponentSchema> events, Map<String, Slot> slots, Map<String, String> exports,
            Set<GuiCapability> requirements, Set<String> dependencies, LuaValue build, LuaValue paint, LuaValue rawDraw,
            LuaValue hit, LuaValue input, boolean focusable, String label, SizeHint size) {
        this.key = key(key);
        if (owner == null || props == null || state == null || events == null || slots == null || exports == null
                || exports.size() > 64 || requirements == null || dependencies == null || events.size() > 64
                || slots.size() > 32 || dependencies.size() > 128 || build == null || paint == null || rawDraw == null
                || hit == null || input == null
                || (build.isnil() ? 0 : 1) + (paint.isnil() ? 0 : 1) + (rawDraw.isnil() ? 0 : 1) != 1
                || !build.isnil() && !build.isfunction() || !paint.isnil() && !paint.isfunction()
                || !rawDraw.isnil() && !rawDraw.isfunction() || !hit.isnil() && !hit.isfunction()
                || !input.isnil() && !input.isfunction()
                || !build.isnil() && (!hit.isnil() || !input.isnil() || focusable) || build.isnil() && !slots.isEmpty()
                || !paint.isnil() && !hit.isnil() || label == null || label.length() > 1024 || size == null) {
            throw new IllegalArgumentException(
                    this.key + ": invalid component contracts or exclusive build/paint/rawDraw choice");
        }
        this.owner = owner;
        this.props = props;
        this.state = state;
        for (String name : events.keySet()) {
            name(name);
        }
        for (String name : slots.keySet()) {
            name(name);
        }
        this.events = Collections.unmodifiableMap(new LinkedHashMap<>(events));
        this.slots = Collections.unmodifiableMap(new LinkedHashMap<>(slots));
        for (Map.Entry<String, String> export : exports.entrySet()) {
            name(export.getKey());
            if (export.getValue() == null || export.getValue().length() > 255) {
                throw new IllegalArgumentException("Invalid component export target");
            }
        }
        this.exports = Collections.unmodifiableMap(new LinkedHashMap<>(exports));
        this.requirements = Collections.unmodifiableSet(new LinkedHashSet<>(requirements));
        Set<String> canonical = new LinkedHashSet<>();
        for (String dependency : dependencies) {
            canonical.add(key(dependency));
        }
        this.dependencies = Collections.unmodifiableSet(canonical);
        this.build = build;
        this.paint = paint;
        this.rawDraw = rawDraw;
        this.hit = hit;
        this.input = input;
        this.focusable = focusable;
        this.label = label;
        this.size = size;
        long bytes = props.metadataBytes + state.metadataBytes + (events.size() + slots.size() + exports.size()) * 128L;
        for (GuiComponentSchema event : events.values()) {
            if (event == null) {
                throw new IllegalArgumentException("Component event schema is required");
            }
            bytes += event.metadataBytes;
        }
        if (bytes > 2 * 1024 * 1024) {
            throw new IllegalArgumentException("Component metadata exceeds 2 MiB");
        }
        metadataBytes = bytes;
        ownerState = ScriptResourceTracker.ownerToken(owner);
        generation = ownerState.current();
    }

    public void requireActive() {
        if (!ownerState.matches(generation)) {
            throw new IllegalStateException(key + ": component owner generation has expired");
        }
    }

    public static String key(String value) {
        if (value == null || value.length() > 255) {
            throw new IllegalArgumentException("Component key requires namespace:path");
        }
        int colon = value.indexOf(':');
        if (colon < 1 || colon != value.lastIndexOf(':')) {
            throw new IllegalArgumentException("Component key requires namespace:path");
        }
        ContentKey checked = ContentKey.of(value.substring(0, colon), ContentType.of("ui_component"),
                value.substring(colon + 1));
        return checked.namespace() + ":" + checked.name();
    }

    public static String name(String value) {
        if (value == null || !value.matches("[A-Za-z][A-Za-z0-9_]{0,127}")) {
            throw new IllegalArgumentException("Component field/slot/event names require a stable identifier");
        }
        return value;
    }
}

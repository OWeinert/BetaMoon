package betamoon.gui.hud;

import betamoon.gui.component.GuiComponentDefinition;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Owned HUD metadata; declarations acquire neither rendering nor cursor
 * authority.
 */
public final class GuiHudDefinition {
    public enum Layer {
        AUTO, WORLD, OVERLAY
    }
    public enum Context {
        GAMEPLAY("gameplay"), CHAT("chat"), INVENTORY("inventory"), PAUSE("pause"), MENU("menu");

        public final String name;

        Context(String name) {
            this.name = name;
        }

        public static Context named(String value) {
            for (Context context : values()) {
                if (context.name.equals(value)) {
                    return context;
                }
            }
            throw new IllegalArgumentException("Unknown HUD visibility context: " + value);
        }
    }

    public static final class NativePart {
        public final boolean hide;
        public final String replace;

        public NativePart(boolean hide, String replace) {
            if (hide == (replace != null)) {
                throw new IllegalArgumentException("Select native hiding or an owned replacement screen");
            }
            this.hide = hide;
            this.replace = replace == null ? null : GuiComponentDefinition.key(replace);
        }
    }

    public final String owner;
    public final String key;
    public final String screen;
    public final int priority;
    public final Set<Context> contexts;
    public final boolean debug;
    public final boolean hideGui;
    public final boolean safeArea;
    public final boolean interactive;
    public final Layer layer;
    public final boolean behindMenus;
    public final Set<String> regions;
    public final Map<GuiHudNativePart, NativePart> nativeParts;
    private final ScriptResourceTracker.OwnerGeneration generation;
    private final long token;

    public GuiHudDefinition(String owner, String key, String screen, int priority, Set<Context> contexts, boolean debug,
            boolean hideGui, boolean safeArea, boolean interactive, Set<String> regions,
            Map<GuiHudNativePart, NativePart> nativeParts) {
        this(owner, key, screen, priority, contexts, debug, hideGui, safeArea, interactive, regions, nativeParts,
                Layer.AUTO, true);
    }

    public GuiHudDefinition(String owner, String key, String screen, int priority, Set<Context> contexts, boolean debug,
            boolean hideGui, boolean safeArea, boolean interactive, Set<String> regions,
            Map<GuiHudNativePart, NativePart> nativeParts, Layer layer, boolean behindMenus) {
        this.owner = Objects.requireNonNull(owner, "HUD owner");
        this.key = GuiComponentDefinition.key(key);
        this.screen = screen == null ? null : GuiComponentDefinition.key(screen);
        if (priority < -10000 || priority > 10000 || contexts.isEmpty() || contexts.contains(null)
                || regions.size() > 32 || nativeParts.containsKey(null) || nativeParts.containsValue(null)
                || screen == null && nativeParts.isEmpty() || interactive && (screen == null || regions.isEmpty())
                || !interactive && !regions.isEmpty()) {
            throw new IllegalArgumentException("Invalid HUD content, priority, visibility or interactive regions");
        }
        this.priority = priority;
        this.contexts = Collections.unmodifiableSet(new LinkedHashSet<>(contexts));
        this.debug = debug;
        this.hideGui = hideGui;
        this.safeArea = safeArea;
        this.interactive = interactive;
        this.layer = Objects.requireNonNull(layer, "HUD layer");
        this.behindMenus = behindMenus;
        Set<String> ids = new LinkedHashSet<>();
        for (String id : regions) {
            if (id == null || id.trim().isEmpty() || id.length() > 160) {
                throw new IllegalArgumentException(
                        "HUD interaction regions require bounded local/exported element IDs");
            }
            ids.add(id);
        }
        this.regions = Collections.unmodifiableSet(ids);
        Map<GuiHudNativePart, NativePart> parts = new EnumMap<>(GuiHudNativePart.class);
        parts.putAll(nativeParts);
        this.nativeParts = Collections.unmodifiableMap(parts);
        generation = ScriptResourceTracker.ownerToken(owner);
        token = generation.current();
    }

    public boolean available() {
        return generation.matches(token);
    }
}

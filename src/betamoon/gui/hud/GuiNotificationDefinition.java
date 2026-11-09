package betamoon.gui.hud;

import betamoon.gui.component.GuiComponentDefinition;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Owned screen template and dimensions, with optional declared interaction
 * regions.
 */
public final class GuiNotificationDefinition {
    public final String owner;
    public final String key;
    public final String screen;
    public final int width;
    public final int height;
    public final GuiNotificationCorner corner;
    public final boolean interactive;
    public final Set<String> regions;
    private final ScriptResourceTracker.OwnerGeneration generation;
    private final long token;

    public GuiNotificationDefinition(String owner, String key, String screen, int width, int height,
            GuiNotificationCorner corner, boolean interactive, Set<String> regions) {
        this.owner = Objects.requireNonNull(owner, "Notification owner");
        this.key = GuiComponentDefinition.key(key);
        this.screen = GuiComponentDefinition.key(screen);
        this.corner = Objects.requireNonNull(corner, "Notification corner");
        if (width < 80 || width > 512 || height < 20 || height > 256) {
            throw new IllegalArgumentException("Notification dimensions require width 80..512 and height 20..256");
        }
        // Reuse the shared overlay region/visibility contract without acquiring a host.
        GuiHudDefinition checked = new GuiHudDefinition(owner, key, screen, 10000,
                EnumSet.allOf(GuiHudDefinition.Context.class), true, true, false, interactive, regions,
                Collections.emptyMap());
        this.width = width;
        this.height = height;
        this.interactive = interactive;
        this.regions = Collections.unmodifiableSet(new LinkedHashSet<>(checked.regions));
        generation = ScriptResourceTracker.ownerToken(owner);
        token = generation.current();
    }

    public boolean available() {
        return generation.matches(token);
    }

    GuiHudDefinition contribution(String key) {
        return new GuiHudDefinition(owner, key, screen, 10000, EnumSet.allOf(GuiHudDefinition.Context.class), true,
                true, false, interactive, regions, Collections.emptyMap());
    }
}

package betamoon.gui.inventory;

import betamoon.gui.component.GuiComponentDefinition;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Owned presentation metadata; contains no live container, item, slot or player
 * reference.
 */
public final class GuiInventoryProfile {
    public static final class Position {
        public final int x;
        public final int y;

        public Position(int x, int y) {
            if (x < 1 || y < 1 || x > 4096 || y > 4096) {
                throw new IllegalArgumentException("Native slot positions require integers in 1..4096");
            }
            this.x = x;
            this.y = y;
        }
    }

    public final String owner;
    public final String key;
    public final String screen;
    public final int priority;
    public final int panelWidth;
    public final int panelHeight;
    public final Map<String, Position> groups;
    public final Map<String, Position> positions;
    public final String background;
    public final String backgroundImage;
    public final boolean slotFrames;
    public final boolean craftingLabel;
    public final Rect preview;
    private final ScriptResourceTracker.OwnerGeneration generation;
    private final long token;

    public GuiInventoryProfile(String owner, String key, String screen, int priority, int panelWidth, int panelHeight,
            Map<String, Position> groups, Map<String, Position> positions, String background, String backgroundImage,
            boolean slotFrames, boolean craftingLabel, Rect preview) {
        this.owner = Objects.requireNonNull(owner, "inventory profile owner");
        this.key = GuiComponentDefinition.key(key);
        this.screen = screen == null ? null : GuiComponentDefinition.key(screen);
        if (priority < -10000 || priority > 10000 || !extent(panelWidth) || !extent(panelHeight)
                || !Arrays.asList("native", "panel", "none").contains(background)
                || !GuiInventorySchema.GROUPS.keySet().containsAll(groups.keySet())
                || !GuiInventorySchema.BY_ID.keySet().containsAll(positions.keySet())
                || backgroundImage != null && screen == null) {
            throw new IllegalArgumentException("Invalid inventory priority, panel, background or semantic slots");
        }
        this.priority = priority;
        this.panelWidth = panelWidth;
        this.panelHeight = panelHeight;
        this.groups = copy(groups);
        this.positions = copy(positions);
        this.background = background;
        this.backgroundImage = backgroundImage == null ? null : GuiComponentDefinition.name(backgroundImage);
        this.slotFrames = slotFrames;
        this.craftingLabel = craftingLabel;
        this.preview = Objects.requireNonNull(preview, "inventory preview rectangle");
        if (preview.getLeft() < 0 || preview.getTop() < 0 || preview.getRight() > 4096 || preview.getBottom() > 4096
                || preview.getWidth() > 256 || preview.getHeight() > 256) {
            throw new IllegalArgumentException("Inventory preview requires a nonnegative rectangle within 256x256");
        }
        generation = ScriptResourceTracker.ownerToken(owner);
        token = generation.current();
    }

    private static boolean extent(int value) {
        return value == 0 || value >= 36 && value <= 4096;
    }

    private static Map<String, Position> copy(Map<String, Position> values) {
        Map<String, Position> result = new LinkedHashMap<>();
        for (Map.Entry<String, Position> value : values.entrySet()) {
            result.put(value.getKey(), Objects.requireNonNull(value.getValue(), "inventory slot position"));
        }
        return Collections.unmodifiableMap(result);
    }

    public boolean available() {
        return generation.matches(token);
    }
}

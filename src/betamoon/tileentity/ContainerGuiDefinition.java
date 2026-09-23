package betamoon.tileentity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.src.ItemStack;

/**
 * Immutable, declarative visual definition for a Lua-backed container screen.
 */
public final class ContainerGuiDefinition {
    public final String name;
    public final String owner;
    public final ContainerDefinition container;
    public final int width;
    public final int height;
    public final Label title;
    public final Label playerInventoryLabel;
    public final Background background;
    public final boolean pauseGame;
    public final List<Element> elements;

    public ContainerGuiDefinition(String name, String owner, ContainerDefinition container, int width, int height,
            Label title, Label playerInventoryLabel, Background background, boolean pauseGame, List<Element> elements) {
        this.name = name;
        this.owner = owner;
        this.container = container;
        this.width = width;
        this.height = height;
        this.title = title;
        this.playerInventoryLabel = playerInventoryLabel;
        this.background = background;
        this.pauseGame = pauseGame;
        this.elements = Collections.unmodifiableList(new ArrayList<>(elements));
    }

    /** Text drawn in the foreground layer. */
    public static final class Label {
        public final String text;
        public final String align;
        public final int x;
        public final int y;
        public final int width;
        public final int color;
        public final boolean shadow;
        public Label(String text, int x, int y, int width, int color, String align, boolean shadow) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.width = width;
            this.color = color;
            this.align = align;
            this.shadow = shadow;
        }
    }

    /** Main background and optional automatically generated slot frames. */
    public static final class Background {
        public final Texture texture;
        public final String style;
        public final boolean drawSlotFrames;
        public final int chestRows;
        public Background(Texture texture, String style, boolean drawSlotFrames, int chestRows) {
            this.texture = texture;
            this.style = style;
            this.drawSlotFrames = drawSlotFrames;
            this.chestRows = chestRows;
        }
    }

    /**
     * A texture and its source region. Atlas details are created internally for
     * built-in sprites.
     */
    public static final class Texture {
        public final String resource;
        public final int u;
        public final int v;
        public final int width;
        public final int height;
        public final int textureWidth;
        public final int textureHeight;
        public final int border;
        public Texture(String resource, int u, int v, int width, int height, int textureWidth, int textureHeight) {
            this(resource, u, v, width, height, textureWidth, textureHeight, 0);
        }
        public Texture(String resource, int u, int v, int width, int height, int textureWidth, int textureHeight,
                int border) {
            this.resource = resource;
            this.u = u;
            this.v = v;
            this.width = width;
            this.height = height;
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
            this.border = border;
        }
    }

    /** Base for every prevalidated GUI element. */
    public abstract static class Element {
        public final int x;
        public final int y;
        public final int layer;
        public final GuiAnchor anchor;
        public final Condition visibleWhen;
        public final List<String> tooltip;
        protected Element(int x, int y, int layer, GuiAnchor anchor, Condition visibleWhen, List<String> tooltip) {
            this.x = x;
            this.y = y;
            this.layer = layer;
            this.anchor = anchor;
            this.visibleWhen = visibleWhen;
            this.tooltip = tooltip == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(tooltip));
        }
    }

    public static final class ImageElement extends Element {
        public final Texture image;
        public final int width;
        public final int height;
        public ImageElement(int x, int y, int layer, GuiAnchor anchor, Condition condition, List<String> tooltip,
                Texture image, int width, int height) {
            super(x, y, layer, anchor, condition, tooltip);
            this.image = image;
            this.width = width;
            this.height = height;
        }
    }

    public static final class TextElement extends Element {
        public final String text;
        public final String field;
        public final String format;
        public final String align;
        public final int width;
        public final int color;
        public final boolean shadow;
        public TextElement(int x, int y, int layer, GuiAnchor anchor, Condition condition, List<String> tooltip,
                String text, String field, String format, String align, int width, int color, boolean shadow) {
            super(x, y, layer, anchor, condition, tooltip);
            this.text = text;
            this.field = field;
            this.format = format;
            this.align = align;
            this.width = width;
            this.color = color;
            this.shadow = shadow;
        }
    }

    public static final class ProgressElement extends Element {
        public final String field;
        public final String maximumField;
        public final ProgressDirection direction;
        public final int maximum;
        public final int minimumPixels;
        public final Texture image;
        public final Texture background;
        public final boolean hideWhenEmpty;
        public ProgressElement(int x, int y, int layer, GuiAnchor anchor, Condition condition, List<String> tooltip,
                String field, String maximumField, int maximum, ProgressDirection direction, int minimumPixels,
                boolean hideWhenEmpty, Texture image, Texture background) {
            super(x, y, layer, anchor, condition, tooltip);
            this.field = field;
            this.maximumField = maximumField;
            this.maximum = maximum;
            this.direction = direction;
            this.minimumPixels = minimumPixels;
            this.hideWhenEmpty = hideWhenEmpty;
            this.image = image;
            this.background = background;
        }
    }

    public static final class StateImageElement extends Element {
        public final String field;
        public final Map<String, Texture> states;
        public final Texture defaultImage;
        public StateImageElement(int x, int y, int layer, GuiAnchor anchor, Condition condition, List<String> tooltip,
                String field, Map<String, Texture> states, Texture defaultImage) {
            super(x, y, layer, anchor, condition, tooltip);
            this.field = field;
            this.states = Collections.unmodifiableMap(new LinkedHashMap<>(states));
            this.defaultImage = defaultImage;
        }
    }

    public static final class RectangleElement extends Element {
        public final int width;
        public final int height;
        public final int color;
        public RectangleElement(int x, int y, int layer, GuiAnchor anchor, Condition condition, List<String> tooltip,
                int width, int height, int color) {
            super(x, y, layer, anchor, condition, tooltip);
            this.width = width;
            this.height = height;
            this.color = color;
        }
    }

    public static final class TooltipElement extends Element {
        public final int width;
        public final int height;
        public TooltipElement(int x, int y, int layer, GuiAnchor anchor, Condition condition, List<String> tooltip,
                int width, int height) {
            super(x, y, layer, anchor, condition, tooltip);
            this.width = width;
            this.height = height;
        }
    }

    public static final class ItemElement extends Element {
        public final String slot;
        private final ItemStack item;
        public final boolean showCount;
        public ItemElement(int x, int y, int layer, GuiAnchor anchor, Condition condition, List<String> tooltip,
                String slot, ItemStack item, boolean showCount) {
            super(x, y, layer, anchor, condition, tooltip);
            this.slot = slot;
            this.item = item == null ? null : item.copy();
            this.showCount = showCount;
        }

        /** Returns a display stack that cannot mutate the compiled definition. */
        public ItemStack getItem() {
            return item == null ? null : item.copy();
        }
    }

    /** Interactive view bound to one container-owned control. */
    public static final class ControlElement extends Element {
        public final String control;
        public final ContainerControlDefinition.Type type;
        public final int width;
        public final int height;
        public final String text;
        public final String orientation;
        public final boolean focusable;
        public final boolean captureDrag;
        public final Map<String, Texture> visuals;

        public ControlElement(int x, int y, int layer, GuiAnchor anchor, Condition condition, List<String> tooltip,
                String control, ContainerControlDefinition.Type type, int width, int height, String text,
                String orientation, boolean focusable, boolean captureDrag, Map<String, Texture> visuals) {
            super(x, y, layer, anchor, condition, tooltip);
            this.control = control;
            this.type = type;
            this.width = width;
            this.height = height;
            this.text = text;
            this.orientation = orientation;
            this.focusable = focusable;
            this.captureDrag = captureDrag;
            this.visuals = Collections.unmodifiableMap(new LinkedHashMap<String, Texture>(visuals));
        }
    }

    /** Recursive comparison tree used by visibleWhen. */
    public static final class Condition {
        public enum Source {
            DATA,
            SESSION
        }

        public final Source source;
        public final String field;
        public final GuiConditionOperator operator;
        public final Object expected;
        public final List<Condition> children;
        public Condition(String field, GuiConditionOperator operator, Object expected, List<Condition> children) {
            this(Source.DATA, field, operator, expected, children);
        }
        public Condition(Source source, String field, GuiConditionOperator operator, Object expected,
                List<Condition> children) {
            this.source = source;
            this.field = field;
            this.operator = operator;
            this.expected = expected;
            this.children = children == null ? null : Collections.unmodifiableList(new ArrayList<>(children));
        }
    }
}

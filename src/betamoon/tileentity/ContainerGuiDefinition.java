package betamoon.tileentity;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.src.ItemStack;

/** Immutable, declarative visual definition for a Lua-backed container screen. */
public final class ContainerGuiDefinition {
    public final String name, owner;
    public final ContainerDefinition container;
    public final int width, height;
    public final Label title, playerInventoryLabel;
    public final Background background;
    public final boolean pauseGame;
    public final List elements = new ArrayList();

    public ContainerGuiDefinition(String name, String owner, ContainerDefinition container, int width, int height,
        Label title, Label playerInventoryLabel, Background background, boolean pauseGame, List elements) {
        this.name = name; this.owner = owner; this.container = container; this.width = width; this.height = height;
        this.title = title; this.playerInventoryLabel = playerInventoryLabel; this.background = background;
        this.pauseGame = pauseGame; this.elements.addAll(elements);
    }

    /** Text drawn in the foreground layer. */
    public static final class Label {
        public final String text, align;
        public final int x, y, width, color;
        public final boolean shadow;
        public Label(String text, int x, int y, int width, int color, String align, boolean shadow) {
            this.text = text; this.x = x; this.y = y; this.width = width; this.color = color;
            this.align = align; this.shadow = shadow;
        }
    }

    /** Main background and optional automatically generated slot frames. */
    public static final class Background {
        public final Texture texture;
        public final String style;
        public final boolean drawSlotFrames;
        public final int chestRows;
        public Background(Texture texture, String style, boolean drawSlotFrames, int chestRows) {
            this.texture = texture; this.style = style; this.drawSlotFrames = drawSlotFrames; this.chestRows = chestRows;
        }
    }

    /** A texture and its source region. Atlas details are created internally for built-in sprites. */
    public static final class Texture {
        public final String resource;
        public final int u, v, width, height, textureWidth, textureHeight;
        public Texture(String resource, int u, int v, int width, int height, int textureWidth, int textureHeight) {
            this.resource = resource; this.u = u; this.v = v; this.width = width; this.height = height;
            this.textureWidth = textureWidth; this.textureHeight = textureHeight;
        }
    }

    /** Base for every prevalidated GUI element. */
    public abstract static class Element {
        public final int x, y, layer;
        public final String anchor;
        public final Condition visibleWhen;
        public final List tooltip;
        protected Element(int x, int y, int layer, String anchor, Condition visibleWhen, List tooltip) {
            this.x = x; this.y = y; this.layer = layer; this.anchor = anchor;
            this.visibleWhen = visibleWhen; this.tooltip = tooltip;
        }
    }

    public static final class ImageElement extends Element {
        public final Texture image; public final int width, height;
        public ImageElement(int x, int y, int layer, String anchor, Condition condition, List tooltip,
            Texture image, int width, int height) {
            super(x, y, layer, anchor, condition, tooltip); this.image = image; this.width = width; this.height = height;
        }
    }

    public static final class TextElement extends Element {
        public final String text, field, format, align; public final int width, color; public final boolean shadow;
        public TextElement(int x, int y, int layer, String anchor, Condition condition, List tooltip,
            String text, String field, String format, String align, int width, int color, boolean shadow) {
            super(x, y, layer, anchor, condition, tooltip); this.text = text; this.field = field;
            this.format = format; this.align = align; this.width = width; this.color = color; this.shadow = shadow;
        }
    }

    public static final class ProgressElement extends Element {
        public final String field, maximumField, direction; public final int maximum, minimumPixels;
        public final Texture image, background; public final boolean hideWhenEmpty;
        public ProgressElement(int x, int y, int layer, String anchor, Condition condition, List tooltip,
            String field, String maximumField, int maximum, String direction, int minimumPixels,
            boolean hideWhenEmpty, Texture image, Texture background) {
            super(x, y, layer, anchor, condition, tooltip); this.field = field; this.maximumField = maximumField;
            this.maximum = maximum; this.direction = direction; this.minimumPixels = minimumPixels;
            this.hideWhenEmpty = hideWhenEmpty; this.image = image; this.background = background;
        }
    }

    public static final class StateImageElement extends Element {
        public final String field; public final java.util.Map states; public final Texture defaultImage;
        public StateImageElement(int x, int y, int layer, String anchor, Condition condition, List tooltip,
            String field, java.util.Map states, Texture defaultImage) {
            super(x, y, layer, anchor, condition, tooltip); this.field = field;
            this.states = states; this.defaultImage = defaultImage;
        }
    }

    public static final class RectangleElement extends Element {
        public final int width, height, color;
        public RectangleElement(int x, int y, int layer, String anchor, Condition condition, List tooltip,
            int width, int height, int color) {
            super(x, y, layer, anchor, condition, tooltip); this.width = width; this.height = height; this.color = color;
        }
    }

    public static final class TooltipElement extends Element {
        public final int width, height;
        public TooltipElement(int x, int y, int layer, String anchor, Condition condition, List tooltip,
            int width, int height) {
            super(x, y, layer, anchor, condition, tooltip); this.width = width; this.height = height;
        }
    }

    public static final class ItemElement extends Element {
        public final String slot; public final ItemStack item; public final boolean showCount;
        public ItemElement(int x, int y, int layer, String anchor, Condition condition, List tooltip,
            String slot, ItemStack item, boolean showCount) {
            super(x, y, layer, anchor, condition, tooltip); this.slot = slot; this.item = item; this.showCount = showCount;
        }
    }

    /** Recursive comparison tree used by visibleWhen. */
    public static final class Condition {
        public final String field, operator; public final Object expected; public final List children;
        public Condition(String field, String operator, Object expected, List children) {
            this.field = field; this.operator = operator; this.expected = expected; this.children = children;
        }
    }
}

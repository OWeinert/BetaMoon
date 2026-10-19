package betamoon.tileentity;

import betamoon.gui.presentation.GuiGeometry;
import betamoon.gui.presentation.GuiGeometry.Bounds;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/** Shared final-layout validation and displayed geometry; independent of Lua callback dispatch. */
public final class ContainerGuiLayout {
    private final ContainerGuiDefinition definition;
    private final List<ContainerGuiNode> nodes;
    private final ToIntFunction<String> textWidth;
    private final Bounds viewport;

    public ContainerGuiLayout(ContainerGuiDefinition definition, List<ContainerGuiNode> nodes,
            ToIntFunction<String> textWidth, Bounds viewport) {
        this.definition = definition;
        this.nodes = nodes;
        this.textWidth = textWidth;
        this.viewport = viewport;
    }

    public static void validateDefinition(ContainerGuiDefinition definition) {
        List<ContainerGuiNode> nodes = new ArrayList<>();
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> state = new LinkedHashMap<>();
        for (TileEntityDefinition.Field field : definition.container.tileEntity.fields.values()) {
            data.put(field.name, field.defaultValue);
        }
        for (ContainerDefinition.SessionField field : definition.container.session.values()) {
            state.put(field.name, field.defaultValue);
        }
        for (ContainerGuiNodeDefinition nodeDefinition : definition.presentation.nodes) {
            ContainerGuiNode node = new ContainerGuiNode(nodeDefinition);
            ContainerGuiNativeBaseline.resolve(node, definition.container, data::get, state::get);
            node.targets.putAll(node.baseline);
            node.displayed.putAll(node.baseline);
            nodes.add(node);
        }
        new ContainerGuiLayout(definition, nodes, text -> 0, new Bounds(-1e9, -1e9, 2e9, 2e9))
                .validate(Collections.emptyMap());
    }

    public List<GuiGeometry> geometries(Map<ContainerGuiNode, Map<String, Object>> candidates, boolean target) {
        List<GuiGeometry> result = new ArrayList<>(nodes.size());
        for (ContainerGuiNode node : nodes) {
            Map<String, Object> values = candidates.getOrDefault(node, target ? node.targets : node.displayed);
            GuiGeometry parent = node.definition.parent < 0
                    ? new GuiGeometry(0, 0, 1, 1, 0, 0, 1, 0xFFFFFF, true, viewport)
                    : result.get(node.definition.parent);
            double w = dimension(node, values, "width");
            double h = dimension(node, values, "height");
            double x = number(values, "x", 0);
            double y = number(values, "y", 0);
            if (node.definition.element != null) {
                GuiAnchor anchor = node.definition.element.anchor;
                x = anchor.positionX(0, (int) w, definition.width) + x;
                y = anchor.positionY(0, (int) h, definition.height) + y;
            }
            double sx = number(values, "scaleX", 1);
            double sy = number(values, "scaleY", 1);
            x += number(values, "translateX", 0) + w * number(values, "originX", 0) * (1 - sx);
            y += number(values, "translateY", 0) + h * number(values, "originY", 0) * (1 - sy);
            x = parent.x + parent.scaleX * x;
            y = parent.y + parent.scaleY * y;
            sx *= parent.scaleX;
            sy *= parent.scaleY;
            Bounds clip = parent.clip;
            if (Boolean.TRUE.equals(values.get("clip"))) {
                clip = clip.intersect(new Bounds(x, y, w * sx, h * sy));
            }
            boolean visible = parent.visible && !Boolean.FALSE.equals(values.get("visible"))
                    && !Boolean.FALSE.equals(values.get("__conditionVisible"));
            double opacity = parent.opacity * number(values, "opacity", 1);
            int tint = GuiGeometry.multiply(parent.tint, (int) number(values, "tint", 0xFFFFFF));
            if (!(Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(sx) && Double.isFinite(sy)
                    && Double.isFinite(w * sx) && Double.isFinite(h * sy))
                    || Math.abs(x) > Float.MAX_VALUE / 4 || Math.abs(y) > Float.MAX_VALUE / 4
                    || sx > Float.MAX_VALUE / 4 || sy > Float.MAX_VALUE / 4
                    || w * sx > Float.MAX_VALUE / 4 || h * sy > Float.MAX_VALUE / 4) {
                throw new ContainerGuiProperties.ValueError("Non-finite transformed geometry for " + label(node));
            }
            result.add(new GuiGeometry(x, y, sx, sy, w, h, opacity, tint, visible, clip));
        }
        return result;
    }

    private double dimension(ContainerGuiNode node, Map<String, Object> values, String property) {
        double value = number(values, property, "item".equals(node.definition.type) ? 16 : 0);
        if ("text".equals(node.definition.type)) {
            if ("height".equals(property)) {
                value = 8;
            } else if (value == 0) {
                value = textWidth.applyAsInt(String.valueOf(values.getOrDefault("text", "")));
            }
        }
        if ("slot".equals(node.definition.type)) {
            value += 2;
        }
        return Math.max(0, Math.round(value));
    }

    public void validate(Map<ContainerGuiNode, Map<String, Object>> candidates) {
        // Final-layout validity must not depend on whether today's window clips a bad target.
        Bounds logicalSpace = new Bounds(-Float.MAX_VALUE, -Float.MAX_VALUE,
                Float.MAX_VALUE * 2.0, Float.MAX_VALUE * 2.0);
        List<GuiGeometry> geometry = new ContainerGuiLayout(definition, nodes, textWidth, logicalSpace)
                .geometries(candidates, true);
        Area coverage = new Area(new Rectangle2D.Double(0, 0, definition.width, definition.height));
        for (int i = 0; i < nodes.size(); i++) {
            ContainerGuiNode node = nodes.get(i);
            GuiGeometry bounds = geometry.get(i);
            if (node.definition.element instanceof ContainerGuiDefinition.PanelElement && bounds.visible) {
                coverage.add(area(bounds.bounds().intersect(bounds.clip)));
            }
            if ("item".equals(node.definition.type) && bounds.opacity != 1) {
                throw new ContainerGuiProperties.ValueError(
                        "Item-containing groups cannot fade without an item alpha adapter.");
            }
        }
        for (int i = 0; i < nodes.size(); i++) {
            ContainerGuiNode node = nodes.get(i);
            GuiGeometry bounds = geometry.get(i);
            if (node.definition.element == null || !bounds.available()
                    || node.definition.element instanceof ContainerGuiDefinition.PanelElement) {
                continue;
            }
            Area uncovered = area(bounds.bounds().intersect(bounds.clip));
            uncovered.subtract(coverage);
            boolean intrinsicText = "text".equals(node.definition.type)
                    && number(candidates.getOrDefault(node, node.targets), "width", 0) == 0;
            if (!intrinsicText && !uncovered.isEmpty()) {
                throw new ContainerGuiProperties.ValueError("Final GUI bounds lack panel coverage: " + label(node));
            }
            if (node.definition.element instanceof ContainerGuiDefinition.ControlElement) {
                for (int j = 0; j < i; j++) {
                    ContainerGuiNode other = nodes.get(j);
                    GuiGeometry otherBounds = geometry.get(j);
                    if (other.definition.element instanceof ContainerGuiDefinition.ControlElement
                            && other.definition.element.layer == node.definition.element.layer && otherBounds.available()) {
                        Area overlap = area(bounds.bounds().intersect(bounds.clip));
                        overlap.intersect(area(otherBounds.bounds().intersect(otherBounds.clip)));
                        if (!overlap.isEmpty()) {
                            throw new ContainerGuiProperties.ValueError("Final controls overlap: " + label(other)
                                    + " and " + label(node));
                        }
                    }
                }
            }
        }
    }

    private static Area area(Bounds bounds) {
        return new Area(new Rectangle2D.Double(bounds.x, bounds.y, bounds.width, bounds.height));
    }

    private static double number(Map<String, Object> values, String name, double fallback) {
        Object value = values.get(name);
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    private static String label(ContainerGuiNode node) {
        return node.definition.id == null ? node.definition.path : node.definition.id;
    }
}

package betamoon.gui.presentation;

import betamoon.gui.presentation.GuiGeometry.Bounds;
import betamoon.gui.presentation.GuiGeometry;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Shared final-layout validation and displayed geometry; independent of Lua
 * callback dispatch.
 */
public final class GuiPresentationLayout {
    private final GuiDocumentDefinition definition;
    private final List<GuiPresentationNode> nodes;
    private final ToIntFunction<String> textWidth;
    private final Bounds viewport;

    public GuiPresentationLayout(GuiDocumentDefinition definition, List<GuiPresentationNode> nodes,
            ToIntFunction<String> textWidth, Bounds viewport) {
        this.definition = definition;
        this.nodes = nodes;
        this.textWidth = textWidth;
        this.viewport = viewport;
    }

    public static void validateDefinition(GuiDocumentDefinition definition, GuiPresentationHost host) {
        List<GuiPresentationNode> nodes = new ArrayList<>();
        GuiPresentationSources sources = host.snapshot();
        for (GuiNodeDefinition nodeDefinition : definition.presentation.nodes) {
            GuiPresentationNode node = new GuiPresentationNode(nodeDefinition);
            node.baseline.putAll(host.baseline(nodeDefinition, sources));
            node.targets.putAll(node.baseline);
            node.displayed.putAll(node.baseline);
            nodes.add(node);
        }
        new GuiPresentationLayout(definition, nodes, text -> 0, new Bounds(-1e9, -1e9, 2e9, 2e9))
                .validate(Collections.emptyMap());
    }

    public List<GuiGeometry> geometries(Map<GuiPresentationNode, Map<String, Object>> candidates, boolean target) {
        List<GuiGeometry> result = new ArrayList<>(nodes.size());
        for (GuiPresentationNode node : nodes) {
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
                    && Double.isFinite(w * sx) && Double.isFinite(h * sy)) || Math.abs(x) > Float.MAX_VALUE / 4
                    || Math.abs(y) > Float.MAX_VALUE / 4 || sx > Float.MAX_VALUE / 4 || sy > Float.MAX_VALUE / 4
                    || w * sx > Float.MAX_VALUE / 4 || h * sy > Float.MAX_VALUE / 4) {
                throw new GuiProperties.ValueError("Non-finite transformed geometry for " + label(node));
            }
            result.add(new GuiGeometry(x, y, sx, sy, w, h, opacity, tint, visible, clip));
        }
        return result;
    }

    private static double envelope(GuiPresentationNode node, Map<String, Object> values, String property,
            double fallback, boolean minimum) {
        double current = number(node.displayed, property, fallback);
        double target = number(values, property, fallback);
        GuiPresentationNode.Track track = node.tracks.get(property);
        double from = track != null && track.from instanceof Number ? ((Number) track.from).doubleValue() : current;
        return minimum ? Math.min(from, Math.min(current, target)) : Math.max(from, Math.max(current, target));
    }

    private double dimension(GuiPresentationNode node, Map<String, Object> values, String property) {
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

    public void validate(Map<GuiPresentationNode, Map<String, Object>> candidates) {
        if (definition.retained) {
            // Retained views own layout; legacy panel/slot geometry is not a second
            // authoritative tree.
            double[] scaleX = new double[nodes.size()];
            double[] scaleY = new double[nodes.size()];
            double[] offsetX = new double[nodes.size()];
            double[] offsetY = new double[nodes.size()];
            // New retained layouts bound dimensions to 16384 and node count to 4096.
            // Include sibling overflow and nested offsets without calculating a second
            // layout tree.
            double coordinateBudget = (16384D + 65536D) * (nodes.size() + 1);
            for (int index = 0; index < nodes.size(); index++) {
                GuiPresentationNode node = nodes.get(index);
                Map<String, Object> values = candidates.getOrDefault(node, node.targets);
                for (GuiPropertyDescriptor descriptor : node.definition.properties.descriptors().values()) {
                    try {
                        descriptor.normalize(values.get(descriptor.name));
                    } catch (IllegalArgumentException invalid) {
                        throw new GuiProperties.ValueError(node.definition.path + "." + invalid.getMessage());
                    }
                }
                int parent = node.definition.parent;
                double parentX = parent < 0 ? 1 : scaleX[parent];
                double parentY = parent < 0 ? 1 : scaleY[parent];
                double sx = envelope(node, values, "scaleX", 1, false);
                double sy = envelope(node, values, "scaleY", 1, false);
                double dx = Math.max(Math.abs(1 - sx), Math.abs(1 - envelope(node, values, "scaleX", 1, true)));
                double dy = Math.max(Math.abs(1 - sy), Math.abs(1 - envelope(node, values, "scaleY", 1, true)));
                double tx = Math.max(Math.abs(envelope(node, values, "translateX", 0, true)),
                        Math.abs(envelope(node, values, "translateX", 0, false)));
                double ty = Math.max(Math.abs(envelope(node, values, "translateY", 0, true)),
                        Math.abs(envelope(node, values, "translateY", 0, false)));
                scaleX[index] = parentX * sx;
                scaleY[index] = parentY * sy;
                offsetX[index] = (parent < 0 ? 0 : offsetX[parent]) + parentX * (tx + coordinateBudget * dx);
                offsetY[index] = (parent < 0 ? 0 : offsetY[parent]) + parentY * (ty + coordinateBudget * dy);
                if (scaleX[index] > 1e9 || scaleY[index] > 1e9 || offsetX[index] > 1e9 || offsetY[index] > 1e9) {
                    throw new GuiProperties.ValueError(
                            node.definition.path + ": nested transforms exceed the retained geometry budget");
                }
            }
            return;
        }
        // Final-layout validity must not depend on whether today's window clips a bad
        // target.
        Bounds logicalSpace = new Bounds(-Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE * 2.0,
                Float.MAX_VALUE * 2.0);
        List<GuiGeometry> geometry = new GuiPresentationLayout(definition, nodes, textWidth, logicalSpace)
                .geometries(candidates, true);
        Area coverage = new Area(new Rectangle2D.Double(0, 0, definition.width, definition.height));
        for (int i = 0; i < nodes.size(); i++) {
            GuiPresentationNode node = nodes.get(i);
            GuiGeometry bounds = geometry.get(i);
            if (node.definition.element instanceof GuiDocumentDefinition.PanelElement && bounds.visible) {
                coverage.add(area(bounds.bounds().intersect(bounds.clip)));
            }
            if ("item".equals(node.definition.type) && bounds.opacity != 1) {
                throw new GuiProperties.ValueError("Item-containing groups cannot fade without an item alpha adapter.");
            }
        }
        for (int i = 0; i < nodes.size(); i++) {
            GuiPresentationNode node = nodes.get(i);
            GuiGeometry bounds = geometry.get(i);
            if (node.definition.element == null || !bounds.available()
                    || node.definition.element instanceof GuiDocumentDefinition.PanelElement) {
                continue;
            }
            Area uncovered = area(bounds.bounds().intersect(bounds.clip));
            uncovered.subtract(coverage);
            boolean intrinsicText = "text".equals(node.definition.type)
                    && number(candidates.getOrDefault(node, node.targets), "width", 0) == 0;
            if (!intrinsicText && !uncovered.isEmpty()) {
                throw new GuiProperties.ValueError("Final GUI bounds lack panel coverage: " + label(node));
            }
            if (node.definition.element instanceof GuiDocumentDefinition.ControlElement) {
                for (int j = 0; j < i; j++) {
                    GuiPresentationNode other = nodes.get(j);
                    GuiGeometry otherBounds = geometry.get(j);
                    if (other.definition.element instanceof GuiDocumentDefinition.ControlElement
                            && other.definition.element.layer == node.definition.element.layer
                            && otherBounds.available()) {
                        Area overlap = area(bounds.bounds().intersect(bounds.clip));
                        overlap.intersect(area(otherBounds.bounds().intersect(otherBounds.clip)));
                        if (!overlap.isEmpty()) {
                            throw new GuiProperties.ValueError(
                                    "Final controls overlap: " + label(other) + " and " + label(node));
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

    private static String label(GuiPresentationNode node) {
        return node.definition.id == null ? node.definition.path : node.definition.id;
    }
}

package betamoon.luaapi.tileentity;

import betamoon.gui.animation.GuiTransitionPolicy;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.tileentity.ContainerDefinition;
import betamoon.tileentity.ContainerGuiDefinition;
import betamoon.tileentity.ContainerGuiNodeDefinition;
import betamoon.tileentity.ContainerGuiPresentationDefinition;
import betamoon.tileentity.ContainerGuiProperties;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/** Preserves group identity while retaining the existing flattened render/layout declarations. */
final class ContainerPresentationParser {
    private final List<ContainerGuiNodeDefinition> nodes = new ArrayList<>();
    private final Set<String> ids = new HashSet<>();
    private final List<ContainerGuiDefinition.Element> elements;
    private final ContainerDefinition container;
    private int leaf;

    private ContainerPresentationParser(List<ContainerGuiDefinition.Element> elements, ContainerDefinition container) {
        this.elements = elements;
        this.container = container;
    }

    static ContainerGuiPresentationDefinition parse(LuaValue gui, List<ContainerGuiDefinition.Element> elements,
            ContainerDefinition container) {
        ContainerPresentationParser parser = new ContainerPresentationParser(elements, container);
        if (!gui.get("elements").isnil()) {
            parser.read(gui.get("elements"), -1, "elements", 0);
        }
        LuaValue callback = gui.get("onEvent");
        if (!callback.isnil() && !callback.isfunction()) {
            throw new LuaError("GUI onEvent must be a function.");
        }
        Set<String> events = new LinkedHashSet<>();
        LuaValue selected = gui.get("events");
        if (selected.isnil()) {
            events.addAll(ContainerGuiPresentationDefinition.EVENT_TYPES);
            events.remove("animation_property");
        } else {
            if (!selected.istable() || selected.checktable().keys().length != selected.length()) {
                throw new LuaError("GUI events must be an array of event-family names.");
            }
            for (int i = 1; i <= selected.length(); i++) {
                String type = selected.get(i).checkjstring();
                if (!ContainerGuiPresentationDefinition.EVENT_TYPES.contains(type)) {
                    throw new LuaError("Unknown GUI event family: " + type);
                }
                events.add(type);
            }
        }
        for (ContainerGuiNodeDefinition node : parser.nodes) {
            if ("item".equals(node.type)) {
                int parent = node.parent;
                while (parent >= 0) {
                    ContainerGuiNodeDefinition ancestor = parser.nodes.get(parent);
                    Object opacity = ancestor.properties.baseline().get("opacity");
                    if (opacity instanceof Number && ((Number) opacity).doubleValue() != 1) {
                        throw new LuaError("Item-containing groups do not support non-unit opacity.");
                    }
                    parent = ancestor.parent;
                }
            }
        }
        String callbackOwner = LuaScriptRegistry.getCurrentScriptFile();
        return new ContainerGuiPresentationDefinition(parser.nodes, callback, events,
                callbackOwner == null ? container.owner : callbackOwner);
    }

    static void validateHierarchy(LuaValue elements) {
        validateHierarchy(elements, 0, new int[1]);
    }

    private static void validateHierarchy(LuaValue elements, int depth, int[] count) {
        if (depth > 64 || !elements.istable() || elements.checktable().keys().length != elements.length()) {
            throw new LuaError("GUI elements require an array with at most 64 nested group levels.");
        }
        for (int i = 1; i <= elements.length(); i++) {
            LuaValue value = elements.get(i);
            if (++count[0] > 4096 || !value.istable()) {
                throw new LuaError("GUI elements require tables, with at most 4096 nodes.");
            }
            if ("group".equalsIgnoreCase(value.get("type").checkjstring())) {
                validateHierarchy(value.get("elements"), depth + 1, count);
            }
        }
    }

    private void read(LuaValue definitions, int parent, String path, int depth) {
        if (depth > 64 || nodes.size() > 4096) {
            throw new LuaError("GUI presentation hierarchy is too deep or too large.");
        }
        for (int i = 1; i <= definitions.length(); i++) {
            LuaValue value = definitions.get(i);
            String nodePath = path + "[" + i + "]";
            String type = value.get("type").checkjstring().toLowerCase(Locale.ROOT);
            boolean group = "group".equals(type);
            ContainerGuiDefinition.Element element = group ? null : elements.get(leaf++);
            if (!value.get("id").isnil() && value.get("id").type() != LuaValue.TSTRING) {
                throw new LuaError("GUI element ID must be a string.");
            }
            String id = value.get("id").isnil() ? null : value.get("id").checkjstring();
            if (id != null && (id.isEmpty() || !ids.add(id))) {
                throw new LuaError("GUI element IDs must be nonempty and unique: " + id);
            }
            LuaValue binding = value.get("bind");
            if (!binding.isnil() && !binding.isfunction()) {
                throw new LuaError(nodePath + ".bind must be a function.");
            }
            ContainerGuiProperties properties = new ContainerGuiProperties(type, element, value);
            GuiTransitionPolicy policy = properties.transition(value.get("transition"));
            int index = nodes.size();
            nodes.add(new ContainerGuiNodeDefinition(id, nodePath, type, parent, element,
                    group ? ContainerGuiParser.parseCondition(value.get("visibleWhen"), container) : element.visibleWhen,
                    properties, binding, policy, !value.get("transition").isnil()));
            if (group) {
                read(value.get("elements"), index, nodePath + ".elements", depth + 1);
            }
        }
    }
}

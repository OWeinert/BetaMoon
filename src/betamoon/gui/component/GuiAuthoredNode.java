package betamoon.gui.component;

import betamoon.gui.animation.GuiTransitionPolicy;
import betamoon.gui.presentation.GuiProperties;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaValue;

/**
 * Immutable author declaration. Expansion compiles it into the existing flat
 * presentation/view index.
 */
public final class GuiAuthoredNode {
    public final String owner;
    public final String type;
    public final String component;
    public final String slot;
    public final int slotIndex;
    public final String id;
    public final String key;
    public final GuiProperties properties;
    public final Map<String, Object> props;
    public final Map<String, List<GuiAuthoredNode>> slots;
    public final List<GuiAuthoredNode> children;
    public final Map<String, LuaValue> handlers;
    public final LuaValue binding;
    public final GuiTransitionPolicy transition;
    public final boolean explicitTransition;

    public GuiAuthoredNode(String owner, String type, String component, String slot, int slotIndex, String id,
            String key, GuiProperties properties, Map<String, Object> props, Map<String, List<GuiAuthoredNode>> slots,
            List<GuiAuthoredNode> children, Map<String, LuaValue> handlers, LuaValue binding,
            GuiTransitionPolicy transition, boolean explicitTransition) {
        this.owner = owner;
        this.type = type;
        this.component = component;
        this.slot = slot;
        this.slotIndex = slotIndex;
        this.id = id;
        this.key = key;
        this.properties = properties;
        this.props = Collections.unmodifiableMap(new LinkedHashMap<>(props));
        Map<String, List<GuiAuthoredNode>> content = new LinkedHashMap<>();
        for (Map.Entry<String, List<GuiAuthoredNode>> entry : slots.entrySet()) {
            content.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        this.slots = Collections.unmodifiableMap(content);
        this.children = Collections.unmodifiableList(new ArrayList<>(children));
        this.handlers = Collections.unmodifiableMap(new LinkedHashMap<>(handlers));
        this.binding = binding;
        this.transition = transition;
        this.explicitTransition = explicitTransition;
    }
}

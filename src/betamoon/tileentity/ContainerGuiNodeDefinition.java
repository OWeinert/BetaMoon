package betamoon.tileentity;

import betamoon.gui.animation.GuiTransitionPolicy;
import org.luaj.vm2.LuaValue;

/** Registered node identity/hierarchy; never stores values or tracks belonging to an open screen. */
public final class ContainerGuiNodeDefinition {
    public final String id;
    public final String path;
    public final String type;
    public final int parent;
    public final ContainerGuiDefinition.Element element;
    public final ContainerGuiDefinition.Condition condition;
    public final ContainerGuiProperties properties;
    public final LuaValue binding;
    public final GuiTransitionPolicy transition;
    public final boolean explicitTransition;

    public ContainerGuiNodeDefinition(String id, String path, String type, int parent,
            ContainerGuiDefinition.Element element, ContainerGuiDefinition.Condition condition,
            ContainerGuiProperties properties, LuaValue binding, GuiTransitionPolicy transition,
            boolean explicitTransition) {
        this.id = id;
        this.path = path;
        this.type = type;
        this.parent = parent;
        this.element = element;
        this.condition = condition;
        this.properties = properties;
        this.binding = binding;
        this.transition = transition;
        this.explicitTransition = explicitTransition;
    }
}

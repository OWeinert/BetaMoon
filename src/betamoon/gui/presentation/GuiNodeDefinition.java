package betamoon.gui.presentation;

import betamoon.gui.animation.GuiTransitionPolicy;
import org.luaj.vm2.LuaValue;

/**
 * Registered node identity/hierarchy; never stores values or tracks belonging
 * to an open screen.
 */
public final class GuiNodeDefinition {
    public final String id;
    public final String path;
    public final String type;
    public final int parent;
    public final GuiDocumentDefinition.Element element;
    public final GuiDocumentDefinition.Condition condition;
    public final GuiProperties properties;
    public final LuaValue binding;
    public final GuiTransitionPolicy transition;
    public final boolean explicitTransition;

    public GuiNodeDefinition(String id, String path, String type, int parent, GuiDocumentDefinition.Element element,
            GuiDocumentDefinition.Condition condition, GuiProperties properties, LuaValue binding,
            GuiTransitionPolicy transition, boolean explicitTransition) {
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

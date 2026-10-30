package betamoon.gui.screen;

import betamoon.gui.component.GuiComponentDefinition;
import betamoon.gui.component.GuiComponentSchema;
import java.util.Objects;
import org.luaj.vm2.LuaValue;

/** An immutable document-declared effect; registration does not schedule it. */
public final class GuiActionDefinition {
    public enum Scope {
        CLIENT, WORLD, PLAYER_DATA
    }

    public final String name;
    public final Scope scope;
    public final GuiComponentSchema payload;
    public final LuaValue handler;

    public GuiActionDefinition(String name, Scope scope, GuiComponentSchema payload, LuaValue handler) {
        GuiComponentDefinition.name(name);
        if (handler == null || !handler.isfunction()) {
            throw new IllegalArgumentException("Declared UI actions require a handler");
        }
        this.name = name;
        this.scope = Objects.requireNonNull(scope, "action scope");
        this.payload = Objects.requireNonNull(payload, "action payload schema");
        this.handler = handler;
    }
}

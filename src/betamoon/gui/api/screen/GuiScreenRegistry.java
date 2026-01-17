package betamoon.gui.api.screen;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.src.GuiScreen;

public final class GuiScreenRegistry {
    private static final Map registry = new HashMap();

    private GuiScreenRegistry() {
    }

    public static void register(String id, IGuiScreenFactory factory) {
        if (id == null || id.trim().isEmpty() || factory == null) {
            return;
        }
        registry.put(id, factory);
    }

    public static IGuiScreenFactory getFactory(String id) {
        return (IGuiScreenFactory) registry.get(id);
    }

    public static GuiScreen create(String id, GuiScreen parent) {
        IGuiScreenFactory factory = getFactory(id);
        if (factory == null) {
            return null;
        }
        return factory.create(parent);
    }
}

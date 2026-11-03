package betamoon.gui.screen;

import betamoon.gui.collection.GuiRowBudget;
import betamoon.gui.component.GuiComponentPlan;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.presentation.GuiPresentationTree;
import betamoon.luaapi.utils.LuaCallbackScope;
import net.minecraft.src.GuiScreen;
import org.luaj.vm2.LuaTable;

/**
 * Java-owned native host attachment; ordinary Lua still declares registered
 * documents.
 */
public interface GuiScreenAttachment {
    GuiScreen nativeScreen();

    GuiScene scene();

    GuiRowBudget rowBudget();

    default boolean inputAvailable() {
        return true;
    }

    void attach(GuiPresentationTree document);

    void replace(GuiPresentationTree document);

    void detach();

    default void prepare(Object document, GuiComponentPlan plan) {
    }

    default void abort(Object document) {
    }

    default void commit(Object document) {
    }

    default void release(Object document) {
    }

    default void context(LuaTable context, LuaCallbackScope scope, GuiScreenActivation activation) {
    }
}

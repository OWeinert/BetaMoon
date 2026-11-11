package betamoon.gui.screen;

import betamoon.gui.collection.GuiRowBudget;
import betamoon.gui.component.GuiComponentPlan;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.presentation.GuiPresentationTree;
import betamoon.gui.presentation.GuiCapability;
import betamoon.gui.interop.GuiOwnedResources;
import betamoon.gui.graphics.GuiResourceDefinitions;
import net.minecraft.client.Minecraft;
import java.util.Collections;
import java.util.Set;
import java.util.Map;
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

    default GuiOwnedResources resources(Minecraft client, String owner, GuiResourceDefinitions definitions) {
        return new GuiOwnedResources(client, nativeScreen(), owner, definitions);
    }

    default Set<GuiCapability> capabilities() {
        return Collections.emptySet();
    }

    default boolean inputAvailable() {
        return true;
    }

    default boolean presentationAvailable() {
        return inputAvailable();
    }

    default void renderFailure(Throwable failure) {
    }

    default Map<String, Object> nativeData() {
        return Collections.emptyMap();
    }

    default Map<String, Object> nativeData(GuiScreenDefinition definition) {
        return nativeData();
    }

    void attach(GuiPresentationTree document);

    void replace(GuiPresentationTree document);

    void detach();

    default void prepare(Object document, GuiComponentPlan plan) {
    }

    default void prepare(Object document, GuiComponentPlan plan, GuiScreenDefinition definition) {
        prepare(document, plan);
    }

    default void prepareDocument(GuiPresentationTree document) {
        scene().prepareDetached(document);
    }

    default void validate(Object document) {
    }

    default void abort(Object document) {
    }

    default void commit(Object document) {
    }

    default void release(Object document) {
    }

    default void context(LuaTable context, LuaCallbackScope scope, GuiScreenActivation activation) {
    }

    default void context(LuaTable context, LuaCallbackScope scope, GuiScreenActivation activation,
            GuiScreenDefinition definition) {
        context(context, scope, activation);
    }
}

package betamoon.gui.screen;

import betamoon.gui.collection.GuiRowBudget;
import betamoon.gui.component.GuiComponentPlan;
import betamoon.gui.framework.GuiDocumentMount;
import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiInputLifetime;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.presentation.GuiPresentationTree;
import betamoon.gui.presentation.GuiCapability;
import betamoon.gui.screen.GuiActionDefinition.Scope;
import betamoon.gui.interop.GuiOwnedResources;
import betamoon.gui.graphics.GuiResourceDefinitions;
import java.util.Set;
import betamoon.luaapi.utils.LuaCallbackScope;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.src.GuiScreen;
import org.luaj.vm2.LuaTable;

/**
 * Shared registered-document lifecycle for Java-owned native presentation
 * adapters.
 */
public final class GuiAttachedDocument implements GuiScreenController.Host, GuiScreenAttachment, AutoCloseable {
    public interface Host {
        Minecraft client();

        GuiScreen screen();

        GuiScene scene();

        GuiRowBudget rowBudget();

        GuiHostElementBudget elementBudget();

        default Set<GuiCapability> capabilities() {
            return Collections.emptySet();
        }

        boolean available();

        boolean attached();

        boolean inputAvailable();

        default boolean presentationAvailable() {
            return inputAvailable();
        }

        default GuiOwnedResources resources(String owner, GuiResourceDefinitions definitions) {
            return new GuiOwnedResources(client(), screen(), owner, definitions);
        }

        default Map<String, Object> nativeData() {
            return Collections.emptyMap();
        }

        long environment();

        boolean worldAvailable();

        boolean paused();

        void context(LuaTable value, LuaCallbackScope scope, GuiScreenActivation activation);

        void failure(String owner, String path, Throwable failure);

        void closed();

        default void documentChanged() {
        }

        default boolean actionsAvailable() {
            return presentationAvailable();
        }
    }

    private Host host;
    private final String owner;
    private final String key;
    private final GuiDocumentMount mount = new GuiDocumentMount();
    private final GuiScreenController controller;
    private final Map<Object, GuiHostElementBudget.Claim> capacity = new IdentityHashMap<>();
    private GuiNativeScreenPage page;
    private Throwable paintFailure;
    private boolean closed;

    public GuiAttachedDocument(Host host, String owner, String key) {
        this.host = host;
        this.owner = owner;
        this.key = key;
        controller = new GuiScreenController(GuiClientUiRuntime.screens(), this, GuiClientUiRuntime.actions());
        GuiInputLifetime input = new GuiInputLifetime(this::inputAvailable);
        input.activate();
        mount.inputLifetime(input);
        mount.paintWhen(this::usable);
        mount.onFailure(this::renderFailure);
    }

    public GuiScreenController.OpenResult open() {
        return open(Collections.emptyMap());
    }

    public GuiScreenController.OpenResult open(Map<String, Object> data) {
        GuiScreenDefinition definition = GuiClientUiRuntime.screens().getRequired(key);
        if (!definition.owner.equals(owner)) {
            throw new IllegalArgumentException("Attached screen belongs to another owner");
        }
        return controller.open(owner, key, Collections.emptyMap(), data);
    }

    public GuiDocumentMount mount() {
        return mount;
    }

    public GuiScreenController controller() {
        return controller;
    }

    public GuiNativeScreenPage page() {
        return page;
    }

    public boolean usable() {
        return presentationAvailable() && !mount.failed() && page != null && page.attachedUsable();
    }

    public void tick() {
        if (!closed && paintFailure != null) {
            Throwable failure = paintFailure;
            paintFailure = null;
            host.failure(owner, key + ".paint", failure);
        }
        if (!closed) {
            controller.tick();
        }
    }

    public void suspend(String reason) {
        controller.suspend(reason);
    }

    public long environment() {
        return closed ? Long.MIN_VALUE : host.environment();
    }

    public boolean available() {
        return !closed && host.available();
    }

    public boolean worldAvailable() {
        return !closed && host.worldAvailable();
    }

    public boolean paused() {
        return closed || host.paused();
    }

    public boolean canOpen() {
        return !closed && host.attached();
    }

    public boolean canNavigate() {
        return false;
    }

    public boolean canOpenLayers() {
        return inputAvailable();
    }

    public Object screenIdentity() {
        return closed ? null : host.client().currentScreen;
    }

    public boolean current(GuiScreenActivation activation) {
        return canOpen() && host.presentationAvailable();
    }

    public String actionUnavailable(Scope scope) {
        return usable() && host.actionsAvailable() ? null : "presentation_unavailable";
    }

    public GuiScreenController.Page prepare(GuiScreenActivation activation, GuiScreenController.History history) {
        GuiNativeScreenPage prepared = new GuiNativeScreenPage(host.client(), activation, GuiClientUiRuntime.screens(),
                GuiClientUiRuntime.components(), history, this);
        if (page == null) {
            page = prepared;
        }
        return prepared;
    }

    public void display(GuiScreenActivation activation) {
        // The native screen retains its own container, navigation and close lifecycle.
    }

    public void fallback() {
        if (!closed) {
            host.closed();
        }
    }

    public void failure(String owner, String path, Throwable failure) {
        if (host != null) {
            host.failure(owner, path, failure);
        }
    }

    public GuiScreen nativeScreen() {
        return host.screen();
    }

    public GuiScene scene() {
        return host.scene();
    }

    public GuiOwnedResources resources(Minecraft client, String owner, GuiResourceDefinitions definitions) {
        if (closed) {
            throw new IllegalStateException("Attached document is closed");
        }
        return host.resources(owner, definitions);
    }

    public Set<GuiCapability> capabilities() {
        return closed ? Collections.emptySet() : host.capabilities();
    }

    public GuiRowBudget rowBudget() {
        return host.rowBudget();
    }

    public boolean inputAvailable() {
        return !closed && !mount.failed() && host.attached() && host.inputAvailable();
    }

    public void renderFailure(Throwable failure) {
        if (closed) {
            return;
        }
        mount.invalidatePaint();
        if (paintFailure == null) {
            paintFailure = failure;
        }
    }

    public Map<String, Object> nativeData() {
        return closed ? Collections.emptyMap() : host.nativeData();
    }

    public boolean presentationAvailable() {
        return !closed && host.attached() && host.presentationAvailable();
    }

    public void prepare(Object document, GuiComponentPlan plan) {
        capacity.put(document, host.elementBudget().prepare(document, plan.rowElementAllowance()));
    }

    public void commit(Object document) {
        GuiHostElementBudget.Claim pending = capacity.get(document);
        if (pending == null) {
            throw new IllegalStateException("Attached document has no capacity proposal");
        }
        pending.commit();
        capacity.remove(document);
    }

    public void abort(Object document) {
        GuiHostElementBudget.Claim pending = capacity.get(document);
        if (pending != null) {
            pending.close();
            capacity.remove(document);
        }
    }

    public void release(Object document) {
        abort(document);
        host.elementBudget().remove(document);
    }

    public void attach(GuiPresentationTree document) {
        mount.document(document);
        host.documentChanged();
    }

    public void replace(GuiPresentationTree document) {
        attach(document);
    }

    public void detach() {
        mount.document(null);
        page = null;
        host.documentChanged();
    }

    public void context(LuaTable value, LuaCallbackScope scope, GuiScreenActivation activation) {
        host.context(value, scope, activation);
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            try {
                controller.reset("attachment_detached");
                for (Object document : new ArrayList<>(capacity.keySet())) {
                    release(document);
                }
            } finally {
                try {
                    page = null;
                    paintFailure = null;
                    mount.document(null);
                    GuiContainer parent = mount.getParent();
                    if (parent != null) {
                        parent.remove(mount);
                    }
                } finally {
                    capacity.clear();
                    host = null;
                }
            }
        }
    }
}

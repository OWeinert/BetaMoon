package betamoon.gui.screen;

import betamoon.gui.component.GuiComponentPlan;
import betamoon.gui.framework.GuiInputLifetime;
import betamoon.gui.screen.GuiActionDefinition.Scope;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.Locale;
import java.util.Collections;
import java.util.Map;

/**
 * Owned activation handle. Pending opens acquire neither a native screen nor
 * input.
 */
public final class GuiScreenActivation implements GuiActionQueue.Activation {
    public enum Status {
        QUEUED, PREPARED, ACTIVE, CLOSED, FAILED, CANCELLED
    }

    public final long identity;
    final GuiScreenController controller;
    final long environment;
    Map<String, Object> initialState;
    Map<String, Object> initialData;
    private final String owner;
    public final String key;
    GuiScreenDefinition definition;
    GuiScreenActivation parent;
    final GuiInputLifetime input = new GuiInputLifetime();
    GuiScreenController.Page page;
    Status status = Status.QUEUED;
    String reason;
    long revision = 1;
    boolean closing;
    ScriptResourceTracker.Cleanup cleanup;

    GuiScreenActivation(GuiScreenController controller, long identity, GuiScreenDefinition definition, long environment,
            Map<String, Object> state, Map<String, Object> data) {
        this.controller = controller;
        this.identity = identity;
        this.definition = definition;
        this.owner = definition.owner;
        this.key = definition.key;
        this.environment = environment;
        this.initialState = state;
        this.initialData = data;
    }

    public GuiScreenDefinition definition() {
        if (definition == null) {
            throw new IllegalStateException("Screen activation is closed");
        }
        return definition;
    }

    public Status status() {
        return status;
    }

    public String reason() {
        return reason;
    }

    public String owner() {
        return owner;
    }

    public long revision() {
        return revision;
    }

    public boolean active() {
        return status == Status.ACTIVE && !closing && definition != null && definition.available()
                && (parent == null || parent.active());
    }

    public boolean terminal() {
        return status == Status.CLOSED || status == Status.FAILED || status == Status.CANCELLED;
    }

    public GuiScreenController.OpenResult dialog(String key, Map<String, Object> state, Map<String, Object> data) {
        return controller.dialog(this, key, state, data);
    }

    GuiScreenActivation root() {
        GuiScreenActivation value = this;
        while (value.parent != null) {
            value = value.parent;
        }
        return value;
    }

    public boolean close() {
        return controller.close(this);
    }

    public boolean back() {
        return controller.back(this);
    }

    public GuiScreenController.OpenResult push(String key, Map<String, Object> state, Map<String, Object> data) {
        return controller.navigate(this, key, state, data, true);
    }

    public GuiScreenController.OpenResult replace(String key, Map<String, Object> state, Map<String, Object> data) {
        return controller.navigate(this, key, state, data, false);
    }

    public Map<String, Object> state() {
        requireActive();
        return page.plan().state().values(GuiComponentPlan.SCREEN_STATE);
    }

    public void state(Map<String, Object> changes) {
        LuaCallbackScope.requireUiStateWrite();
        requireActive();
        page.plan().state().set(GuiComponentPlan.SCREEN_STATE, changes);
    }

    public Map<String, Object> data() {
        requireActive();
        return page.data();
    }

    public void data(Map<String, Object> changes) {
        LuaCallbackScope.requireUiStateWrite();
        requireActive();
        page.publish(changes);
    }

    public GuiComponentPlan plan() {
        requireActive();
        return page.plan();
    }

    public GuiActionQueue.Submission request(String name, Map<String, Object> payload) {
        LuaCallbackScope.requireUiStateWrite();
        requireActive();
        GuiActionDefinition action = definition.actions.get(name);
        if (action == null) {
            throw new IllegalArgumentException("Undeclared screen action: " + name);
        }
        return controller.actions().submit(this, action, payload);
    }

    public String unavailable(Scope scope) {
        if (!active()) {
            return "screen_closed";
        }
        if (controller.host().environment() != environment) {
            return "environment_changed";
        }
        if (!controller.host().current(this)) {
            return "screen_replaced";
        }
        if (scope == Scope.WORLD) {
            if (!controller.host().worldAvailable()) {
                return "world_unavailable";
            }
            if (controller.host().paused()) {
                return "world_paused";
            }
        }
        return null;
    }

    public Object execute(GuiActionDefinition action, Map<String, Object> payload) {
        requireActive();
        return page.execute(action, payload);
    }

    public void failed(String action, Throwable failure) {
        controller.host().failure(owner(), key + "." + action, failure);
    }

    public void requireActive() {
        if (!active() || page == null || controller.host().environment() != environment) {
            throw new IllegalStateException("Screen activation is unavailable: "
                    + (reason == null ? status.name().toLowerCase(Locale.ROOT) : reason));
        }
    }
}

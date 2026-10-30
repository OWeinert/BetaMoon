package betamoon.gui.screen;

import betamoon.gui.component.GuiComponentPlan;
import betamoon.gui.presentation.GuiPublishedData;
import betamoon.gui.screen.GuiActionDefinition.Scope;
import betamoon.gui.screen.GuiScreenActivation.Status;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Logical navigation and detached history, separate from the native
 * screen/scene adapter.
 */
public final class GuiScreenController {
    public interface Host {
        long environment();

        boolean available();

        boolean worldAvailable();

        boolean paused();

        boolean canOpen();

        Object screenIdentity();

        boolean current(GuiScreenActivation activation);

        Page prepare(GuiScreenActivation activation, History history);

        void display(GuiScreenActivation activation);

        void fallback();

        void failure(String owner, String path, Throwable failure);
    }

    public interface Page {
        GuiComponentPlan plan();

        Map<String, Object> data();

        void publish(Map<String, Object> changes);

        String focusedElement();

        void restoreFocus(String id);

        void activate();

        void update();

        default void flush() {
        }

        boolean needsRefresh(GuiScreenDefinition definition);

        void refresh(GuiScreenDefinition definition);

        Object execute(GuiActionDefinition action, Map<String, Object> payload);

        void close(String reason, boolean notify);
    }

    public static final class OpenResult {
        public final GuiScreenActivation instance;
        public final String reason;
        private OpenResult(GuiScreenActivation instance, String reason) {
            this.instance = instance;
            this.reason = reason;
        }
    }

    /**
     * Does not retain a native screen, scene, player, world or callback closure.
     */
    public static final class History {
        public final String key;
        public final String owner;
        public final long generation;
        public final long environment;
        public final Map<String, Object> state;
        public final Map<String, Object> data;
        public final String focus;
        private History(GuiScreenActivation activation) {
            key = activation.definition.key;
            owner = activation.owner();
            generation = ScriptResourceTracker.ownerGeneration(owner);
            environment = activation.environment;
            GuiPublishedData detached = new GuiPublishedData();
            detached.publish(activation.page.plan().state().snapshot());
            state = detached.snapshot();
            detached = new GuiPublishedData();
            detached.publish(activation.page.data());
            data = detached.snapshot();
            focus = activation.page.focusedElement();
        }

        public boolean available(GuiScreenRegistry registry, long currentEnvironment) {
            GuiScreenDefinition current = registry.snapshot().get(key);
            return environment == currentEnvironment && current != null && current.available()
                    && owner.equals(current.owner) && ScriptResourceTracker.ownerGeneration(owner) == generation;
        }
    }

    private enum Kind {
        OPEN, PUSH, REPLACE, BACK, CLOSE, DIALOG
    }
    private final GuiScreenRegistry registry;
    private final Host host;
    private final GuiActionQueue actions = new GuiActionQueue();
    private final Deque<Operation> pending = new ArrayDeque<>();
    private final Deque<History> history = new ArrayDeque<>();
    private GuiScreenActivation current;
    private final Map<Long, GuiScreenActivation> dialogs = new LinkedHashMap<>();
    private long nextIdentity;
    private boolean ticking;
    private WeakReference<Object> fallbackScreen;

    public GuiScreenController(GuiScreenRegistry registry, Host host) {
        this.registry = registry;
        this.host = host;
    }

    public static OpenResult unavailable(String reason) {
        return refused(reason);
    }

    public void reset(String reason) {
        for (Operation operation : new ArrayList<>(pending)) {
            if (operation.target != null) {
                retire(operation.target, reason, Status.CANCELLED, false);
            }
        }
        pending.clear();
        if (current != null) {
            retire(current, reason, Status.CLOSED, false);
        }
        for (GuiScreenActivation dialog : new ArrayList<>(dialogs.values())) {
            retire(dialog, reason, Status.CLOSED, false);
        }
        dialogs.clear();
        history.clear();
        fallbackScreen = null;
    }

    public Host host() {
        return host;
    }

    public GuiActionQueue actions() {
        return actions;
    }

    public GuiScreenActivation current() {
        return current;
    }

    public OpenResult open(String owner, String key, Map<String, Object> state, Map<String, Object> data) {
        return enqueue(owner, key, state, data, Kind.OPEN, null, null);
    }

    OpenResult navigate(GuiScreenActivation source, String key, Map<String, Object> state, Map<String, Object> data,
            boolean push) {
        if (source.parent != null) {
            return refused("dialog_navigation_unavailable");
        }
        return enqueue(source.owner(), key, state, data, push ? Kind.PUSH : Kind.REPLACE, source, null);
    }

    public OpenResult dialog(GuiScreenActivation source, String key, Map<String, Object> state,
            Map<String, Object> data) {
        if (dialogs.size() >= 32) {
            return refused("dialog_limit");
        }
        return enqueue(source.owner(), key, state, data, Kind.DIALOG, source, null);
    }

    private boolean belongsCurrent(GuiScreenActivation activation) {
        return activation == current
                || activation != null && activation.root() == current && dialogs.get(activation.identity) == activation;
    }

    private OpenResult enqueue(String owner, String key, Map<String, Object> state, Map<String, Object> data, Kind kind,
            GuiScreenActivation source, History restored) {
        LuaCallbackScope.requireUiStateWrite();
        GuiScreenDefinition definition = registry.getRequired(key);
        if (!definition.owner.equals(owner)) {
            return refused("wrong_owner");
        }
        if (!host.available()) {
            return refused("client_unavailable");
        }
        if (definition.context == GuiScreenDefinition.Context.WORLD && !host.worldAvailable()) {
            return refused("world_unavailable");
        }
        if (kind == Kind.OPEN && (current != null || !host.canOpen())) {
            return refused("screen_busy");
        }
        if (source != null && (!belongsCurrent(source) || !source.active() || !host.current(source))) {
            return refused("screen_replaced");
        }
        if (pending.size() >= 16) {
            return refused("navigation_queue_full");
        }
        if (kind == Kind.PUSH && history.size() >= 16) {
            return refused("navigation_history_full");
        }
        GuiPublishedData detached = new GuiPublishedData();
        detached.publish(definition.state.patch(definition.state.normalize(Collections.emptyMap()), state));
        Map<String, Object> initialState = detached.snapshot();
        detached = new GuiPublishedData();
        detached.publish(definition.data.patch(definition.data.normalize(Collections.emptyMap()), data));
        GuiScreenActivation activation = new GuiScreenActivation(this, ++nextIdentity, definition, host.environment(),
                // History owns compatible state; only explicit options may override it.
                restored == null ? initialState : immutable(state), detached.snapshot());
        ScriptResourceTracker.Cleanup cleanup = () -> ownerUnloaded(activation);
        activation.cleanup = cleanup;
        if (kind == Kind.DIALOG) {
            activation.parent = source;
        }
        ScriptExecution.withOwner(owner, () -> {
            ScriptResourceTracker.track(cleanup);
            return null;
        });
        if (kind == Kind.DIALOG) {
            dialogs.put(activation.identity, activation);
        }
        pending.addLast(new Operation(kind, source, activation, restored, host.screenIdentity()));
        return new OpenResult(activation, null);
    }

    public boolean close(GuiScreenActivation activation) {
        LuaCallbackScope.requireUiStateWrite();
        if (activation.terminal() || activation.closing) {
            return false;
        }
        if (activation.status == Status.QUEUED) {
            retire(activation, "user", Status.CANCELLED, false);
            return true;
        }
        if (!belongsCurrent(activation)) {
            return false;
        }
        activation.closing = true;
        activation.input.close();
        actions.cancel(activation, "screen_closed");
        pending.addFirst(new Operation(Kind.CLOSE, activation, null, null, host.screenIdentity()));
        return true;
    }

    public boolean back(GuiScreenActivation activation) {
        LuaCallbackScope.requireUiStateWrite();
        if (!belongsCurrent(activation) || !activation.active() || !host.current(activation)) {
            return false;
        }
        if (activation.parent != null) {
            return close(activation);
        }
        History previous = previous();
        if (previous == null) {
            return close(activation);
        }
        OpenResult result = enqueue(previous.owner, previous.key, Collections.emptyMap(), previous.data, Kind.BACK,
                activation, previous);
        return result.instance != null;
    }

    /**
     * Called at a fresh logical client boundary, after any invoking Lua/native
     * callback has returned.
     */
    public void tick() {
        if (ticking) {
            return;
        }
        ticking = true;
        try {
            if (fallbackScreen != null) {
                Object expected = fallbackScreen.get();
                fallbackScreen = null;
                if (expected != null && host.screenIdentity() == expected) {
                    host.fallback();
                }
            }
            if (current != null && (!current.definition.available() || current.environment != host.environment()
                    || !host.current(current))) {
                boolean owns = host.current(current);
                retire(current, "environment_changed", Status.CLOSED, false);
                history.clear();
                if (owns) {
                    host.fallback();
                }
            }
            Operation operation = pending.pollFirst();
            if (operation != null) {
                apply(operation);
            }
            updateCurrent();
            updateDialogs();
            actions.dispatch(Scope.CLIENT);
        } finally {
            ticking = false;
        }
    }

    private void updateCurrent() {
        GuiScreenActivation updating = current;
        if (updating == null || !updating.active()) {
            return;
        }
        try {
            GuiScreenDefinition definition = registry.snapshot().get(updating.key);
            if (definition == null || !definition.owner.equals(updating.owner())) {
                retire(updating, "definition_removed", Status.CLOSED, false);
                host.fallback();
                return;
            }
            updating.page.update();
            if (current == updating && updating.active() && updating.page.needsRefresh(definition)) {
                try {
                    updating.page.refresh(definition);
                    actions.cancel(updating, "screen_refreshed");
                    updating.definition = definition;
                    updating.revision++;
                } catch (VirtualMachineError | ThreadDeath fatal) {
                    throw fatal;
                } catch (Throwable failure) {
                    host.failure(updating.owner(), updating.key + ".refresh", failure);
                }
            }
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            boolean owns = current == updating && host.current(updating);
            retire(updating, "screen_update_failed", Status.FAILED, false);
            host.failure(updating.owner(), updating.key + ".update", failure);
            if (owns) {
                host.fallback();
            }
        }
    }

    private void updateDialogs() {
        boolean prepared = false;
        for (GuiScreenActivation dialog : new ArrayList<>(dialogs.values())) {
            if (dialog.status == Status.QUEUED || dialog.terminal()) {
                continue;
            }
            if (!dialog.definition.available() || dialog.parent == null || !dialog.parent.active()
                    || dialog.environment != host.environment() || !host.current(dialog)) {
                retire(dialog, "dialog_host_expired", Status.CLOSED, false);
                continue;
            }
            if (!dialog.active()) {
                continue;
            }
            try {
                dialog.page.update();
                prepared = true;
                GuiScreenDefinition definition = registry.snapshot().get(dialog.key);
                if (definition == null || !definition.owner.equals(dialog.owner())) {
                    retire(dialog, "definition_removed", Status.CLOSED, false);
                    continue;
                }
                if (dialog.active() && dialog.page.needsRefresh(definition)) {
                    try {
                        dialog.page.refresh(definition);
                        actions.cancel(dialog, "screen_refreshed");
                        dialog.definition = definition;
                        dialog.revision++;
                    } catch (VirtualMachineError | ThreadDeath fatal) {
                        throw fatal;
                    } catch (Throwable failure) {
                        host.failure(dialog.owner(), dialog.key + ".refresh", failure);
                    }
                }
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable failure) {
                retire(dialog, "dialog_update_failed", Status.FAILED, false);
                host.failure(dialog.owner(), dialog.key + ".update", failure);
            }
        }
        GuiScreenActivation flushing = current;
        if (prepared && flushing != null && flushing.active()) {
            try {
                flushing.page.flush();
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable failure) {
                boolean owns = current == flushing && host.current(flushing);
                retire(flushing, "screen_update_failed", Status.FAILED, false);
                host.failure(flushing.owner(), flushing.key + ".update", failure);
                if (owns) {
                    host.fallback();
                }
            }
        }
    }

    void dependencyExpired(GuiScreenActivation activation) {
        if (activation.parent != null) {
            retire(activation, "dependency_unloaded", Status.CLOSED, false);
        }
    }

    public void worldTick() {
        actions.dispatch(Scope.WORLD);
    }

    /**
     * Native replacement/close always takes precedence over queued scripted
     * navigation.
     */
    public void externalClosed(GuiScreenActivation activation) {
        if (activation.terminal()) {
            return;
        }
        boolean dialog = activation.parent != null;
        retire(activation, dialog ? "dialog_closed" : "screen_replaced", Status.CLOSED, dialog);
        if (!dialog) {
            history.clear();
        }
    }

    private void apply(Operation operation) {
        GuiScreenActivation target = operation.target;
        if (target != null && target.terminal()) {
            return;
        }
        if (!operation.sameScreen(host.screenIdentity())
                || (operation.source == null ? current != null : !belongsCurrent(operation.source))
                || operation.source != null && operation.source.revision != operation.sourceRevision) {
            if (target != null) {
                retire(target, "screen_replaced", Status.CANCELLED, false);
            }
            return;
        }
        if (operation.kind == Kind.CLOSE) {
            boolean dialog = operation.source.parent != null;
            retire(operation.source, "user", Status.CLOSED, true);
            if (!dialog) {
                history.clear();
                host.fallback();
            }
            return;
        }
        if (operation.source != null && !operation.source.active()) {
            retire(target, "screen_closed", Status.CANCELLED, false);
            return;
        }
        if (target.environment != host.environment() || !target.definition.available()
                || target.definition.context == GuiScreenDefinition.Context.WORLD && !host.worldAvailable()) {
            retire(target, "environment_changed", Status.CANCELLED, false);
            return;
        }
        Page prepared = null;
        try {
            prepared = host.prepare(target, operation.restored);
            if (target.terminal()) {
                prepared.close(target.reason, false);
                return;
            }
            target.page = prepared;
            target.status = Status.PREPARED;
            target.initialState = Collections.emptyMap();
            target.initialData = Collections.emptyMap();
            // Preparation can run author builders. Recheck every admission condition before
            // native ownership changes.
            if (target.terminal() || !target.definition.available() || target.environment != host.environment()
                    || !operation.sameScreen(host.screenIdentity())
                    || (operation.source == null ? current != null : !belongsCurrent(operation.source))) {
                retire(target, "preparation_invalidated", Status.CANCELLED, false);
                return;
            }
            if (operation.kind == Kind.DIALOG) {
                target.status = Status.ACTIVE;
                target.input.activate();
                prepared.activate();
                if (!target.active() || !host.current(target)) {
                    throw new IllegalStateException("Dialog activation was displaced");
                }
                return;
            }
            History predecessor = operation.kind == Kind.PUSH ? new History(operation.source) : null;
            if (operation.source != null) {
                retire(operation.source, "navigation", Status.CLOSED, true);
            }
            if (target.terminal() || !target.definition.available() || target.environment != host.environment()
                    || !operation.sameScreen(host.screenIdentity())) {
                retire(target, "activation_invalidated", Status.CANCELLED, false);
                host.fallback();
                return;
            }
            if (predecessor != null) {
                history.addLast(predecessor);
            }
            if (operation.restored != null) {
                history.remove(operation.restored);
            }
            current = target;
            target.status = Status.ACTIVE;
            target.input.activate();
            host.display(target);
            if (!target.active() || !host.current(target)) {
                throw new IllegalStateException("Native screen activation was displaced");
            }
            prepared.activate();
            if (operation.restored != null) {
                prepared.restoreFocus(operation.restored.focus);
            }
        } catch (VirtualMachineError | ThreadDeath fatal) {
            retire(target, "fatal_preparation_failure", Status.FAILED, false);
            throw fatal;
        } catch (Throwable failure) {
            boolean installed = current == target;
            boolean owns = installed && host.current(target);
            retire(target, "screen_preparation_failed", Status.FAILED, false);
            host.failure(target.owner(), target.key + ".open", failure);
            if (owns) {
                host.fallback();
            } else if (installed) {
                history.clear();
            }
        }
    }

    private void ownerUnloaded(GuiScreenActivation activation) {
        boolean owns = current == activation && host.current(activation);
        Object nativeScreen = owns ? host.screenIdentity() : null;
        retire(activation, "owner_unload", Status.CLOSED, false);
        history.removeIf(previous -> previous.owner.equals(activation.owner()));
        if (owns) {
            fallbackScreen = new WeakReference<>(nativeScreen);
        }
    }

    private void retire(GuiScreenActivation activation, String reason, Status status, boolean notify) {
        if (activation == null || activation.terminal()) {
            return;
        }
        activation.status = status;
        activation.reason = reason;
        activation.closing = false;
        activation.input.close();
        dialogs.remove(activation.identity);
        for (GuiScreenActivation child : new ArrayList<>(dialogs.values())) {
            if (child.parent == activation) {
                retire(child, reason, Status.CLOSED, notify);
            }
        }
        actions.cancel(activation, reason);
        pending.removeIf(operation -> operation.target == activation);
        if (current == activation) {
            current = null;
        }
        if (activation.cleanup != null) {
            ScriptResourceTracker.untrack(activation.owner(), activation.cleanup);
        }
        activation.cleanup = null;
        Page page = activation.page;
        activation.page = null;
        try {
            if (page != null) {
                try {
                    page.close(reason, notify && activation.definition.available());
                } catch (VirtualMachineError | ThreadDeath fatal) {
                    throw fatal;
                } catch (Throwable failure) {
                    host.failure(activation.owner(), activation.key + ".close", failure);
                }
            }
        } finally {
            activation.definition = null;
            activation.initialState = Collections.emptyMap();
            activation.initialData = Collections.emptyMap();
        }
    }

    private History previous() {
        while (!history.isEmpty() && !history.peekLast().available(registry, host.environment())) {
            history.removeLast();
        }
        return history.peekLast();
    }

    private static Map<String, Object> immutable(Map<String, Object> values) {
        GuiPublishedData detached = new GuiPublishedData();
        detached.publish(values);
        return detached.snapshot();
    }

    private static OpenResult refused(String reason) {
        return new OpenResult(null, reason);
    }

    private static final class Operation {
        final Kind kind;
        final GuiScreenActivation source;
        final long sourceRevision;
        final GuiScreenActivation target;
        final History restored;
        final WeakReference<Object> nativeScreen;
        final boolean hadScreen;
        Operation(Kind kind, GuiScreenActivation source, GuiScreenActivation target, History restored, Object screen) {
            this.kind = kind;
            this.source = source;
            sourceRevision = source == null ? 0 : source.revision;
            this.target = target;
            this.restored = restored;
            nativeScreen = new WeakReference<>(screen);
            hadScreen = screen != null;
        }

        boolean sameScreen(Object screen) {
            return hadScreen ? nativeScreen.get() != null && nativeScreen.get() == screen : screen == null;
        }
    }
}

package betamoon.gui.component;

import betamoon.gui.presentation.GuiPresentationRuntime;
import betamoon.luaapi.client.ui.GuiPropertyValues;
import betamoon.luaapi.client.ui.GuiReadOnlyValues;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * One component identity/scope. Slot placements can share caller state while
 * keeping local view IDs scoped.
 */
final class GuiComponentScope {
    interface Emitter {
        LuaValue emit(GuiComponentScope scope, String name, LuaValue payload);
    }

    final String path;
    final String owner;
    final GuiComponentDefinition definition;
    final GuiComponentScope parent;
    final GuiComponentScope eventScope;
    final String stateKey;
    final GuiComponentStateStore state;
    final Map<String, Object> props;
    final LuaValue propsView;
    final Map<String, List<GuiAuthoredNode>> slots;
    final Map<String, LuaValue> subscriptions;
    final Map<String, String> ids = new LinkedHashMap<>();
    final ScriptResourceTracker.OwnerGeneration ownerState;
    final long generation;
    boolean disabled;

    GuiComponentScope(String path, String owner, GuiComponentDefinition definition, GuiComponentScope parent,
            GuiComponentScope eventScope, String stateKey, GuiComponentStateStore state, Map<String, Object> props,
            Map<String, List<GuiAuthoredNode>> slots, Map<String, LuaValue> subscriptions) {
        this.path = path;
        this.owner = owner;
        this.definition = definition;
        this.parent = parent;
        this.eventScope = eventScope;
        this.stateKey = stateKey;
        this.state = state;
        this.props = props;
        this.propsView = GuiReadOnlyValues.fromPublished(props);
        this.slots = slots;
        this.subscriptions = subscriptions;
        ownerState = ScriptResourceTracker.ownerToken(owner);
        generation = ownerState.current();
    }

    boolean available() {
        return !disabled && ownerState.matches(generation) && (parent == null || parent.available());
    }

    void requireActive() {
        if (!available()) {
            throw new IllegalStateException(path + ": component scope is closed, disabled or expired");
        }
        if (definition != null) {
            definition.requireActive();
        }
    }

    String resolve(String id) {
        String resolved = ids.get(id);
        if (resolved == null) {
            throw new LuaError(path + ": unknown local/exported element: " + id);
        }
        return resolved;
    }

    void bindingView(LuaTable view) {
        requireActive();
        view.set("props", propsView);
        if (stateKey != null) {
            LuaValue observed = view.get("state");
            LuaTable local = new LuaTable();
            local.set("get", new VarArgFunction() {
                public Varargs invoke(Varargs arguments) {
                    requireActive();
                    String name = arguments.arg(arguments.arg1() == local ? 2 : 1).checkjstring();
                    if (!definition.state.fields().containsKey(name)) {
                        throw new LuaError(path + ": unknown local state field: " + name);
                    }
                    return observed.get("get").call(observed,
                            LuaValue.valueOf(GuiComponentStateStore.source(stateKey, name)));
                }
            });
            view.set("state", local);
        }
        LuaValue observedInteraction = view.get("interaction");
        if (observedInteraction.isfunction()) {
            view.set("interaction", new VarArgFunction() {
                public Varargs invoke(Varargs arguments) {
                    requireActive();
                    int offset = arguments.arg1() == view ? 1 : 0;
                    LuaValue name = arguments.arg(offset + 1);
                    LuaValue target = arguments.arg(offset + 2);
                    return target.isnil()
                            ? observedInteraction.call(view, name)
                            : observedInteraction.call(view, name, LuaValue.valueOf(resolve(target.checkjstring())));
                }
            });
        }
    }

    void eventContext(LuaTable context, LuaCallbackScope scope, Emitter emitter) {
        requireActive();
        scope.requireActive();
        context.set("props", propsView);
        if (stateKey != null) {
            LuaTable local = new LuaTable();
            local.set("get", new VarArgFunction() {
                public Varargs invoke(Varargs arguments) {
                    scope.requireActive();
                    requireActive();
                    return GuiPresentationRuntime
                            .lua(state.get(stateKey, arguments.arg(arguments.arg1() == local ? 2 : 1).checkjstring()));
                }
            });
            local.set("set", new VarArgFunction() {
                public Varargs invoke(Varargs arguments) {
                    scope.requireUiStateMutable();
                    requireActive();
                    int offset = arguments.arg1() == local ? 1 : 0;
                    String name = arguments.arg(offset + 1).checkjstring();
                    GuiComponentSchema.Field field = definition.state.fields().get(name);
                    if (field == null) {
                        throw new LuaError(path + ": unknown local state field: " + name);
                    }
                    Object value = GuiPropertyValues.read(field.descriptor, arguments.arg(offset + 2));
                    state.set(stateKey, Collections.singletonMap(name, value));
                    return NIL;
                }
            });
            context.set("state", local);
        }
        context.set("emit", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireUiStateMutable();
                requireActive();
                int offset = arguments.arg1() == context ? 1 : 0;
                return emitter.emit(GuiComponentScope.this, arguments.arg(offset + 1).checkjstring(),
                        arguments.arg(offset + 2));
            }
        });
        LuaValue globalGui = context.get("gui");
        if (globalGui.istable() && globalGui.get("element").isfunction()) {
            LuaTable localGui = new LuaTable();
            localGui.set("element", new VarArgFunction() {
                public Varargs invoke(Varargs arguments) {
                    scope.requireActive();
                    requireActive();
                    String name = arguments.arg(arguments.arg1() == localGui ? 2 : 1).checkjstring();
                    return globalGui.get("element").call(globalGui, LuaValue.valueOf(resolve(name)));
                }
            });
            context.set("gui", localGui);
        }
    }
}

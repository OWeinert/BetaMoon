package betamoon.luaapi.input;

import betamoon.client.control.input.InputActionEvent;
import betamoon.client.control.input.InputActionState;
import betamoon.client.control.input.InputBinding;
import betamoon.client.control.input.InputContext;
import betamoon.client.control.input.InputDisposition;
import betamoon.client.control.input.InputFamily;
import betamoon.client.control.input.InputMapRegistration;
import betamoon.luaapi.utils.InteractionOutcome;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.EnumSet;
import java.util.List;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Owner-scoped Lua handle for one registered input map and its default context.
 */
final class LuaInputMap extends LuaTable {
    private final InputMapRegistration registration;
    private final InputContext context;
    private final String owner;

    LuaInputMap(InputMapRegistration registration, int priority, EnumSet<InputFamily> captures, String owner) {
        this.registration = registration;
        this.context = registration.activate(priority, captures);
        this.owner = owner;
        set("key", LuaValue.valueOf(registration.definition().key().toString()));
        set("on", on(this, context));
        set("getState", getState());
        set("getBindings", getBindings());
        set("rebind", rebind());
        set("resetBinding", resetBinding());
        set("activate", activate());
        set("isActive", isActive());
        set("close", closeHandle());
    }

    private VarArgFunction getState() {
        return new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                String action = argument(args, LuaInputMap.this, 1).checkjstring();
                try {
                    return state(registration.state(action));
                } catch (IllegalArgumentException | IllegalStateException error) {
                    throw new LuaError("Input state: " + error.getMessage());
                }
            }
        };
    }

    private VarArgFunction getBindings() {
        return new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                String action = argument(args, LuaInputMap.this, 1).checkjstring();
                try {
                    LuaTable result = new LuaTable();
                    List<InputBinding> bindings = registration.bindings(action);
                    for (int index = 0; index < bindings.size(); index++) {
                        result.set(index + 1, LuaValue.valueOf(bindings.get(index).toString()));
                    }
                    return result;
                } catch (IllegalArgumentException | IllegalStateException error) {
                    throw new LuaError("Input bindings: " + error.getMessage());
                }
            }
        };
    }

    private VarArgFunction rebind() {
        return new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                int offset = args.arg1() == LuaInputMap.this ? 1 : 0;
                String action = args.arg(1 + offset).checkjstring();
                List<InputBinding> bindings = InputApi.bindings(args.arg(2 + offset), "Input rebind '" + action + "'");
                LuaValue options = args.arg(3 + offset);
                boolean allowConflict = false;
                if (!options.isnil()) {
                    LuaTable settings = InputApi.table(options, "Input rebind options");
                    InputApi.fields(settings, "Input rebind options", "allowConflict");
                    allowConflict = settings.get("allowConflict").optboolean(false);
                }
                try {
                    registration.rebind(action, bindings, allowConflict);
                    return LuaInputMap.this;
                } catch (IllegalArgumentException | IllegalStateException error) {
                    throw new LuaError("Input rebind: " + error.getMessage());
                }
            }
        };
    }

    private VarArgFunction resetBinding() {
        return new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                String action = argument(args, LuaInputMap.this, 1).checkjstring();
                try {
                    registration.resetBinding(action);
                    return LuaInputMap.this;
                } catch (IllegalArgumentException | IllegalStateException error) {
                    throw new LuaError("Input reset: " + error.getMessage());
                }
            }
        };
    }

    private VarArgFunction activate() {
        return new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                LuaValue value = argument(args, LuaInputMap.this, 1);
                LuaTable options = value.isnil() ? new LuaTable() : InputApi.table(value, "Input context options");
                InputApi.fields(options, "Input context options", "priority", "captures");
                int priority = InputApi.priority(options.get("priority"), 0, "Input context priority");
                EnumSet<InputFamily> captures = InputApi.captures(options.get("captures"), "Input context captures");
                try {
                    InputContext created = registration.activate(priority, captures);
                    ScriptResourceTracker.track(created::close);
                    return new LuaInputContext(created, owner);
                } catch (IllegalArgumentException | IllegalStateException error) {
                    throw new LuaError("Input context: " + error.getMessage());
                }
            }
        };
    }

    private VarArgFunction isActive() {
        return new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(registration.isActive() && context.isActive());
            }
        };
    }

    private VarArgFunction closeHandle() {
        return new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                registration.close();
                return LuaValue.NIL;
            }
        };
    }

    static VarArgFunction on(final LuaTable handle, final InputContext context) {
        return new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                int offset = args.arg1() == handle ? 1 : 0;
                String action = args.arg(1 + offset).checkjstring();
                LuaValue callback = args.arg(2 + offset);
                try {
                    if (callback.isnil()) {
                        context.on(action, null);
                    } else {
                        if (!callback.isfunction()) {
                            throw new LuaError("Input action callback must be a function or nil.");
                        }
                        final LuaValue function = callback.checkfunction();
                        final String callbackOwner = context.owner();
                        context.on(action,
                                event -> outcome(ScriptExecution.invoke(callbackOwner, function, event(event)).arg1(),
                                        "Input action '" + action + "'"));
                    }
                    return handle;
                } catch (IllegalArgumentException | IllegalStateException error) {
                    throw new LuaError("Input listener: " + error.getMessage());
                }
            }
        };
    }

    private static InputDisposition outcome(LuaValue value, String path) {
        InteractionOutcome outcome = InteractionOutcome.fromLua(value, path);
        if (outcome == InteractionOutcome.HANDLED) {
            return InputDisposition.HANDLED;
        }
        if (outcome == InteractionOutcome.DENY) {
            return InputDisposition.DENY;
        }
        return InputDisposition.PASS;
    }

    private static LuaTable event(InputActionEvent event) {
        LuaTable result = state(event.state());
        result.set("map", LuaValue.valueOf(event.mapKey().toString()));
        result.set("action", LuaValue.valueOf(event.action()));
        result.set("phase", LuaValue.valueOf(event.phase().luaName()));
        result.set("amount", LuaValue.valueOf(event.amount()));
        if (event.source() != null) {
            result.set("device", LuaValue.valueOf(event.source().device().luaName()));
            result.set("code", LuaValue.valueOf(event.source().code()));
        }
        return result;
    }

    private static LuaTable state(InputActionState state) {
        LuaTable result = new LuaTable();
        result.set("cycle", LuaValue.valueOf(state.cycle()));
        result.set("pressed", LuaValue.valueOf(state.pressed()));
        result.set("held", LuaValue.valueOf(state.held()));
        result.set("released", LuaValue.valueOf(state.released()));
        result.set("amount", LuaValue.valueOf(state.amount()));
        return result;
    }

    static LuaValue argument(Varargs args, LuaTable receiver, int index) {
        return args.arg(index + (args.arg1() == receiver ? 1 : 0));
    }
}

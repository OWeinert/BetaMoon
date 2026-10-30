package betamoon.gui.component;

import betamoon.luaapi.client.ui.GuiAuthoredNodeParser;
import betamoon.luaapi.client.ui.GuiReadOnlyValues;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * Scoped semantic/input events with local-state transactions and bounded
 * declared-event propagation.
 */
final class GuiComponentEvents implements GuiComponentScope.Emitter {
    private final GuiComponentPlan plan;
    private LuaTable outer;
    private int depth;

    GuiComponentEvents(GuiComponentPlan plan) {
        this.plan = plan;
    }

    LuaValue handle(LuaTable context, LuaTable payload) {
        GuiComponentPlan.View view = plan.views.get(payload.get("elementId").optjstring(""));
        if (view == null || !plan.available(view.scope)) {
            return LuaValue.FALSE;
        }
        String name = payload.get("event").checkjstring();
        LuaValue handler = "input".equals(name) && view.leaf != null
                ? view.leaf.input
                : view.authored.handlers.getOrDefault(name, LuaValue.NIL);
        if (handler.isnil()) {
            return LuaValue.NIL;
        }
        if (outer != null) {
            throw new IllegalStateException("Routed component events must use one outer transaction");
        }
        outer = context;
        plan.state.beginEvent();
        boolean finished = false;
        try {
            LuaTable event = new LuaTable();
            if ("input".equals(name) && payload.get("value").istable()) {
                for (LuaValue key : payload.get("value").checktable().keys()) {
                    event.set(key, payload.get("value").get(key));
                }
            } else {
                event.set("type", name);
                event.set("value", payload.get("value"));
            }
            event.set("elementId", view.authored.id == null ? LuaValue.NIL : LuaValue.valueOf(view.authored.id));
            LuaValue result = call(view.scope, handler, event);
            if ("input".equals(name)) {
                GuiComponentPlan.inputDecision(result, view.scope.path);
            } else if (!result.isnil() && !result.isboolean()) {
                throw new LuaError("Semantic handlers return boolean or nil");
            }
            finished = true;
            plan.state.finishEvent(true);
            return result;
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            plan.runtime.discardUiCommands();
            GuiComponentScope failed = failure instanceof CallbackFailure
                    ? ((CallbackFailure) failure).scope
                    : view.scope;
            plan.disable(failed);
            plan.services.failure(failed.owner, failed.path,
                    failure instanceof CallbackFailure ? failure.getCause() : failure);
            return LuaValue.FALSE;
        } finally {
            try {
                if (!finished) {
                    plan.state.finishEvent(false);
                }
            } finally {
                outer = null;
                depth = 0;
            }
        }
    }

    public LuaValue emit(GuiComponentScope scope, String name, LuaValue payload) {
        scope.requireActive();
        if (scope.definition == null || !scope.definition.events.containsKey(name)) {
            throw new LuaError(scope.path + ": undeclared component event: " + name);
        }
        if (outer == null) {
            throw new LuaError("Component events require an active scoped handler");
        }
        Map<String, Object> detached = GuiAuthoredNodeParser.readProps(scope.definition.events.get(name), payload,
                scope.path + ".event." + name);
        LuaValue subscriber = scope.subscriptions.getOrDefault(name, LuaValue.NIL);
        if (subscriber.isnil()) {
            return LuaValue.NIL;
        }
        LuaTable event = new LuaTable();
        event.set("type", name);
        event.set("payload", GuiReadOnlyValues.fromPublished(detached));
        LuaValue result = call(scope.eventScope, subscriber, event);
        if (!result.isnil() && !result.isboolean()) {
            throw new LuaError("Semantic handlers return boolean or nil");
        }
        return result;
    }

    private LuaValue call(GuiComponentScope scope, LuaValue handler, LuaTable event) {
        if (++depth > 16) {
            depth--;
            throw new LuaError("Component event propagation exceeds depth 16");
        }
        try (LuaCallbackScope callback = new LuaCallbackScope(false, true)) {
            scope.requireActive();
            LuaTable context = plan.eventContext(scope, callback, outer, this);
            return ScriptExecution.invoke(scope.owner, "ui.event." + scope.path, handler,
                    LuaValue.varargsOf(new LuaValue[]{context, event})).arg1();
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (CallbackFailure failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new CallbackFailure(scope, failure);
        } finally {
            depth--;
        }
    }

    private static final class CallbackFailure extends RuntimeException {
        final GuiComponentScope scope;
        CallbackFailure(GuiComponentScope scope, Throwable failure) {
            super(failure);
            this.scope = scope;
        }
    }
}

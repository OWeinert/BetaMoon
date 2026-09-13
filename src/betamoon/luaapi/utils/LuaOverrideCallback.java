package betamoon.luaapi.utils;

import betamoon.BetaMoonMain;
import betamoon.luamodloader.LuaScriptErrors;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * One effective callback layer with a scoped, single-use original invocation.
 */
public final class LuaOverrideCallback {
    public enum Result {
        EVENT, BOOLEAN, NUMBER, INTERACTION, VALUE
    }

    public final LuaOverrideDefinition definition;
    private boolean disabled;

    public LuaOverrideCallback(LuaOverrideDefinition definition) {
        this.definition = definition;
    }

    public boolean isEnabled() {
        return !disabled;
    }

    public LuaValue invoke(LuaTable context, Supplier<LuaValue> original, Result contract) {
        return invoke(context, original, value -> validate(value, contract), contract != Result.EVENT,
                contract == Result.INTERACTION);
    }

    public LuaValue invoke(LuaTable context, Supplier<LuaValue> original, UnaryOperator<LuaValue> validate) {
        return invoke(context, original, validate, true, false);
    }

    private LuaValue invoke(LuaTable context, Supplier<LuaValue> original, UnaryOperator<LuaValue> validate,
            boolean fallbackOnNil, boolean interaction) {
        BaseCall base = new BaseCall(original);
        context.set("base", base);
        try {
            LuaValue result = validate.apply(definition.action.call(context));
            if (fallbackOnNil && result.isnil() || interaction && result.raweq(LuaValue.valueOf("pass"))) {
                return base.originalResult();
            }
            return result.isnil() ? base.result : result;
        } catch (RuntimeException error) {
            disabled = true;
            String message = definition.name + " override disabled after error: " + error.getMessage();
            LuaScriptErrors.add(definition.owner, message);
            BetaMoonMain.LOGGER.warning(definition.owner + ": " + message);
            return base.originalResult();
        } finally {
            base.active = false;
        }
    }

    private static LuaValue validate(LuaValue value, Result contract) {
        if (contract == Result.EVENT) {
            return LuaValue.NIL;
        }
        if (value.isnil() || contract == Result.VALUE) {
            return value;
        }
        if (contract == Result.BOOLEAN && value.isboolean()) {
            return value;
        }
        if (contract == Result.NUMBER) {
            double number = LuaDeclarationValues.number(value, "callback result");
            if (number >= 0 && number <= 100000) {
                return value;
            }
        }
        if (contract == Result.INTERACTION && (value.raweq(LuaValue.valueOf("pass"))
                || value.raweq(LuaValue.valueOf("handled")) || value.raweq(LuaValue.valueOf("deny")))) {
            return InteractionOutcome.fromLua(value, "callback result").toLuaValue();
        }
        throw new LuaError("Invalid callback result for " + contract);
    }

    private static final class BaseCall extends VarArgFunction {
        private final Supplier<LuaValue> original;
        private boolean active = true;
        private boolean called;
        private LuaValue result = LuaValue.NIL;

        private BaseCall(Supplier<LuaValue> original) {
            this.original = original;
        }

        public Varargs invoke(Varargs arguments) {
            if (!active || called) {
                throw new LuaError("ctx:base() is valid once during its callback");
            }
            return originalResult();
        }

        private LuaValue originalResult() {
            if (!called) {
                called = true;
                result = original.get();
            }
            return result;
        }
    }
}

package betamoon.luaapi.utils;

import betamoon.BetaMoonCommon;

import betamoon.luamodloader.LuaScriptErrors;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Effective callback layers with scoped, single-use next-layer invocation.
 */
public final class LuaOverrideCallback {
    public enum Result {
        EVENT, BOOLEAN, NUMBER, INTERACTION, VALUE
    }

    private final LuaOverrideLayers<? extends LuaOverrideActionDefinition> layers;
    private final Set<LuaOverrideActionDefinition> disabled = Collections
            .newSetFromMap(new IdentityHashMap<LuaOverrideActionDefinition, Boolean>());

    public LuaOverrideCallback(LuaOverrideLayers<? extends LuaOverrideActionDefinition> layers) {
        if (layers == null || layers.definitions().isEmpty()) {
            throw new IllegalArgumentException("At least one callback override layer is required");
        }
        this.layers = layers;
    }

    public boolean isEnabled() {
        return effectiveDefinition() != null;
    }

    public LuaOverrideLayers<? extends LuaOverrideActionDefinition> layers() {
        return layers;
    }

    public LuaOverrideActionDefinition effectiveDefinition() {
        List<? extends LuaOverrideActionDefinition> definitions = layers.definitions();
        for (int i = definitions.size() - 1; i >= 0; i--) {
            LuaOverrideActionDefinition definition = definitions.get(i);
            if (!disabled.contains(definition)) {
                return definition;
            }
        }
        return null;
    }

    public LuaValue invoke(LuaTable context, Supplier<LuaValue> original, Result contract) {
        return invoke(context, original, value -> validate(value, contract), contract != Result.EVENT,
                contract == Result.INTERACTION);
    }

    public LuaValue invoke(LuaTable context, Supplier<LuaValue> original, UnaryOperator<LuaValue> validate) {
        return invoke(context, original, validate, true, false);
    }

    public LuaValue invoke(LuaTable context, Varargs arguments, Supplier<LuaValue> original,
            UnaryOperator<LuaValue> validate) {
        return invokeLayer(layers.definitions().size() - 1, context, arguments, original, validate, true, false);
    }

    private LuaValue invoke(LuaTable context, Supplier<LuaValue> original, UnaryOperator<LuaValue> validate,
            boolean fallbackOnNil, boolean interaction) {
        return invokeLayer(layers.definitions().size() - 1, context, context, original, validate, fallbackOnNil,
                interaction);
    }

    private LuaValue invokeLayer(int index, LuaTable context, Varargs arguments, Supplier<LuaValue> original,
            UnaryOperator<LuaValue> validate, boolean fallbackOnNil, boolean interaction) {
        List<? extends LuaOverrideActionDefinition> definitions = layers.definitions();
        while (index >= 0 && disabled.contains(definitions.get(index))) {
            index--;
        }
        if (index < 0) {
            return original.get();
        }
        final int nextIndex = index - 1;
        LuaOverrideActionDefinition definition = definitions.get(index);
        LuaValue previousBase = context.get("base");
        BaseCall base = new BaseCall(() -> invokeLayer(nextIndex, context, arguments, original, validate, fallbackOnNil,
                interaction));
        context.set("base", base);
        try {
            LuaValue result = validate.apply(definition.getAction().invoke(arguments).arg1());
            if (fallbackOnNil && result.isnil() || interaction && result.raweq(LuaValue.valueOf("pass"))) {
                return base.originalResult();
            }
            return result.isnil() ? base.result : result;
        } catch (RuntimeException error) {
            disabled.add(definition);
            String message = definition.getName() + " override disabled after error: " + error.getMessage();
            LuaScriptErrors.add(definition.getOwner(), message);
            BetaMoonCommon.LOGGER.warning(definition.getOwner() + ": " + message);
            return base.originalResult();
        } finally {
            base.active = false;
            context.set("base", previousBase);
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

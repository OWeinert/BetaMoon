package betamoon.luaapi.utils;

import betamoon.BetaMoonCommon;

import betamoon.luamodloader.LuaScriptErrors;
import java.util.EnumSet;
import java.util.Set;
import org.luaj.vm2.LuaValue;
import static betamoon.luaapi.utils.LuaDeclarationValues.error;

/** Consistent callback contracts and per-handler circuit breakers. */
public final class LuaCallbackDispatcher<K extends Enum<K> & LuaCallbackKey> {
    private final LuaCallbackDeclarations<K> declarations;
    private final Set<K> failed;

    public LuaCallbackDispatcher(LuaCallbackDeclarations<K> declarations) {
        this.declarations = declarations;
        failed = EnumSet.noneOf(declarations.keyType());
    }

    public boolean has(K key) {
        return declarations.has(key) && !failed.contains(key);
    }

    public LuaValue call(K key, LuaValue ctx, LuaValue fallback) {
        if (!has(key)) {
            return fallback;
        }
        try {
            return declarations.action(key).call(ctx);
        } catch (RuntimeException e) {
            fail(key, e);
            return fallback;
        }
    }

    public InteractionOutcome interaction(K key, LuaValue ctx) {
        if (!has(key)) {
            return InteractionOutcome.PASS;
        }
        LuaValue result = call(key, ctx, InteractionOutcome.DENY.toLuaValue());
        try {
            return InteractionOutcome.fromLua(result, key.luaName());
        } catch (RuntimeException error) {
            fail(key, error);
            return InteractionOutcome.DENY;
        }
    }

    public boolean query(K key, LuaValue ctx, boolean fallback) {
        LuaValue result = call(key, ctx, LuaValue.valueOf(fallback));
        if (result.isnil()) {
            return fallback;
        }
        if (!result.isboolean()) {
            fail(key, error(key.luaName(), "expected boolean result"));
            return fallback;
        }
        return result.toboolean();
    }

    public float numberQuery(K key, LuaValue context, float fallback) {
        LuaValue result = call(key, context, LuaValue.valueOf(fallback));
        if (result.isnil()) {
            return fallback;
        }
        try {
            double number = LuaDeclarationValues.number(result, key.luaName());
            if (number < 0 || number > 100000) {
                throw error(key.luaName(), "result must be between 0 and 100000");
            }
            return (float) number;
        } catch (RuntimeException error) {
            fail(key, error);
            return fallback;
        }
    }

    public void fail(K key, RuntimeException error) {
        if (!failed.add(key)) {
            return;
        }
        String message = declarations.resource + "." + key.luaName() + " disabled after error: " + error.getMessage();
        LuaScriptErrors.add(declarations.owner, message);
        BetaMoonCommon.LOGGER.warning(declarations.owner + ": " + message);
    }
}

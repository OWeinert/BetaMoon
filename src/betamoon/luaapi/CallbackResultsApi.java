package betamoon.luaapi;

import betamoon.luaapi.utils.InteractionOutcome;
import betamoon.luaapi.utils.LuaConstantTable;
import java.util.LinkedHashMap;
import java.util.Map;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Shared constants returned by block, item, and entity interaction callbacks. */
final class CallbackResultsApi {
    private CallbackResultsApi() {
    }

    static void attach(LuaTable module) {
        Map<String, LuaValue> values = new LinkedHashMap<>();
        values.put("pass", InteractionOutcome.PASS.toLuaValue());
        values.put("deny", InteractionOutcome.DENY.toLuaValue());
        values.put("handled", InteractionOutcome.HANDLED.toLuaValue());
        module.set("callbackResults", new LuaConstantTable("betamoon.callbackResults", values));
    }
}

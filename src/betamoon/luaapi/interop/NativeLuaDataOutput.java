package betamoon.luaapi.interop;

import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.query.NativeDataOutput;
import betamoon.interop.runtime.NativeContext;
import betamoon.interop.value.NativeExactValue;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * Query-local private Lua graph; never visible until the whole batch succeeds.
 */
final class NativeLuaDataOutput implements NativeDataOutput {
    private final NativeAccessApi host;
    private final NativeContext context;

    NativeLuaDataOutput(NativeAccessApi host, NativeContext context) {
        this.host = host;
        this.context = context;
    }

    public Object record(int fields) {
        return new LuaTable(0, fields);
    }

    public Object sequence(int length) {
        return new LuaTable(length, 0);
    }

    public void field(Object record, String key, Object value) {
        ((LuaTable) record).rawset(key, (LuaValue) value);
    }

    public void element(Object sequence, int index, Object value) {
        ((LuaTable) sequence).rawset(index + 1, (LuaValue) value);
    }

    public Object scalar(Object value) {
        if (value instanceof NativeExactValue) {
            return ((NativeExactValue) value).value == null
                    ? NativeLuaExact.NULL
                    : new NativeLuaExact(host, (NativeExactValue) value);
        }
        LuaValue result = host.result(value, context, false);
        if (result instanceof NativeLuaView) {
            throw new NativeAccessFailure("native_data_projection_required", "result was not detached");
        }
        return result;
    }
}

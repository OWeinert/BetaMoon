package betamoon.luaapi.interop;

import betamoon.luaapi.utils.LuaCallbackScope;

/** Java-only marker for a context whose native entry was explicitly audited. */
public interface NativeCallbackContext {
    LuaCallbackScope nativeCallbackScope();
}

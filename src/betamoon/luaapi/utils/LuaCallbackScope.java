package betamoon.luaapi.utils;

/**
 * Guards live access against mutation from queries and use after callback
 * return.
 */
public final class LuaCallbackScope implements AutoCloseable {
    private final boolean mutable;
    private boolean active = true;

    public LuaCallbackScope(boolean mutable) {
        this.mutable = mutable;
    }

    public void requireActive() {
        if (!active) {
            throw LuaDeclarationValues.error("context", "live access expired when the callback returned");
        }
    }

    public void requireMutable() {
        requireActive();
        if (!mutable) {
            throw LuaDeclarationValues.error("context",
                    "gameplay mutation is unavailable in queries or client callbacks");
        }
    }

    @Override
    public void close() {
        active = false;
    }
}

package betamoon.luaapi.utils;

/**
 * Guards live access against mutation from queries and use after callback
 * return.
 */
public final class LuaCallbackScope implements AutoCloseable {
    private final boolean mutable;
    private final boolean queuedTeleportAllowed;
    private boolean active = true;
    private int multiblockWork = 65536;

    public LuaCallbackScope(boolean mutable) {
        this(mutable, mutable);
    }

    public LuaCallbackScope(boolean mutable, boolean queuedTeleportAllowed) {
        this.mutable = mutable;
        this.queuedTeleportAllowed = queuedTeleportAllowed;
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

    public void requireQueuedTeleport() {
        requireActive();
        if (!queuedTeleportAllowed) {
            throw LuaDeclarationValues.error("teleport", "queue teleportation from a gameplay, GUI or input callback");
        }
    }

    public int multiblockWorkRemaining() {
        requireActive();
        return multiblockWork;
    }

    public void useMultiblockWork(int amount) {
        requireActive();
        if (amount < 0 || amount > multiblockWork) {
            throw LuaDeclarationValues.error("multiblock", "callback search budget exhausted");
        }
        multiblockWork -= amount;
    }

    public boolean isMutable() {
        requireActive();
        return mutable;
    }

    @Override
    public void close() {
        active = false;
    }
}

package betamoon.luaapi.utils;

import betamoon.interop.runtime.NativeEntry;

/**
 * Guards live access against mutation from queries and use after callback
 * return.
 */
public final class LuaCallbackScope implements AutoCloseable {
    private static final ThreadLocal<Integer> BINDING_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Integer> GAMEPLAY_READ_ONLY = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Integer> UI_READ_ONLY = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Integer> TELEPORT_READ_ONLY = ThreadLocal.withInitial(() -> 0);
    private final boolean mutable;
    private final boolean queuedTeleportAllowed;
    private final boolean uiStateMutable;
    private boolean binding;
    private boolean active = true;
    private int multiblockWork = 65536;
    private NativeEntry nativeEntry;

    public void nativeEntry(NativeEntry entry) {
        requireActive();
        this.nativeEntry = entry;
    }

    public NativeEntry nativeEntry() {
        requireActive();
        return nativeEntry;
    }

    public LuaCallbackScope(boolean mutable) {
        this(mutable, mutable);
    }

    public LuaCallbackScope(boolean mutable, boolean uiStateMutable) {
        this(mutable, uiStateMutable, mutable);
    }

    private LuaCallbackScope(boolean mutable, boolean uiStateMutable, boolean queuedTeleportAllowed) {
        this.mutable = mutable;
        this.uiStateMutable = uiStateMutable;
        this.queuedTeleportAllowed = queuedTeleportAllowed;
        if (!mutable) {
            GAMEPLAY_READ_ONLY.set(GAMEPLAY_READ_ONLY.get() + 1);
        }
        if (!uiStateMutable) {
            UI_READ_ONLY.set(UI_READ_ONLY.get() + 1);
        }
        if (!queuedTeleportAllowed) {
            TELEPORT_READ_ONLY.set(TELEPORT_READ_ONLY.get() + 1);
        }
    }

    /**
     * Allows deferred teleport requests without granting immediate gameplay or UI
     * writes.
     */
    public static LuaCallbackScope withQueuedTeleport(boolean mutable, boolean queuedTeleportAllowed) {
        return new LuaCallbackScope(mutable, mutable, queuedTeleportAllowed);
    }

    public static LuaCallbackScope withQueuedTeleport(boolean mutable, boolean uiStateMutable,
            boolean queuedTeleportAllowed) {
        return new LuaCallbackScope(mutable, uiStateMutable, queuedTeleportAllowed);
    }

    public static LuaCallbackScope binding() {
        LuaCallbackScope scope = new LuaCallbackScope(false, false);
        scope.binding = true;
        BINDING_DEPTH.set(BINDING_DEPTH.get() + 1);
        return scope;
    }

    public static void requireNoBindingMutation() {
        if (BINDING_DEPTH.get() > 0) {
            throw LuaDeclarationValues.error("bind", "mutation is unavailable during presentation derivation");
        }
    }

    public static void requireGameplayWrite() {
        requireNoBindingMutation();
        if (GAMEPLAY_READ_ONLY.get() > 0) {
            throw LuaDeclarationValues.error("context", "gameplay mutation is unavailable in this callback");
        }
    }

    public static void requireUiStateWrite() {
        requireNoBindingMutation();
        if (UI_READ_ONLY.get() > 0) {
            throw LuaDeclarationValues.error("state", "state is read-only in this callback");
        }
    }

    /**
     * Effective read-only intersection, for Java update coordinators; querying it
     * grants no authority.
     */
    public static boolean uiStateWriteAllowed() {
        return BINDING_DEPTH.get() == 0 && UI_READ_ONLY.get() == 0;
    }

    public void requireUiStateMutable() {
        requireActive();
        requireUiStateWrite();
        if (!uiStateMutable) {
            throw LuaDeclarationValues.error("state", "state is read-only in this callback");
        }
    }

    public void requireActive() {
        if (!active) {
            throw LuaDeclarationValues.error("context", "live access expired when the callback returned");
        }
    }

    public void requireMutable() {
        requireActive();
        requireGameplayWrite();
        if (!mutable) {
            throw LuaDeclarationValues.error("context",
                    "gameplay mutation is unavailable in queries or client callbacks");
        }
    }

    public void requireQueuedTeleport() {
        requireActive();
        requireNoBindingMutation();
        if (!queuedTeleportAllowed || TELEPORT_READ_ONLY.get() > 0) {
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
        return mutable && GAMEPLAY_READ_ONLY.get() == 0;
    }

    public boolean isBinding() {
        requireActive();
        return binding;
    }

    @Override
    public void close() {
        if (active && !mutable) {
            GAMEPLAY_READ_ONLY.set(GAMEPLAY_READ_ONLY.get() - 1);
        }
        if (active && !uiStateMutable) {
            UI_READ_ONLY.set(UI_READ_ONLY.get() - 1);
        }
        if (active && binding) {
            BINDING_DEPTH.set(BINDING_DEPTH.get() - 1);
        }
        if (active && !queuedTeleportAllowed) {
            TELEPORT_READ_ONLY.set(TELEPORT_READ_ONLY.get() - 1);
        }
        active = false;
        nativeEntry = null;
    }
}

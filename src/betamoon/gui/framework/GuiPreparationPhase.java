package betamoon.gui.framework;

import betamoon.interop.runtime.NativeContext;
import betamoon.luaapi.utils.LuaCallbackScope;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Java update orchestration between callbacks; it grants no native or Lua
 * effects.
 */
public final class GuiPreparationPhase implements AutoCloseable {
    private static final ThreadLocal<Deque<GuiPreparationPhase>> PHASES = ThreadLocal.withInitial(ArrayDeque::new);
    private boolean closed;

    private GuiPreparationPhase() {
    }

    public static GuiPreparationPhase open() {
        if (NativeContext.hasFrame()) {
            throw new IllegalStateException("Logical UI preparation must start outside Native Interop callbacks");
        }
        LuaCallbackScope.requireUiStateWrite();
        GuiPreparationPhase phase = new GuiPreparationPhase();
        PHASES.get().push(phase);
        return phase;
    }

    /**
     * A nested Lua/native callback cannot inherit its Java caller's preparation
     * eligibility.
     */
    public static boolean active() {
        return !NativeContext.hasFrame() && LuaCallbackScope.uiStateWriteAllowed() && !PHASES.get().isEmpty();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        Deque<GuiPreparationPhase> phases = PHASES.get();
        if (phases.peek() != this) {
            throw new IllegalStateException("Logical UI preparation closes in reverse order on its owning thread");
        }
        phases.pop();
        closed = true;
    }
}

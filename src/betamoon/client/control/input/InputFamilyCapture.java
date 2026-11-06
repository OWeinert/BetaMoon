package betamoon.client.control.input;

import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.EnumSet;

/**
 * Bounded native-family ownership in the existing router, without a dummy
 * named-action map.
 */
public final class InputFamilyCapture implements AutoCloseable, GuiInputOwner {
    private final InputRouter router;
    private final String owner;
    private final EnumSet<InputFamily> families;
    private final ScriptResourceTracker.Cleanup cleanup = this::close;
    private boolean active = true;
    private boolean tracked;

    InputFamilyCapture(InputRouter router, String owner, EnumSet<InputFamily> families) {
        this.router = router;
        this.owner = owner;
        this.families = families.clone();
    }

    void track() {
        ScriptExecution.withOwner(owner, () -> {
            ScriptResourceTracker.track(cleanup);
            return null;
        });
        tracked = true;
    }

    boolean captures(InputFamily family) {
        return active && families.contains(family);
    }

    public boolean isActive() {
        return active;
    }

    public boolean ownsGuiInput() {
        return active;
    }

    @Override
    public void close() {
        if (!active) {
            return;
        }
        if (families.contains(InputFamily.WORLD_ACTIONS)) {
            router.retainHeldPointer(this);
        }
        active = false;
        router.removeCapture(this);
        if (tracked) {
            ScriptResourceTracker.untrack(owner, cleanup);
            tracked = false;
        }
    }
}

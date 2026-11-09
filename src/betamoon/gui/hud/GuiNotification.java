package betamoon.gui.hud;

import betamoon.gui.framework.GuiGeometry.Rect;
import java.lang.ref.WeakReference;
import java.util.Collections;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptResourceTracker;
import betamoon.gui.screen.GuiScreenActivation;

/**
 * An owned detached queue entry; native presentation is prepared separately.
 */
public final class GuiNotification {
    public enum State {
        QUEUED, VISIBLE, EXITING, DISMISSED, EXPIRED, CANCELLED, FAILED
    }

    public final long id;
    public final GuiNotificationDefinition definition;
    final GuiHudActivation activation;
    final GuiHudDefinition contribution;
    final GuiNotificationQueue queue;
    final WeakReference<Object> world;
    final WeakReference<Object> player;
    GuiNotificationOptions options;
    State state = State.QUEUED;
    State ending;
    String reason;
    Rect bounds = Rect.EMPTY;
    long elapsed;
    long advancedAt;
    long enteredAt;
    long exitAt;
    long placementSequence;
    boolean displayed;
    boolean admitted;
    boolean hovered;
    boolean focused;
    Runnable onEnd;
    final ScriptResourceTracker.Cleanup cleanup;
    boolean tracked;

    GuiNotification(GuiNotificationQueue queue, long id, GuiNotificationDefinition definition,
            GuiNotificationOptions options, Object world, Object player, long now) {
        this.queue = queue;
        this.id = id;
        this.definition = definition;
        this.options = options;
        this.world = new WeakReference<>(world);
        this.player = new WeakReference<>(player);
        advancedAt = now;
        String key = "betamoon:notification/" + id;
        activation = new GuiHudActivation(definition.owner, key, Long.MAX_VALUE / 2 + id, Collections.emptySet(),
                () -> queue.cancel(this, "screen_closed"));
        contribution = definition.contribution(key);
        cleanup = () -> queue.cancel(this, "owner_unloaded");
    }

    public State state() {
        return state;
    }

    public String reason() {
        return reason;
    }

    public GuiNotificationOptions options() {
        return options;
    }

    public boolean live() {
        return state == State.QUEUED || state == State.VISIBLE;
    }

    public boolean update(GuiNotificationOptions candidate) {
        return queue.update(this, candidate);
    }

    public String updateReason(GuiNotificationOptions candidate) {
        return queue.updateReason(this, candidate);
    }

    public GuiScreenActivation instance() {
        return live() ? GuiHudRuntime.instance(activation, null) : null;
    }

    public boolean inputAvailable() {
        return live() && GuiHudRuntime.inputAvailable(activation);
    }

    public String enterCursorMode() {
        LuaCallbackScope.requireUiStateWrite();
        return live() ? GuiHudRuntime.enterCursorMode(activation) : "inactive";
    }

    public boolean isCursorMode() {
        return live() && GuiHudRuntime.cursorMode(activation);
    }

    public void leaveCursorMode() {
        LuaCallbackScope.requireUiStateWrite();
        GuiHudRuntime.stopInteraction(activation);
    }

    public void dismiss() {
        LuaCallbackScope.requireUiStateWrite();
        queue.end(this, State.DISMISSED, "dismissed");
    }

    public Rect bounds() {
        return bounds;
    }

    public void rendered() {
        if (!bounds.isEmpty() && live() && !displayed) {
            displayed = true;
            state = State.VISIBLE;
            advancedAt = queue.now();
            enteredAt = advancedAt;
        }
    }

    public double opacity(long now) {
        if (ending != null) {
            return Math.max(0, 1 - Math.max(0, now - exitAt) / 120.0);
        }
        return displayed ? Math.max(0.01, Math.min(1, Math.max(0, now - enteredAt) / 150.0)) : 0.01;
    }

    public void pause(boolean hovered, boolean focused) {
        this.hovered = hovered;
        this.focused = focused;
    }

    public void onEnd(Runnable cleanup) {
        onEnd = cleanup;
    }
}

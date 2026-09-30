package betamoon.client.control.input;

import betamoon.content.ContentKey;

/** Detached semantic transition delivered through an active input context. */
public final class InputActionEvent {
    private final ContentKey mapKey;
    private final String action;
    private final InputPhase phase;
    private final float amount;
    private final long cycle;
    private final InputDeviceEvent source;
    private final InputActionState state;

    InputActionEvent(ContentKey mapKey, String action, InputPhase phase, float amount, long cycle,
            InputDeviceEvent source, InputActionState state) {
        this.mapKey = mapKey;
        this.action = action;
        this.phase = phase;
        this.amount = amount;
        this.cycle = cycle;
        this.source = source;
        this.state = state;
    }

    public ContentKey mapKey() {
        return mapKey;
    }

    public String action() {
        return action;
    }

    public InputPhase phase() {
        return phase;
    }

    public float amount() {
        return amount;
    }

    public long cycle() {
        return cycle;
    }

    public InputDeviceEvent source() {
        return source;
    }

    public InputActionState state() {
        return state;
    }
}

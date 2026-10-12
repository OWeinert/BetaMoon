package betamoon.client.control.input;

/** Immutable, cycle-coherent public view of one named action. */
public final class InputActionState {
    private final long cycle;
    private final boolean pressed;
    private final boolean held;
    private final boolean released;
    private final float amount;
    private final InputActivationMode mode;
    private final boolean active;
    private final boolean activated;
    private final boolean deactivated;

    InputActionState(long cycle, boolean pressed, boolean held, boolean released, float amount) {
        this(cycle, pressed, held, released, amount, InputActivationMode.HOLD, held, pressed, released);
    }

    InputActionState(long cycle, boolean pressed, boolean held, boolean released, float amount,
            InputActivationMode mode, boolean active, boolean activated, boolean deactivated) {
        this.cycle = cycle;
        this.pressed = pressed;
        this.held = held;
        this.released = released;
        this.amount = amount;
        this.mode = mode;
        this.active = active;
        this.activated = activated;
        this.deactivated = deactivated;
    }

    public long cycle() {
        return cycle;
    }

    public boolean pressed() {
        return pressed;
    }

    public boolean held() {
        return held;
    }

    public boolean released() {
        return released;
    }

    public float amount() {
        return amount;
    }

    public InputActivationMode mode() {
        return mode;
    }

    public boolean active() {
        return active;
    }

    public boolean activated() {
        return activated;
    }

    public boolean deactivated() {
        return deactivated;
    }
}

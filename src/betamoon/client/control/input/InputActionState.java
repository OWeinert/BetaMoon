package betamoon.client.control.input;

/** Immutable, cycle-coherent public view of one named action. */
public final class InputActionState {
    private final long cycle;
    private final boolean pressed;
    private final boolean held;
    private final boolean released;
    private final float amount;

    InputActionState(long cycle, boolean pressed, boolean held, boolean released, float amount) {
        this.cycle = cycle;
        this.pressed = pressed;
        this.held = held;
        this.released = released;
        this.amount = amount;
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
}

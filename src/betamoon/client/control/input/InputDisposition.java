package betamoon.client.control.input;

/** Closed result of a named input-action callback. */
public enum InputDisposition {
    PASS, HANDLED, DENY;

    public boolean consumesNativeInput() {
        return this != PASS;
    }
}

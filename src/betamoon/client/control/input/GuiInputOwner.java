package betamoon.client.control.input;

/**
 * Java UI surfaces without an actual current GuiScreen can own their physical
 * release.
 */
public interface GuiInputOwner {
    boolean ownsGuiInput();
}

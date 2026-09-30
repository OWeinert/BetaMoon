package betamoon.client.control.input;

/** Listener invoked by ModLoader for a registered hotkey. */
@FunctionalInterface
public interface HotkeyListener {
    void pressed(HotkeyEvent event);
}

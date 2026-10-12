package betamoon.client.control.input;

import betamoon.content.ContentKey;

/** Snapshot delivered when ModLoader dispatches a registered hotkey. */
public final class HotkeyEvent {
    private final ContentKey key;
    private final String binding;
    private final int keyCode;

    HotkeyEvent(ContentKey key, String binding, int keyCode) {
        this.key = key;
        this.binding = binding;
        this.keyCode = keyCode;
    }

    public ContentKey key() {
        return key;
    }

    public String binding() {
        return binding;
    }

    public int keyCode() {
        return keyCode;
    }
}

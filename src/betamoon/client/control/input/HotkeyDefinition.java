package betamoon.client.control.input;

import betamoon.content.ContentKey;
import betamoon.content.ContentType;
import org.lwjgl.input.Keyboard;

/** Immutable declaration for one user-configurable ModLoader hotkey. */
public final class HotkeyDefinition {
    public static final int MAX_LABEL_LENGTH = 128;

    private final ContentKey key;
    private final String label;
    private final int defaultKeyCode;
    private final boolean repeat;

    public HotkeyDefinition(ContentKey key, String label, int defaultKeyCode, boolean repeat) {
        if (key == null || !ContentType.HOTKEY.equals(key.type())) {
            throw new IllegalArgumentException("Hotkey key must use the hotkey content type");
        }
        String normalizedLabel = label == null ? null : label.trim();
        if (normalizedLabel == null || normalizedLabel.length() == 0) {
            throw new IllegalArgumentException("Hotkey label is required");
        }
        if (normalizedLabel.length() > MAX_LABEL_LENGTH) {
            throw new IllegalArgumentException("Hotkey label exceeds " + MAX_LABEL_LENGTH + " characters");
        }
        if (defaultKeyCode < Keyboard.KEY_NONE || defaultKeyCode >= Keyboard.KEYBOARD_SIZE) {
            throw new IllegalArgumentException("Hotkey default key code is outside the LWJGL keyboard range");
        }
        this.key = key;
        this.label = normalizedLabel;
        this.defaultKeyCode = defaultKeyCode;
        this.repeat = repeat;
    }

    public ContentKey key() {
        return key;
    }

    public String label() {
        return label;
    }

    public int defaultKeyCode() {
        return defaultKeyCode;
    }

    public boolean repeat() {
        return repeat;
    }
}

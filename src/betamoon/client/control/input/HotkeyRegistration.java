package betamoon.client.control.input;

import net.minecraft.src.KeyBinding;

/** Owner-scoped handle for a retained native hotkey slot. */
public final class HotkeyRegistration implements AutoCloseable {
    private final HotkeyRegistry registry;
    private final HotkeyRegistry.Slot slot;
    private final String owner;
    private boolean active = true;
    private HotkeyListener listener;

    HotkeyRegistration(HotkeyRegistry registry, HotkeyRegistry.Slot slot, String owner) {
        this.registry = registry;
        this.slot = slot;
        this.owner = owner;
    }

    public HotkeyDefinition definition() {
        return slot.definition();
    }

    public String owner() {
        return owner;
    }

    public boolean isActive() {
        return active && slot.activeRegistration() == this;
    }

    public HotkeyRegistration on(HotkeyListener listener) {
        requireActive();
        this.listener = listener;
        return this;
    }

    public String binding() {
        requireActive();
        return HotkeyRegistry.bindingName(slot.binding());
    }

    public int keyCode() {
        requireActive();
        return slot.binding().keyCode;
    }

    public KeyBinding nativeBinding() {
        return slot.binding();
    }

    HotkeyListener listener() {
        return listener;
    }

    void disableListener() {
        listener = null;
    }

    void deactivate() {
        active = false;
        listener = null;
    }

    private void requireActive() {
        if (!isActive()) {
            throw new IllegalStateException("Hotkey is no longer active: " + definition().key());
        }
    }

    @Override
    public void close() {
        registry.unregister(this);
    }
}

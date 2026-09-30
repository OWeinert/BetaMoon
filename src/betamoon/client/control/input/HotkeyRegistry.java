package betamoon.client.control.input;

import betamoon.content.ContentKey;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.src.KeyBinding;
import org.lwjgl.input.Keyboard;

/** Retains native bindings while attaching reloadable script registrations. */
public final class HotkeyRegistry {
    public interface Backend {
        KeyBinding register(String descriptionKey, String label, int defaultKeyCode, boolean repeat);

        void updateLabel(String descriptionKey, String label);
    }

    public interface FailureHandler {
        void failed(HotkeyRegistration registration, Throwable error);
    }

    public static final int MAX_HOTKEYS = 256;

    private final Backend backend;
    private final FailureHandler failures;
    private final Map<ContentKey, Slot> slots = new LinkedHashMap<ContentKey, Slot>();
    private final Map<KeyBinding, Slot> byBinding = new IdentityHashMap<KeyBinding, Slot>();

    public HotkeyRegistry(Backend backend, FailureHandler failures) {
        if (backend == null) {
            throw new IllegalArgumentException("Hotkey backend is required");
        }
        this.backend = backend;
        this.failures = failures;
    }

    public synchronized HotkeyRegistration register(HotkeyDefinition definition, String owner) {
        if (definition == null || owner == null || owner.trim().length() == 0) {
            throw new IllegalArgumentException("Hotkey definition and owner are required");
        }
        Slot slot = slots.get(definition.key());
        if (slot != null && slot.activeRegistration() != null) {
            throw new IllegalStateException("Hotkey is already registered: " + definition.key());
        }
        if (slot == null) {
            if (slots.size() >= MAX_HOTKEYS) {
                throw new IllegalStateException("Hotkey limit reached: " + MAX_HOTKEYS);
            }
            String descriptionKey = descriptionKey(definition.key());
            KeyBinding binding = backend.register(descriptionKey, definition.label(), definition.defaultKeyCode(),
                    definition.repeat());
            if (binding == null) {
                throw new IllegalStateException("Hotkey backend did not create a native binding");
            }
            slot = new Slot(definition, descriptionKey, binding);
            slots.put(definition.key(), slot);
            byBinding.put(binding, slot);
        } else {
            if (slot.definition().repeat() != definition.repeat()) {
                throw new IllegalStateException("Hotkey repeat cannot change until Minecraft restarts: "
                        + definition.key());
            }
            backend.updateLabel(slot.descriptionKey(), definition.label());
            slot.updateDefinition(definition);
        }
        HotkeyRegistration registration = new HotkeyRegistration(this, slot, owner);
        slot.activate(registration);
        return registration;
    }

    public synchronized boolean dispatch(KeyBinding binding) {
        Slot slot = byBinding.get(binding);
        if (slot == null || slot.activeRegistration() == null) {
            return false;
        }
        HotkeyRegistration registration = slot.activeRegistration();
        HotkeyListener listener = registration.listener();
        if (listener == null) {
            return true;
        }
        try {
            listener.pressed(new HotkeyEvent(slot.definition().key(), bindingName(binding), binding.keyCode));
        } catch (Throwable error) {
            registration.disableListener();
            if (failures != null) {
                failures.failed(registration, error);
            }
        }
        return true;
    }

    synchronized void unregister(HotkeyRegistration registration) {
        if (registration == null) {
            return;
        }
        Slot slot = slots.get(registration.definition().key());
        if (slot != null && slot.activeRegistration() == registration) {
            slot.deactivate();
        }
        registration.deactivate();
    }

    public synchronized int activeCount() {
        int count = 0;
        for (Slot slot : slots.values()) {
            if (slot.activeRegistration() != null) {
                count++;
            }
        }
        return count;
    }

    public synchronized int retainedCount() {
        return slots.size();
    }

    static String bindingName(KeyBinding binding) {
        String name = Keyboard.getKeyName(binding.keyCode);
        if (name == null || name.length() == 0) {
            return "key.none";
        }
        return "key." + name.toLowerCase(Locale.ENGLISH);
    }

    private static String descriptionKey(ContentKey key) {
        return "key.betamoon.hotkey." + encode(key.namespace()) + "." + encode(key.name());
    }

    private static String encode(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '.') {
                result.append("%2e");
            } else if (character == '/') {
                result.append("%2f");
            } else if (character == '%') {
                result.append("%25");
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }

    static final class Slot {
        private HotkeyDefinition definition;
        private final String descriptionKey;
        private final KeyBinding binding;
        private HotkeyRegistration activeRegistration;

        private Slot(HotkeyDefinition definition, String descriptionKey, KeyBinding binding) {
            this.definition = definition;
            this.descriptionKey = descriptionKey;
            this.binding = binding;
        }

        HotkeyDefinition definition() {
            return definition;
        }

        String descriptionKey() {
            return descriptionKey;
        }

        KeyBinding binding() {
            return binding;
        }

        HotkeyRegistration activeRegistration() {
            return activeRegistration;
        }

        void updateDefinition(HotkeyDefinition definition) {
            this.definition = definition;
        }

        void activate(HotkeyRegistration registration) {
            activeRegistration = registration;
        }

        void deactivate() {
            activeRegistration = null;
        }
    }
}

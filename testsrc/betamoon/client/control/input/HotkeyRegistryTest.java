package betamoon.client.control.input;

import betamoon.content.ContentKey;
import betamoon.content.ContentType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.src.KeyBinding;
import org.lwjgl.input.Keyboard;

/** Verifies retained ModLoader hotkey identity, reload, dispatch, and isolation. */
public final class HotkeyRegistryTest {
    private HotkeyRegistryTest() {
    }

    public static void main(String[] args) {
        FakeBackend backend = new FakeBackend();
        final int[] failures = { 0 };
        HotkeyRegistry registry = new HotkeyRegistry(backend, (registration, error) -> failures[0]++);
        ContentKey key = ContentKey.of("mymod", ContentType.HOTKEY, "open/menu");
        HotkeyRegistration first = registry.register(
                new HotkeyDefinition(key, "Open Menu", Keyboard.KEY_G, false), "first.lua");
        require(backend.registrations == 1 && registry.activeCount() == 1 && registry.retainedCount() == 1,
                "A new declaration creates one retained native slot");

        final List<HotkeyEvent> events = new ArrayList<HotkeyEvent>();
        first.on(events::add);
        require(registry.dispatch(first.nativeBinding()) && events.size() == 1,
                "The native binding dispatches its owner");
        require(events.get(0).key().equals(key) && "key.g".equals(events.get(0).binding()),
                "Dispatch exposes canonical identity and normalized binding");
        expectFailure(() -> registry.register(new HotkeyDefinition(key, "Duplicate", Keyboard.KEY_H, false),
                "other.lua"), "Active duplicate hotkeys are rejected");

        first.nativeBinding().keyCode = Keyboard.KEY_K;
        first.close();
        require(!registry.dispatch(first.nativeBinding()) && registry.activeCount() == 0
                && registry.retainedCount() == 1,
                "Unload detaches callbacks but retains the Controls entry");
        HotkeyRegistration reloaded = registry.register(
                new HotkeyDefinition(key, "Open Better Menu", Keyboard.KEY_H, false), "second.lua");
        require(backend.registrations == 1 && backend.labelUpdates == 1,
                "Reload reuses the native registration and updates its label");
        require(reloaded.nativeBinding() == first.nativeBinding() && reloaded.keyCode() == Keyboard.KEY_K,
                "Reload preserves the user's live Controls remap");

        reloaded.on(event -> {
            throw new IllegalStateException("listener failure");
        });
        registry.dispatch(reloaded.nativeBinding());
        registry.dispatch(reloaded.nativeBinding());
        require(failures[0] == 1, "A failing callback is disabled without repeated failures");
        reloaded.close();
        expectFailure(() -> registry.register(new HotkeyDefinition(key, "Open Menu", Keyboard.KEY_G, true),
                "third.lua"), "Retained ModLoader repeat policy cannot change before restart");

        ContentKey dotted = ContentKey.of("my.mod", ContentType.HOTKEY, "open.menu");
        HotkeyRegistration distinct = registry.register(
                new HotkeyDefinition(dotted, "Dotted", Keyboard.KEY_M, false), "fourth.lua");
        require(!distinct.nativeBinding().keyDescription.equals(first.nativeBinding().keyDescription),
                "Native description keys preserve namespace and path boundaries");
        System.out.println("Hotkey registry passed: ModLoader slots, dispatch, reload, remaps and failures.");
    }

    private static void expectFailure(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (IllegalArgumentException | IllegalStateException expected) {
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class FakeBackend implements HotkeyRegistry.Backend {
        private int registrations;
        private int labelUpdates;

        @Override
        public KeyBinding register(String descriptionKey, String label, int defaultKeyCode, boolean repeat) {
            registrations++;
            return new KeyBinding(descriptionKey, defaultKeyCode);
        }

        @Override
        public void updateLabel(String descriptionKey, String label) {
            labelUpdates++;
        }
    }
}

package betamoon.client.control.input;

import betamoon.luaapi.LuaApiUtils;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.src.BaseMod;
import net.minecraft.src.GameSettings;
import net.minecraft.src.KeyBinding;
import net.minecraft.src.ModLoader;

/** Client-global bridge between reloadable hotkeys and ModLoader's native key API. */
public final class ClientHotkeys {
    private static HotkeyRegistry registry;

    private ClientHotkeys() {
    }

    public static synchronized void initialize(BaseMod mod) {
        if (registry != null) {
            return;
        }
        if (mod == null) {
            throw new IllegalArgumentException("BetaMoon BaseMod is required for hotkeys");
        }
        registry = new HotkeyRegistry(new ModLoaderBackend(mod),
                (registration, error) -> LuaApiUtils.warnForScript(registration.owner(), "Input",
                        registration.definition().key() + " hotkey callback disabled after error: "
                                + safeMessage(error)));
    }

    public static synchronized HotkeyRegistry registry() {
        if (registry == null) {
            throw new IllegalStateException("Client hotkeys are not initialized");
        }
        return registry;
    }

    public static boolean dispatch(KeyBinding binding) {
        HotkeyRegistry current;
        synchronized (ClientHotkeys.class) {
            current = registry;
        }
        return current != null && current.dispatch(binding);
    }

    public static int activeCount() {
        synchronized (ClientHotkeys.class) {
            return registry == null ? 0 : registry.activeCount();
        }
    }

    public static int retainedCount() {
        synchronized (ClientHotkeys.class) {
            return registry == null ? 0 : registry.retainedCount();
        }
    }

    private static String safeMessage(Throwable error) {
        if (error == null) {
            return "unknown error";
        }
        String message = error.getMessage();
        return message == null || message.trim().length() == 0 ? error.getClass().getSimpleName() : message;
    }

    private static final class ModLoaderBackend implements HotkeyRegistry.Backend {
        private final BaseMod mod;

        private ModLoaderBackend(BaseMod mod) {
            this.mod = mod;
        }

        @Override
        public KeyBinding register(String descriptionKey, String label, int defaultKeyCode, boolean repeat) {
            KeyBinding binding = new KeyBinding(descriptionKey, defaultKeyCode);
            ModLoader.AddLocalization(descriptionKey, label);
            ModLoader.RegisterKey(mod, binding, repeat);
            publishLateBinding(binding);
            return binding;
        }

        @Override
        public void updateLabel(String descriptionKey, String label) {
            ModLoader.AddLocalization(descriptionKey, label);
        }

        private static void publishLateBinding(KeyBinding binding) {
            Minecraft minecraft = ModLoader.getMinecraftInstance();
            if (minecraft == null || minecraft.gameSettings == null) {
                return;
            }
            GameSettings settings = minecraft.gameSettings;
            for (KeyBinding existing : settings.keyBindings) {
                if (existing == binding) {
                    return;
                }
            }
            settings.keyBindings = Arrays.copyOf(settings.keyBindings, settings.keyBindings.length + 1);
            settings.keyBindings[settings.keyBindings.length - 1] = binding;
            settings.loadOptions();
        }
    }
}

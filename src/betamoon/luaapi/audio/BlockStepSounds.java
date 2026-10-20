package betamoon.luaapi.audio;

import betamoon.assets.AssetKey;
import betamoon.content.ContentKey;
import betamoon.content.ContentType;
import betamoon.instrumentation.agent.AgentRuntime;
import betamoon.instrumentation.diagnostics.HookDiagnostic;
import betamoon.instrumentation.diagnostics.HookStatus;
import betamoon.luaapi.utils.LuaContentNamespaces;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.minecraft.MinecraftBuiltins;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.src.StepSound;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.number;
import static betamoon.luaapi.utils.LuaDeclarationValues.required;

/** Retained native step sounds backed by registered BetaMoon sound events. */
public final class BlockStepSounds {
    private static final Map<String, Entry> CUSTOM = new LinkedHashMap<String, Entry>();
    private static final Map<String, Entry> BY_MARKER = new LinkedHashMap<String, Entry>();
    private static final String MARKER = "betamoon.step.";
    private static int nextId;

    private BlockStepSounds() {
    }

    static synchronized Entry register(LuaValue value) {
        requirePlaybackHook();
        String owner = LuaScriptRegistry.getCurrentScriptFile();
        if (owner == null) {
            throw new LuaError("Step sounds may only be declared while a Lua script is loading.");
        }
        fields(value, "stepSound", "key", "event", "volume", "pitch");
        AssetKey key = key(required(value, "key"));
        AssetKey event = SoundEvents.requireKey(required(value, "event"), "stepSound.event");
        float volume = positive(value.get("volume"), 1.0F, "stepSound.volume", 0.0F, 4.0F);
        float pitch = positive(value.get("pitch"), 1.0F, "stepSound.pitch", 0.01F, 4.0F);
        Entry existing = CUSTOM.get(key.toString());
        if (existing != null) {
            if (!owner.equals(existing.owner)) {
                throw new LuaError("Step sound '" + key + "' is already owned by another script.");
            }
            if (existing.volume != volume || existing.pitch != pitch) {
                throw new LuaError("Step sound '" + key
                        + "' changed native volume or pitch. Restart Minecraft to change these properties.");
            }
            existing.event = event;
            return existing;
        }
        String marker = MARKER + (++nextId);
        Entry created = new Entry(key.toString(), owner, marker, event, volume, pitch);
        CUSTOM.put(created.key, created);
        BY_MARKER.put(marker, created);
        return created;
    }

    public static synchronized StepSound resolve(LuaValue input, String path) {
        if (input instanceof StepSoundReference) {
            return ((StepSoundReference) input).entry().sound;
        }
        if (!input.isstring()) {
            throw new LuaError(path + ": expected a vanilla step sound name or registered step sound reference");
        }
        StepSound sound = find(input.checkjstring());
        if (sound == null) {
            throw new LuaError(path + ": unknown step sound '" + input.checkjstring() + "'");
        }
        return sound;
    }

    public static synchronized StepSound find(String name) {
        Entry entry = CUSTOM.get(name);
        return entry == null ? MinecraftBuiltins.resolveStepSound(name) : entry.sound;
    }

    public static synchronized Entry entry(String name) {
        return CUSTOM.get(name);
    }

    public static synchronized String name(StepSound sound) {
        for (Entry entry : CUSTOM.values()) {
            if (entry.sound == sound) {
                return entry.key;
            }
        }
        for (String name : MinecraftBuiltins.stepSounds().keySet()) {
            if (MinecraftBuiltins.resolveStepSound(name) == sound) {
                return name;
            }
        }
        return sound == null ? "" : sound.stepSoundDir();
    }

    public static synchronized AssetKey eventForMarker(String marker) {
        Entry entry = BY_MARKER.get(marker);
        return entry == null ? null : entry.event;
    }

    public static boolean isMarker(String name) {
        return name != null && name.startsWith(MARKER);
    }

    private static void requirePlaybackHook() {
        if (AgentRuntime.isRegistered()) {
            for (HookDiagnostic diagnostic : AgentRuntime.getReport().snapshot()) {
                if ("betamoon:step_sound:play".equals(diagnostic.getHookId())) {
                    HookStatus status = diagnostic.getStatus();
                    if (status == HookStatus.APPLIED || status == HookStatus.ALREADY_APPLIED
                            || status == HookStatus.WAITING_FOR_TARGET) {
                        return;
                    }
                }
            }
        }
        throw new LuaError("Custom step sounds require the BetaMoon native audio hook.");
    }

    private static AssetKey key(LuaValue value) {
        try {
            ContentKey content = ContentKey.parseForType(value.checkjstring(), ContentType.STEP_SOUND);
            LuaContentNamespaces.requireDeclarationNamespace(content);
            return AssetKey.parse(content.toString());
        } catch (IllegalArgumentException error) {
            throw new LuaError("stepSound.key: " + error.getMessage());
        }
    }

    private static float positive(LuaValue input, float fallback, String path, float min, float max) {
        if (input.isnil()) {
            return fallback;
        }
        double value = number(input, path);
        if (value <= min || value > max) {
            throw new LuaError(path + ": expected a value above " + min + " and at most " + max);
        }
        return (float) value;
    }

    public static final class Entry {
        public final String key;
        public final String owner;
        public final StepSound sound;
        public final float volume;
        public final float pitch;
        private volatile AssetKey event;
        private StepSoundReference reference;

        private Entry(String key, String owner, String marker, AssetKey event, float volume, float pitch) {
            this.key = key;
            this.owner = owner;
            this.event = event;
            this.volume = volume;
            this.pitch = pitch;
            this.sound = new EventStepSound(marker, volume, pitch);
        }

        public AssetKey event() {
            return event;
        }

        synchronized StepSoundReference reference() {
            if (reference == null) {
                reference = new StepSoundReference(this);
            }
            return reference;
        }
    }

    private static final class EventStepSound extends StepSound {
        private final String marker;

        private EventStepSound(String marker, float volume, float pitch) {
            super(marker, volume, pitch);
            this.marker = marker;
        }

        @Override
        public String stepSoundDir() {
            return marker;
        }

        @Override
        public String func_1145_d() {
            return marker;
        }
    }
}

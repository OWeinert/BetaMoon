package betamoon.luaapi.audio;

import betamoon.assets.AssetKey;
import betamoon.client.assets.AssetLocation;
import betamoon.client.audio.ClientAudio;
import betamoon.client.audio.ClientSounds;
import betamoon.client.audio.SoundAsset;
import betamoon.luaapi.asset.AssetInputs;
import java.io.IOException;
import java.util.Random;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

/**
 * Local sound events and one-shot playback; networking and controllable voices
 * come later.
 */
public final class AudioApi {
    private static final Random RANDOM = new Random();
    private AudioApi() {
    }

    public static void attach(LuaTable module) {
        LuaTable events = new LuaTable();
        events.set("add", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                SoundEventDefinition definition = SoundEventParser.read(args.arg(args.arg1() == events ? 2 : 1));
                try {
                    SoundEvents.stage(definition);
                } catch (IllegalStateException error) {
                    throw new LuaError("Sound event: " + error.getMessage());
                }
                return new EventReference(definition.key);
            }
        });
        events.set("get", lookup(events, false));
        events.set("getRequired", lookup(events, true));
        module.set("soundEvents", events);
        LuaTable audio = new LuaTable();
        audio.set("play", new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                int first = args.arg1() == audio ? 2 : 1;
                play(args.arg(first), args.arg(first + 1));
                return NONE;
            }
        });
        module.set("audio", audio);
    }

    private static VarArgFunction lookup(LuaTable registry, boolean required) {
        return new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                AssetKey key = key(args.arg(args.arg1() == registry ? 2 : 1));
                if (SoundEvents.find(key) == null) {
                    if (required) {
                        throw new LuaError("Sound event not registered: " + key);
                    }
                    return NIL;
                }
                return new EventReference(key);
            }
        };
    }

    private static AssetKey key(LuaValue value) {
        try {
            return AssetKey.parse(value.checkjstring());
        } catch (IllegalArgumentException error) {
            throw new LuaError("Sound event: " + error.getMessage());
        }
    }

    private static void play(LuaValue sound, LuaValue options) {
        LuaTable settings = options.isnil() ? new LuaTable() : options.checktable();
        SoundEventParser.checkFields(settings, "position", "volume", "pitch", "range");
        SoundEventDefinition event = null;
        if (sound instanceof EventReference) {
            AssetKey key = ((EventReference) sound).key;
            event = SoundEvents.find(key);
            if (event == null) {
                throw new LuaError("Sound event is no longer registered: " + key);
            }
        } else if (sound.type() == LuaValue.TSTRING && sound.checkjstring().indexOf(':') >= 0) {
            event = SoundEvents.find(key(sound));
        }
        AssetLocation location = event == null ? AssetInputs.sound(sound) : event.choose(RANDOM);
        float volume = SoundEventParser.number(settings.get("volume"), event == null ? 1 : event.volume, 0, 1,
                "volume");
        float defaultPitch = event == null
                ? 1
                : event.pitchMin + RANDOM.nextFloat() * (event.pitchMax - event.pitchMin);
        float pitch = SoundEventParser.number(settings.get("pitch"), defaultPitch, 0.01f, 4, "pitch");
        float range = SoundEventParser.number(settings.get("range"), event == null ? 16 : event.range, 0.01f, 1024,
                "range");
        LuaValue position = settings.get("position");
        float x = 0;
        float y = 0;
        float z = 0;
        if (!position.isnil()) {
            LuaTable coordinates = position.checktable();
            SoundEventParser.checkFields(coordinates, "x", "y", "z");
            x = coordinate(coordinates.get("x"), "x");
            y = coordinate(coordinates.get("y"), "y");
            z = coordinate(coordinates.get("z"), "z");
        }
        try {
            ClientSounds.retainForPlayback(location);
        } catch (IOException error) {
            throw new LuaError("Sound playback: " + error.getMessage());
        }
        try (SoundAsset clip = ClientSounds.acquire(location)) {
            if (!position.isnil() && clip.getContent().getValue().getFormat().getChannels() != 1) {
                throw new LuaError("Positional sounds require a mono clip; export mono or omit position");
            }
            ClientAudio.play(clip.getContent().getValue(), x, y, z, !position.isnil(), volume, pitch, range);
        } catch (IOException error) {
            throw new LuaError("Sound playback: " + error.getMessage());
        }
    }

    private static float coordinate(LuaValue value, String name) {
        if (value.isnil()) {
            throw new LuaError("Sound position requires " + name);
        }
        return SoundEventParser.number(value, 0, -32000000, 32000000, "position." + name);
    }

    private static final class EventReference extends LuaTable {
        private final AssetKey key;
        private EventReference(AssetKey key) {
            this.key = key;
            set("getKey", new ZeroArgFunction() {
                public LuaValue call() {
                    return valueOf(key.toString());
                }
            });
        }
    }
}

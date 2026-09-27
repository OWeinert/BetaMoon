package betamoon.luaapi.audio;

import betamoon.assets.AssetKey;
import betamoon.luaapi.asset.AssetInputs;
import java.util.ArrayList;
import java.util.List;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

final class SoundEventParser {
    private SoundEventParser() {
    }

    static SoundEventDefinition read(LuaValue value) {
        LuaTable table = value.checktable();
        checkFields(table, "key", "sound", "clips", "volume", "pitch", "range");
        AssetKey key;
        try {
            key = AssetKey.parse(table.get("key").checkjstring());
        } catch (IllegalArgumentException error) {
            throw new LuaError("Sound event: " + error.getMessage());
        }
        LuaValue single = table.get("sound");
        LuaValue alternatives = table.get("clips");
        if (single.isnil() == alternatives.isnil()) {
            throw new LuaError("Sound event requires exactly one of sound or clips");
        }
        List<SoundEventDefinition.Clip> clips = new ArrayList<>();
        if (!single.isnil()) {
            clips.add(new SoundEventDefinition.Clip(AssetInputs.sound(single), 1));
        } else {
            LuaTable list = alternatives.checktable();
            if (list.length() < 1 || list.length() > 256 || list.keys().length != list.length()) {
                throw new LuaError("Sound event clips must be a dense list of 1 to 256 entries");
            }
            for (int i = 1; i <= list.length(); i++) {
                LuaTable clip = list.get(i).checktable();
                checkFields(clip, "sound", "weight");
                clips.add(new SoundEventDefinition.Clip(AssetInputs.sound(clip.get("sound")),
                        number(clip.get("weight"), 1, 0.000001f, 1000000, "weight")));
            }
        }
        float min;
        float max;
        LuaValue pitch = table.get("pitch");
        if (pitch.istable()) {
            checkFields(pitch.checktable(), "min", "max");
            min = number(pitch.get("min"), 1, 0.01f, 4, "pitch.min");
            max = number(pitch.get("max"), 1, 0.01f, 4, "pitch.max");
            if (min > max) {
                throw new LuaError("Sound event pitch.min must not exceed pitch.max");
            }
        } else {
            min = number(pitch, 1, 0.01f, 4, "pitch");
            max = min;
        }
        return new SoundEventDefinition(key, clips, number(table.get("volume"), 1, 0, 1, "volume"), min, max,
                number(table.get("range"), 16, 0.01f, 1024, "range"));
    }

    static SoundEventPatch readPatch(LuaValue value) {
        LuaTable table = value.checktable();
        checkFields(table, "sound", "clips", "volume", "pitch", "range", "enabled");
        LuaValue single = table.get("sound");
        LuaValue alternatives = table.get("clips");
        if (!single.isnil() && !alternatives.isnil()) {
            throw new LuaError("Sound event override accepts only one of sound or clips");
        }
        List<SoundEventDefinition.Clip> clips = null;
        if (!single.isnil()) {
            clips = new ArrayList<SoundEventDefinition.Clip>();
            clips.add(new SoundEventDefinition.Clip(AssetInputs.sound(single), 1));
        } else if (!alternatives.isnil()) {
            clips = readClips(alternatives);
        }
        Float min = null;
        Float max = null;
        LuaValue pitch = table.get("pitch");
        if (!pitch.isnil()) {
            if (pitch.istable()) {
                checkFields(pitch.checktable(), "min", "max");
                min = Float.valueOf(number(pitch.get("min"), 1, 0.01f, 4, "pitch.min"));
                max = Float.valueOf(number(pitch.get("max"), 1, 0.01f, 4, "pitch.max"));
            } else {
                min = Float.valueOf(number(pitch, 1, 0.01f, 4, "pitch"));
                max = min;
            }
            if (min.floatValue() > max.floatValue()) {
                throw new LuaError("Sound event pitch.min must not exceed pitch.max");
            }
        }
        return new SoundEventPatch(clips,
                table.get("volume").isnil() ? null
                        : Float.valueOf(number(table.get("volume"), 1, 0, 1, "volume")),
                min, max,
                table.get("range").isnil() ? null
                        : Float.valueOf(number(table.get("range"), 16, 0.01f, 1024, "range")),
                table.get("enabled").isnil() ? null : Boolean.valueOf(table.get("enabled").checkboolean()));
    }

    private static List<SoundEventDefinition.Clip> readClips(LuaValue value) {
        LuaTable list = value.checktable();
        if (list.length() < 1 || list.length() > 256 || list.keys().length != list.length()) {
            throw new LuaError("Sound event clips must be a dense list of 1 to 256 entries");
        }
        List<SoundEventDefinition.Clip> clips = new ArrayList<SoundEventDefinition.Clip>();
        for (int i = 1; i <= list.length(); i++) {
            LuaTable clip = list.get(i).checktable();
            checkFields(clip, "sound", "weight");
            clips.add(new SoundEventDefinition.Clip(AssetInputs.sound(clip.get("sound")),
                    number(clip.get("weight"), 1, 0.000001f, 1000000, "weight")));
        }
        return clips;
    }

    static float number(LuaValue value, float fallback, float min, float max, String name) {
        double number = value.isnil() ? fallback : value.checkdouble();
        if (!Double.isFinite(number) || number < min || number > max) {
            throw new LuaError("Sound " + name + " must be between " + min + " and " + max);
        }
        return (float) number;
    }

    static void checkFields(LuaTable table, String... allowed) {
        for (LuaValue field : table.keys()) {
            boolean found = false;
            for (String name : allowed) {
                if (field.eq_b(LuaValue.valueOf(name))) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new LuaError("Unknown sound field: " + field.tojstring());
            }
        }
    }
}

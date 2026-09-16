package betamoon.assets.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import static betamoon.assets.model.ModelJson.bool;
import static betamoon.assets.model.ModelJson.fields;
import static betamoon.assets.model.ModelJson.name;
import static betamoon.assets.model.ModelJson.number;
import static betamoon.assets.model.ModelJson.object;

/**
 * Numeric Bedrock/GeckoLib-style clips; unsupported expression/effect fields
 * fail explicitly.
 */
public final class BedrockAnimations {
    private int keyCount;

    private BedrockAnimations() {
    }

    public static ModelAnimations decode(byte[] bytes) throws IOException {
        return new BedrockAnimations().read(ModelJson.read(bytes));
    }

    private ModelAnimations read(Map<String, Object> root) throws IOException {
        fields(root, "animation", "format_version", "animations");
        if (!"1.8.0".equals(root.get("format_version"))) {
            throw new IOException("animation.format_version: supported export version is 1.8.0");
        }
        Map<String, Object> definitions = object(root.get("animations"), "animations");
        if (definitions.isEmpty() || definitions.size() > 256) {
            throw new IOException("Animation file requires 1-256 clips");
        }
        Map<String, ModelAnimations.Clip> clips = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : definitions.entrySet()) {
            String path = "animations." + entry.getKey();
            name(entry.getKey(), "animation name");
            Map<String, Object> data = object(entry.getValue(), path);
            fields(data, path, "loop", "animation_length", "bones");
            String loop = "once";
            if (data.containsKey("loop")) {
                Object value = data.get("loop");
                loop = "hold_on_last_frame".equals(value) ? "hold" : bool(value, path + ".loop") ? "loop" : "once";
            }
            Map<String, ModelAnimations.Channels> bones = new LinkedHashMap<>();
            double duration = 0;
            Map<String, Object> sourceBones = data.containsKey("bones")
                    ? object(data.get("bones"), path + ".bones")
                    : new LinkedHashMap<>();
            if (sourceBones.size() > 128) {
                throw new IOException(path + ": exceeds 128 bone tracks");
            }
            for (Map.Entry<String, Object> bone : sourceBones.entrySet()) {
                String bonePath = path + ".bones." + bone.getKey();
                Map<String, Object> channels = object(bone.getValue(), bonePath);
                fields(channels, bonePath, "position", "rotation", "scale");
                AnimationTrack position = track(channels.get("position"), bonePath + ".position", false);
                AnimationTrack rotation = track(channels.get("rotation"), bonePath + ".rotation", false);
                AnimationTrack scale = track(channels.get("scale"), bonePath + ".scale", true);
                for (AnimationTrack track : Arrays.asList(position, rotation, scale)) {
                    if (track != null) {
                        duration = Math.max(duration, track.duration());
                    }
                }
                bones.put(bone.getKey(), new ModelAnimations.Channels(position, rotation, scale));
            }
            if (data.containsKey("animation_length")) {
                double declared = number(data.get("animation_length"), path + ".animation_length");
                if (declared < duration || declared < 0 || declared > 3600) {
                    throw new IOException(path + ": animation_length must cover all keys and be within 0-3600 seconds");
                }
                duration = declared;
            }
            clips.put(entry.getKey(), new ModelAnimations.Clip(duration, loop, bones));
        }
        return new ModelAnimations(clips);
    }

    private AnimationTrack track(Object source, String path, boolean scale) throws IOException {
        if (source == null) {
            return null;
        }
        TreeMap<Double, AnimationTrack.Key> keys = new TreeMap<>();
        if (!(source instanceof Map) || ((Map<?, ?>) source).containsKey("post")
                || ((Map<?, ?>) source).containsKey("pre")) {
            keys.put(0.0, key(source, 0, path, scale));
        } else {
            for (Map.Entry<String, Object> entry : object(source, path).entrySet()) {
                double time;
                try {
                    if (!entry.getKey().matches("[0-9]+(?:\\.[0-9]+)?")) {
                        throw new NumberFormatException();
                    }
                    time = Double.parseDouble(entry.getKey());
                } catch (NumberFormatException error) {
                    throw new IOException(path + ": invalid keyframe time " + entry.getKey());
                }
                if (!Double.isFinite(time) || time > 3600
                        || keys.put(time, key(entry.getValue(), time, path + "." + entry.getKey(), scale)) != null) {
                    throw new IOException(path + ": duplicate or out-of-range timestamp " + entry.getKey());
                }
            }
        }
        if (keys.isEmpty()) {
            throw new IOException(path + ": empty animation channel");
        }
        return new AnimationTrack(new ArrayList<>(keys.values()));
    }

    private AnimationTrack.Key key(Object source, double time, String path, boolean scale) throws IOException {
        if (++keyCount > 20000) {
            throw new IOException("Animation file exceeds 20000 keys");
        }
        String interpolation = "linear";
        Object before = source;
        Object after = source;
        if (source instanceof Map) {
            Map<String, Object> data = object(source, path);
            fields(data, path, "pre", "post", "lerp_mode", "easing");
            before = data.containsKey("pre") ? data.get("pre") : data.get("post");
            after = data.containsKey("post") ? data.get("post") : before;
            if (data.containsKey("lerp_mode")) {
                interpolation = name(data.get("lerp_mode"), path + ".lerp_mode");
            }
            if (data.containsKey("easing") && !"linear".equals(data.get("easing"))) {
                throw new IOException(path + ".easing: only linear GeckoLib easing is supported");
            }
        }
        if (!Arrays.asList("linear", "step", "catmullrom").contains(interpolation)) {
            throw new IOException(path + ": unsupported interpolation " + interpolation);
        }
        ModelVector pre = vector(before, path, scale);
        ModelVector post = vector(after, path, scale);
        return new AnimationTrack.Key(time, pre, post, interpolation);
    }

    private static ModelVector vector(Object value, String path, boolean scale) throws IOException {
        ModelVector result;
        if (scale && value instanceof Number) {
            result = ModelVector.ONE.times(number(value, path));
        } else {
            result = ModelVector.read(value, null, path);
        }
        if (scale && (result.x < 0 || result.y < 0 || result.z < 0)) {
            throw new IOException(path + ": scale must be nonnegative");
        }
        return result;
    }
}

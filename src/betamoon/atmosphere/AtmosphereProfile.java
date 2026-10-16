package betamoon.atmosphere;

import betamoon.assets.AssetKey;
import betamoon.worldgen.spatial.GenerationValues;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static betamoon.worldgen.spatial.GenerationValues.error;
import static betamoon.worldgen.spatial.GenerationValues.fields;
import static betamoon.worldgen.spatial.GenerationValues.integer;
import static betamoon.worldgen.spatial.GenerationValues.list;
import static betamoon.worldgen.spatial.GenerationValues.number;
import static betamoon.worldgen.spatial.GenerationValues.object;
import static betamoon.worldgen.spatial.GenerationValues.required;
import static betamoon.worldgen.spatial.GenerationValues.string;

/**
 * Immutable client presentation values, independent of biome identity and world
 * light.
 */
public final class AtmosphereProfile {
    public final String key;
    public final boolean hasFog;
    public final float[] fogColor;
    public final float near;
    public final float far;
    public final float density;
    public final float[] sky;
    public final float[] lightTone;
    public final Sound ambient;
    public final Sound weather;

    public AtmosphereProfile(Map<String, Object> value) {
        key = string(required(value, "key", "atmosphere"), "atmosphere.key");
        AssetKey parsed = AssetKey.parse(key);
        if (!parsed.toString().equals(key) || key.startsWith("minecraft:") || key.startsWith("betamoon:")) {
            throw error(key, "expected an owned atmosphere key");
        }
        fields(value, key, "key", "fog", "sky", "lightTone", "ambientSound", "weatherSound");
        Map<String, Object> fog = value.containsKey("fog") ? object(value.get("fog"), key + ".fog") : null;
        hasFog = fog != null;
        if (fog == null) {
            fogColor = null;
            near = 1;
            far = 128;
            density = 0;
        } else {
            fields(fog, key + ".fog", "color", "near", "far", "density");
            fogColor = fog.containsKey("color") ? color(fog.get("color"), key + ".fog.color") : null;
            near = (float) number(fog, "near", 1, key);
            far = (float) number(fog, "far", 128, key);
            density = (float) number(fog, "density", 0, key);
            if (near < 1 || far > 256 || near >= far || density < 0 || density > 1) {
                throw error(key + ".fog", "expected 1 <= near < far <= 256 and density in 0..1");
            }
        }
        if (value.containsKey("sky")) {
            fields(object(value.get("sky"), key + ".sky"), key + ".sky", "color");
        }
        sky = value.containsKey("sky")
                ? color(object(value.get("sky"), key + ".sky").get("color"), key + ".sky.color")
                : null;
        lightTone = value.containsKey("lightTone") ? color(value.get("lightTone"), key + ".lightTone") : null;
        ambient = value.containsKey("ambientSound")
                ? new Sound(value.get("ambientSound"), key + ".ambientSound")
                : null;
        weather = value.containsKey("weatherSound")
                ? new Sound(value.get("weatherSound"), key + ".weatherSound")
                : null;
    }

    public static float[] color(Object value, String path) {
        if (value instanceof Number) {
            int rgb = integer(value, path, 0, 0xffffff);
            return new float[]{((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 1};
        }
        List<Object> components = list(value, path, 3, 4);
        float[] result = {0, 0, 0, 1};
        for (int i = 0; i < components.size(); i++) {
            double component = number(components.get(i), path);
            if (component < 0 || component > 1) {
                throw error(path, "normalized color components must be in 0..1");
            }
            result[i] = (float) component;
        }
        return result;
    }

    public static final class Sound {
        public final AssetKey key;
        public final float volume;
        public final int interval;
        public final int fade;

        private Sound(Object value, String path) {
            Map<String, Object> object = value instanceof String
                    ? Collections.<String, Object>singletonMap("event", value)
                    : GenerationValues.object(value, path);
            fields(object, path, "event", "volume", "loopIntervalTicks", "fadeTicks");
            key = AssetKey.parse(string(required(object, "event", path), path + ".event"));
            volume = (float) number(object, "volume", 1, path);
            interval = integer(object.getOrDefault("loopIntervalTicks", 200.0), path + ".loopIntervalTicks", 1, 24000);
            fade = integer(object.getOrDefault("fadeTicks", 20.0), path + ".fadeTicks", 0, 1200);
            if (volume < 0 || volume > 4) {
                throw error(path + ".volume", "expected 0..4");
            }
        }
    }
}

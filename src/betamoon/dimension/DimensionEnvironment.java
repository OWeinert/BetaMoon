package betamoon.dimension;

import betamoon.worldgen.spatial.GenerationValues;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Explicit world rules; choosing a generator family does not silently select
 * Nether behavior.
 */
public final class DimensionEnvironment {
    public final String preset;
    public final boolean skylight;
    public final boolean sky;
    public final boolean clouds;
    public final float cloudHeight;
    public final boolean fixedCelestial;
    public final float celestialAngle;
    public final float ambientLight;
    public final String weather;
    public final String beds;
    public final boolean waterEvaporates;
    public final String lavaFlow;
    public final String compass;
    public final String clock;

    public DimensionEnvironment(Map<String, Object> value, String path) {
        GenerationValues.fields(value, path, "preset", "skylight", "sky", "clouds", "cloudHeight", "celestial",
                "ambientLight", "weather", "beds", "waterEvaporates", "lavaFlow", "compass", "clock");
        preset = choice(value, "preset", "surface", path, "surface", "nether");
        boolean nether = preset.equals("nether");
        skylight = GenerationValues.bool(value, "skylight", !nether, path);
        sky = GenerationValues.bool(value, "sky", !nether, path);
        clouds = GenerationValues.bool(value, "clouds", !nether, path);
        cloudHeight = (float) bounded(value, "cloudHeight", 108, 0, 256, path);
        ambientLight = (float) bounded(value, "ambientLight", nether ? 0.1 : 0.05, 0, 1, path);
        Map<String, Object> celestial = value.containsKey("celestial")
                ? GenerationValues.object(value.get("celestial"), path + ".celestial")
                : Collections.<String, Object>emptyMap();
        GenerationValues.fields(celestial, path + ".celestial", "type", "angle");
        fixedCelestial = choice(celestial, "type", nether ? "fixed" : "cycle", path + ".celestial", "cycle", "fixed")
                .equals("fixed");
        if (!fixedCelestial && celestial.containsKey("angle")) {
            throw GenerationValues.error(path + ".celestial.angle", "angle requires fixed celestial mode");
        }
        celestialAngle = (float) bounded(celestial, "angle", 0.5, 0, 1, path + ".celestial");
        weather = choice(value, "weather", nether ? "disabled" : "normal", path, "normal", "disabled");
        beds = choice(value, "beds", nether ? "explode" : "sleep", path, "sleep", "explode", "disabled");
        waterEvaporates = GenerationValues.bool(value, "waterEvaporates", nether, path);
        lavaFlow = choice(value, "lavaFlow", nether ? "nether" : "normal", path, "normal", "nether");
        compass = choice(value, "compass", nether ? "random" : "spawn", path, "spawn", "random");
        clock = choice(value, "clock", nether ? "random" : "normal", path, "normal", "random");
    }

    public Map<String, Object> describe() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("skylight", skylight);
        result.put("sky", sky);
        result.put("clouds", clouds);
        result.put("cloudHeight", cloudHeight);
        Map<String, Object> celestial = new LinkedHashMap<String, Object>();
        celestial.put("type", fixedCelestial ? "fixed" : "cycle");
        if (fixedCelestial) {
            celestial.put("angle", celestialAngle);
        }
        result.put("celestial", celestial);
        result.put("ambientLight", ambientLight);
        result.put("weather", weather);
        result.put("beds", beds);
        result.put("waterEvaporates", waterEvaporates);
        result.put("lavaFlow", lavaFlow);
        result.put("compass", compass);
        result.put("clock", clock);
        return result;
    }

    public static double bounded(Map<String, Object> value, String field, double fallback, double minimum,
            double maximum, String path) {
        double number = GenerationValues.number(value, field, fallback, path);
        if (number < minimum || number > maximum) {
            throw GenerationValues.error(path + "." + field, "expected " + minimum + ".." + maximum);
        }
        return number;
    }

    public static String choice(Map<String, Object> value, String field, String fallback, String path,
            String... choices) {
        String selected = GenerationValues.string(value, field, fallback, path);
        for (String choice : choices) {
            if (selected.equals(choice)) {
                return selected;
            }
        }
        throw GenerationValues.error(path + "." + field, "unsupported value: " + selected);
    }
}

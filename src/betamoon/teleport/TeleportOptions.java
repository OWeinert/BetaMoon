package betamoon.teleport;

import betamoon.dimension.DimensionEnvironment;
import betamoon.dimension.DimensionKey;
import betamoon.worldgen.spatial.GenerationValues;
import java.util.Collections;
import java.util.Map;

/**
 * Strict structural options. No callback, scoped access or mutable declaration
 * is retained.
 */
public final class TeleportOptions {
    public final String arrivalType;
    public final double x;
    public final double y;
    public final double z;
    public final Float yaw;
    public final Float pitch;
    public final Boolean rememberReturn;
    public final String channel;
    public final String safety;
    public final int searchRadius;
    public final int verticalRange;
    public final boolean loadChunks;
    public final int maxChunks;
    public final int timeoutTicks;
    public final String velocity;
    public final double motionX;
    public final double motionY;
    public final double motionZ;
    public final boolean preserveFallDistance;
    public final boolean dismount;
    public final int cooldownTicks;
    public final TeleportPlatformDefinition platform;
    public final boolean returnFallback;

    public TeleportOptions(Map<String, Object> value, boolean returning) {
        String path = "teleport.options";
        GenerationValues.fields(value, path, "arrival", "rememberReturn", "channel", "safety", "searchRadius",
                "verticalRange", "loadChunks", "maxChunks", "timeoutTicks", "velocity", "fallDistance", "dismount",
                "cooldownTicks", "platform", "yaw", "pitch", "fallback");
        if (returning
                ? value.containsKey("arrival") || value.containsKey("rememberReturn")
                : value.containsKey("yaw") || value.containsKey("pitch") || value.containsKey("fallback")) {
            throw GenerationValues.error(path, "options conflict with request/return mode");
        }
        Map<String, Object> arrival = value.containsKey("arrival")
                ? GenerationValues.object(value.get("arrival"), path + ".arrival")
                : Collections.<String, Object>emptyMap();
        GenerationValues.fields(arrival, path + ".arrival", "type", "x", "y", "z", "yaw", "pitch");
        arrivalType = returning
                ? "near"
                : DimensionEnvironment.choice(arrival, "type", "spawn", path + ".arrival", "spawn", "exact", "near",
                        "scaled");
        boolean coordinates = arrivalType.equals("exact") || arrivalType.equals("near");
        if (!returning && coordinates) {
            GenerationValues.required(arrival, "x", path + ".arrival");
            GenerationValues.required(arrival, "y", path + ".arrival");
            GenerationValues.required(arrival, "z", path + ".arrival");
        } else if (!returning && (arrival.containsKey("x") || arrival.containsKey("y") || arrival.containsKey("z"))) {
            throw GenerationValues.error(path + ".arrival", "spawn/scaled arrivals resolve their own coordinates");
        }
        x = DimensionEnvironment.bounded(arrival, "x", 0, -29999999.999, 29999999.999, path + ".arrival");
        y = DimensionEnvironment.bounded(arrival, "y", 65, -64, 4096, path + ".arrival");
        z = DimensionEnvironment.bounded(arrival, "z", 0, -29999999.999, 29999999.999, path + ".arrival");
        Map<String, Object> orientation = returning ? value : arrival;
        yaw = orientation.containsKey("yaw")
                ? TeleportLocation
                        .normalizeYaw(DimensionEnvironment.bounded(orientation, "yaw", 0, -1.0e12, 1.0e12, path))
                : null;
        pitch = orientation.containsKey("pitch")
                ? (float) DimensionEnvironment.bounded(orientation, "pitch", 0, -90, 90, path)
                : null;
        rememberReturn = value.containsKey("rememberReturn")
                ? GenerationValues.bool(value, "rememberReturn", true, path)
                : null;
        channel = GenerationValues.string(value, "channel", "default", path);
        if (!channel.matches("[a-zA-Z0-9_.:-]{1,64}")) {
            throw GenerationValues.error(path + ".channel", "expected 1..64 identifier characters");
        }
        safety = DimensionEnvironment.choice(value, "safety", "safe", path, "safe", "collision", "unchecked");
        searchRadius = integer(value, "searchRadius", 16, 0, 64, path);
        verticalRange = integer(value, "verticalRange", 32, 0, 127, path);
        loadChunks = GenerationValues.bool(value, "loadChunks", true, path);
        maxChunks = integer(value, "maxChunks", 25, 1, 81, path);
        timeoutTicks = integer(value, "timeoutTicks", 200, 20, 1200, path);
        Object movement = value.get("velocity");
        if (movement instanceof Map) {
            Map<String, Object> motion = GenerationValues.object(movement, path + ".velocity");
            GenerationValues.fields(motion, path + ".velocity", "x", "y", "z");
            GenerationValues.required(motion, "x", path);
            GenerationValues.required(motion, "y", path);
            GenerationValues.required(motion, "z", path);
            velocity = "explicit";
            motionX = DimensionEnvironment.bounded(motion, "x", 0, -10, 10, path + ".velocity");
            motionY = DimensionEnvironment.bounded(motion, "y", 0, -10, 10, path + ".velocity");
            motionZ = DimensionEnvironment.bounded(motion, "z", 0, -10, 10, path + ".velocity");
        } else {
            velocity = DimensionEnvironment.choice(value, "velocity", "reset", path, "reset", "preserve");
            motionX = 0;
            motionY = 0;
            motionZ = 0;
        }
        preserveFallDistance = DimensionEnvironment.choice(value, "fallDistance", "reset", path, "reset", "preserve")
                .equals("preserve");
        dismount = GenerationValues.bool(value, "dismount", false, path);
        cooldownTicks = integer(value, "cooldownTicks", 10, 0, 1200, path);
        platform = value.containsKey("platform")
                ? new TeleportPlatformDefinition(GenerationValues.object(value.get("platform"), path + ".platform"),
                        path + ".platform")
                : null;
        if (safety.equals("unchecked") && (!arrivalType.equals("exact") || platform != null)) {
            throw GenerationValues.error(path, "unchecked safety requires exact arrival and no platform");
        }
        returnFallback = returning && DimensionEnvironment
                .choice(value, "fallback", "fail", path, "fail", "dimension_spawn").equals("dimension_spawn");
    }

    public TeleportLocation at(DimensionKey key, double x, double y, double z, TeleportLocation orientation) {
        return new TeleportLocation(key, x, y, z, yaw == null ? orientation.yaw : yaw,
                pitch == null ? orientation.pitch : pitch);
    }

    private static int integer(Map<String, Object> value, String key, int fallback, int min, int max, String path) {
        return value.containsKey(key) ? GenerationValues.integer(value.get(key), path + "." + key, min, max) : fallback;
    }
}

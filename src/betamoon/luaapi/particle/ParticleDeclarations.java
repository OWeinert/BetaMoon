package betamoon.luaapi.particle;

import betamoon.client.assets.AssetLocation;
import betamoon.content.ContentKey;
import betamoon.content.ContentType;
import betamoon.luaapi.asset.AssetReference;
import betamoon.luaapi.asset.TextureInputs;
import betamoon.luaapi.utils.LuaContentNamespaces;
import betamoon.luamodloader.ScriptAssetScope;
import betamoon.particles.ParticleDefinition;
import betamoon.particles.ParticleEffectDefinition;
import betamoon.particles.ParticleEffectDefinition.Emitter;
import betamoon.particles.ParticleEffectDefinition.Range;
import betamoon.particles.ParticleRegistry;
import java.util.ArrayList;
import java.util.List;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import static betamoon.luaapi.utils.LuaDeclarationValues.bool;
import static betamoon.luaapi.utils.LuaDeclarationValues.error;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;
import static betamoon.luaapi.utils.LuaDeclarationValues.length;
import static betamoon.luaapi.utils.LuaDeclarationValues.number;
import static betamoon.luaapi.utils.LuaDeclarationValues.required;
import static betamoon.luaapi.utils.LuaDeclarationValues.string;

/**
 * Strict Lua declaration parsing; publication and live particle state have
 * separate owners.
 */
public final class ParticleDeclarations {
    private ParticleDeclarations() {
    }

    public static ParticleDefinition particle(LuaValue value) {
        fields(value, "particle", "key", "texture", "frameStart", "lifetimeTicks", "size", "color", "motion", "facing",
                "blend", "light", "rotation", "onTick");
        String owner = ScriptAssetScope.currentOwner();
        ContentKey key = key(value.get("key"), ContentType.PARTICLE, true);
        LuaValue art = required(value, "texture");
        AssetLocation texture = null;
        String atlas = null;
        int[] indices = null;
        int frameTicks = 1;
        if (art.istable() && !(art instanceof AssetReference) && !art.get("atlas").isnil()) {
            fields(art, "particle.texture", "atlas", "index", "indices", "frameTicks");
            String name = choice(art.get("atlas"), "particle.texture.atlas", "minecraft:particles",
                    "minecraft:particles", "minecraft:terrain", "minecraft:items");
            atlas = "minecraft:particles".equals(name)
                    ? "/particles.png"
                    : "minecraft:terrain".equals(name) ? "/terrain.png" : "/gui/items.png";
            if (!art.get("index").isnil() && !art.get("indices").isnil()) {
                throw error("particle.texture", "index and indices cannot be combined");
            }
            if (art.get("indices").isnil()) {
                indices = new int[]{integer(required(art, "index"), "particle.texture.index", 0, 255)};
            } else {
                LuaValue array = art.get("indices");
                int count = length(array, "particle.texture.indices");
                if (count < 1 || count > 256) {
                    throw error("particle.texture.indices", "expected 1..256 cells");
                }
                indices = new int[count];
                for (int i = 0; i < count; i++) {
                    indices[i] = integer(array.get(i + 1), "particle.texture.indices[" + (i + 1) + "]", 0, 255);
                }
            }
            frameTicks = optionalInt(art.get("frameTicks"), 1, "particle.texture.frameTicks", 1, 1200);
        } else {
            texture = TextureInputs.read(art);
        }
        Range lifetime = range(value.get("lifetimeTicks"), 20, "particle.lifetimeTicks", 1, 600);
        if (lifetime.min != Math.floor(lifetime.min) || lifetime.max != Math.floor(lifetime.max)) {
            throw error("particle.lifetimeTicks", "expected integer ticks");
        }
        double[] size = endpoints(value.get("size"), 0.2, "particle.size", 0, 4);
        if (size[0] <= 0) {
            throw error("particle.size.start", "must be positive");
        }
        LuaValue color = value.get("color");
        double[] startColor;
        double[] finishColor;
        if (color.istable() && !color.get("start").isnil()) {
            fields(color, "particle.color", "start", "finish");
            startColor = color(color.get("start"), "particle.color.start");
            finishColor = color(required(color, "finish"), "particle.color.finish");
        } else {
            startColor = color(color, "particle.color");
            finishColor = startColor;
        }
        LuaValue motion = value.get("motion");
        double gravity = 0;
        double drag = 0.98;
        String collision = "none";
        double bounce = 0;
        if (!motion.isnil()) {
            fields(motion, "particle.motion", "gravity", "drag", "collision", "bounce");
            gravity = bounded(motion.get("gravity"), 0, "particle.motion.gravity", -1, 1);
            drag = bounded(motion.get("drag"), 0.98, "particle.motion.drag", 0, 1);
            collision = choice(motion.get("collision"), "particle.motion.collision", "none", "none", "stop", "bounce",
                    "die");
            if ("bounce".equals(collision)) {
                bounce = bounded(required(motion, "bounce"), 0, "particle.motion.bounce", 0, 1);
            } else if (!motion.get("bounce").isnil()) {
                throw error("particle.motion.bounce", "requires bounce collision");
            }
        }
        double rotationStart = 0;
        double rotationSpeed = 0;
        LuaValue rotation = value.get("rotation");
        if (!rotation.isnil()) {
            fields(rotation, "particle.rotation", "start", "degreesPerTick");
            rotationStart = bounded(rotation.get("start"), 0, "particle.rotation.start", -1000000, 1000000);
            rotationSpeed = bounded(rotation.get("degreesPerTick"), 0, "particle.rotation.degreesPerTick", -1000000,
                    1000000);
        }
        LuaValue callback = value.get("onTick");
        if (!callback.isnil() && !callback.isfunction()) {
            throw error("particle.onTick", "expected a function");
        }
        return new ParticleDefinition(key, owner, texture, atlas, indices, frameTicks,
                "random".equals(choice(value.get("frameStart"), "particle.frameStart", "first", "first", "random")),
                (int) lifetime.min, (int) lifetime.max, size[0], size[1], startColor, finishColor, gravity, drag,
                collision, bounce,
                "vertical".equals(choice(value.get("facing"), "particle.facing", "camera", "camera", "vertical")),
                "additive".equals(choice(value.get("blend"), "particle.blend", "alpha", "alpha", "additive")),
                "full".equals(choice(value.get("light"), "particle.light", "world", "world", "full")), rotationStart,
                rotationSpeed, callback);
    }

    public static ParticleEffectDefinition effect(LuaValue value) {
        fields(value, "particleEffect", "key", "emitters", "viewDistance", "importance");
        String owner = ScriptAssetScope.currentOwner();
        ContentKey key = key(value.get("key"), ContentType.PARTICLE_EFFECT, true);
        LuaValue entries = required(value, "emitters");
        int count = length(entries, "particleEffect.emitters");
        if (count < 1 || count > 8) {
            throw error("particleEffect.emitters", "expected 1..8 emitters");
        }
        List<Emitter> emitters = new ArrayList<Emitter>();
        int maximum = 0;
        for (int i = 1; i <= count; i++) {
            Emitter emitter = emitter(entries.get(i), "particleEffect.emitters[" + i + "]");
            emitters.add(emitter);
            maximum += emitter.burst + (int) Math.ceil(emitter.rate);
        }
        if (maximum > 256) {
            throw error("particleEffect.emitters", "combined worst-case emissions exceed 256 per tick");
        }
        return new ParticleEffectDefinition(key, owner, emitters,
                bounded(value.get("viewDistance"), 16, "particleEffect.viewDistance", 1, 64),
                "decorative".equals(choice(value.get("importance"), "particleEffect.importance", "normal", "decorative",
                        "normal")));
    }

    private static Emitter emitter(LuaValue value, String path) {
        fields(value, path, "particle", "shape", "burstCount", "ratePerTick", "durationTicks", "velocity", "scale",
                "color", "seedOffset");
        ParticleDefinition particle = ParticleRegistry.particle(referenceKey(required(value, "particle"), false));
        if (particle == null) {
            throw error(path + ".particle", "particle type is not registered");
        }
        LuaValue shape = required(value, "shape");
        fields(shape, path + ".shape", "type", "radius", "halfExtents", "from", "to", "surface");
        String type = choice(shape.get("type"), path + ".shape.type", "point", "point", "box", "sphere", "line");
        boolean surface = bool(shape.get("surface"), path + ".shape.surface", false);
        double[] dimensions = new double[6];
        if ("sphere".equals(type)) {
            dimensions[0] = positive(required(shape, "radius"), path + ".shape.radius", 64);
        } else if ("box".equals(type)) {
            vector(required(shape, "halfExtents"), path + ".shape.halfExtents", dimensions, 0, true);
        } else if ("line".equals(type)) {
            vector(required(shape, "from"), path + ".shape.from", dimensions, 0, false);
            vector(required(shape, "to"), path + ".shape.to", dimensions, 3, false);
        }
        if (surface && !"sphere".equals(type) && !"box".equals(type)) {
            throw error(path + ".shape.surface", "requires sphere or box");
        }
        requireShapeFields(shape, type, path);
        int burst = optionalInt(value.get("burstCount"), 0, path + ".burstCount", 0, 256);
        double rate = bounded(value.get("ratePerTick"), 0, path + ".ratePerTick", 0, 64);
        int duration = rate == 0 ? 0 : integer(required(value, "durationTicks"), path + ".durationTicks", 1, 1200);
        if (rate == 0 && !value.get("durationTicks").isnil()) {
            throw error(path + ".durationTicks", "requires a positive ratePerTick");
        }
        if (burst == 0 && rate == 0) {
            throw error(path, "requires a burst or a positive rate");
        }
        LuaValue velocity = value.get("velocity");
        Range vx = new Range(0, 0);
        Range vy = vx;
        Range vz = vx;
        Range outward = null;
        if (!velocity.isnil()) {
            fields(velocity, path + ".velocity", "x", "y", "z", "outward");
            vx = range(velocity.get("x"), 0, path + ".velocity.x", -16, 16);
            vy = range(velocity.get("y"), 0, path + ".velocity.y", -16, 16);
            vz = range(velocity.get("z"), 0, path + ".velocity.z", -16, 16);
            if (!velocity.get("outward").isnil()) {
                if (!velocity.get("x").isnil() || !velocity.get("z").isnil()) {
                    throw error(path + ".velocity", "outward cannot be combined with x or z");
                }
                outward = range(velocity.get("outward"), 0, path + ".velocity.outward", 0, 16);
            }
        }
        Range scale = range(value.get("scale"), 1, path + ".scale", 0, 4);
        if (scale.min <= 0) {
            throw error(path + ".scale", "must be positive");
        }
        return new Emitter(particle, type, surface, dimensions, burst, rate, duration, vx, vy, vz, outward, scale,
                color(value.get("color"), path + ".color"),
                optionalInt(value.get("seedOffset"), 0, path + ".seedOffset", Integer.MIN_VALUE, Integer.MAX_VALUE));
    }

    private static void requireShapeFields(LuaValue shape, String type, String path) {
        for (String field : new String[]{"radius", "halfExtents", "from", "to"}) {
            boolean allowed = "radius".equals(field) && "sphere".equals(type)
                    || "halfExtents".equals(field) && "box".equals(type)
                    || ("from".equals(field) || "to".equals(field)) && "line".equals(type);
            if (!allowed && !shape.get(field).isnil()) {
                throw error(path + ".shape." + field, "unsupported for " + type);
            }
        }
    }

    public static ContentKey referenceKey(LuaValue value, boolean effect) {
        if (value instanceof ParticleReference) {
            ParticleReference reference = (ParticleReference) value;
            reference.requireCurrent(effect);
            return reference.key();
        }
        return key(value, effect ? ContentType.PARTICLE_EFFECT : ContentType.PARTICLE, false);
    }

    public static ContentKey key(LuaValue value, ContentType type, boolean declaration) {
        try {
            ContentKey key = ContentKey.parseForType(string(value, "particle.key"), type);
            if (declaration) {
                LuaContentNamespaces.requireDeclarationNamespace(key);
            }
            return key;
        } catch (IllegalArgumentException exception) {
            throw new LuaError("Particle key: " + exception.getMessage());
        }
    }

    public static double bounded(LuaValue value, double fallback, String path, double min, double max) {
        double result = value.isnil() ? fallback : number(value, path);
        if (result < min || result > max) {
            throw error(path, "expected " + min + ".." + max);
        }
        return result;
    }

    public static double positive(LuaValue value, String path, double max) {
        double result = bounded(value, 0, path, 0, max);
        if (result <= 0) {
            throw error(path, "must be positive");
        }
        return result;
    }

    private static int optionalInt(LuaValue value, int fallback, String path, int min, int max) {
        return value.isnil() ? fallback : integer(value, path, min, max);
    }

    public static Range range(LuaValue value, double fallback, String path, double min, double max) {
        if (!value.istable()) {
            double number = bounded(value, fallback, path, min, max);
            return new Range(number, number);
        }
        fields(value, path, "min", "max");
        double low = bounded(required(value, "min"), fallback, path + ".min", min, max);
        double high = bounded(required(value, "max"), fallback, path + ".max", min, max);
        if (low > high) {
            throw error(path, "min exceeds max");
        }
        return new Range(low, high);
    }

    private static double[] endpoints(LuaValue value, double fallback, String path, double min, double max) {
        if (!value.istable()) {
            double number = bounded(value, fallback, path, min, max);
            return new double[]{number, number};
        }
        fields(value, path, "start", "finish");
        return new double[]{bounded(required(value, "start"), fallback, path + ".start", min, max),
                bounded(required(value, "finish"), fallback, path + ".finish", min, max)};
    }

    public static double[] color(LuaValue value, String path) {
        if (value.isnil()) {
            return new double[]{1, 1, 1, 1};
        }
        fields(value, path, "r", "g", "b", "a");
        return new double[]{bounded(required(value, "r"), 1, path + ".r", 0, 1),
                bounded(required(value, "g"), 1, path + ".g", 0, 1),
                bounded(required(value, "b"), 1, path + ".b", 0, 1), bounded(value.get("a"), 1, path + ".a", 0, 1)};
    }

    private static void vector(LuaValue value, String path, double[] output, int offset, boolean positive) {
        fields(value, path, "x", "y", "z");
        String[] axes = {"x", "y", "z"};
        for (int axis = 0; axis < 3; axis++) {
            output[offset + axis] = positive
                    ? positive(required(value, axes[axis]), path + "." + axes[axis], 64)
                    : bounded(required(value, axes[axis]), 0, path + "." + axes[axis], -64, 64);
        }
    }

    private static String choice(LuaValue value, String path, String fallback, String... values) {
        String selected = value.isnil() ? fallback : string(value, path);
        for (String allowed : values) {
            if (allowed.equals(selected)) {
                return selected;
            }
        }
        throw error(path, "unsupported value '" + selected + "'");
    }
}

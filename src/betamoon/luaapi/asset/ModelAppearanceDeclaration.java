package betamoon.luaapi.asset;

import betamoon.assets.AssetKind;
import betamoon.assets.model.ModelRotation;
import betamoon.assets.model.ModelVector;
import betamoon.client.assets.AssetLocation;
import betamoon.luaapi.utils.LuaCallbackDeclarations;
import betamoon.luaapi.utils.LuaCallbackDispatcher;
import betamoon.luaapi.utils.LuaCallbackKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;

/**
 * Presentation parsing only; block and item registration keep their own
 * responsibilities.
 */
public final class ModelAppearanceDeclaration {
    /**
     * Nil means inherit at the caller; false explicitly requests ordinary
     * rendering.
     */
    public static ModelAppearanceDeclaration optional(LuaValue value) {
        return value.isnil() || value == LuaValue.FALSE ? null : new ModelAppearanceDeclaration(value);
    }

    public enum Callback implements LuaCallbackKey {
        POSE;
        public String luaName() {
            return "onPose";
        }
    }

    public final AssetLocation model;
    private final boolean forceDynamic;
    private final boolean procedural;
    public final AssetLocation animation;
    public final String clip;
    public final double speed;
    public final Map<String, Material> materials;
    public final Transform transform;
    public final Map<String, Transform> display;
    public final List<ModelAppearanceDeclaration> layers;
    public final LuaCallbackDispatcher<Callback> callbacks;

    public ModelAppearanceDeclaration(LuaValue value) {
        this(value, 0);
    }

    private ModelAppearanceDeclaration(LuaValue value, int depth) {
        fields(value, "appearance", "model", "texture", "material", "materials", "animation", "onPose", "position",
                "rotation", "scale", "display", "layers", "mode");
        forceDynamic = choice(value.get("mode"), "auto", "auto", "dynamic").equals("dynamic");
        model = AssetInputs.read(value.get("model"), AssetKind.MODEL);
        Map<String, Material> bindings = new LinkedHashMap<>();
        if (!value.get("texture").isnil() || !value.get("material").isnil()) {
            bindings.put("default", new Material(value.get("material"), value.get("texture")));
        }
        if (!value.get("materials").isnil()) {
            for (LuaValue name : value.get("materials").checktable().keys()) {
                bindings.put(name.checkjstring(), new Material(value.get("materials").get(name), LuaValue.NIL));
            }
        }
        materials = Collections.unmodifiableMap(bindings);
        LuaValue animated = value.get("animation");
        if (animated.isnil()) {
            animation = null;
            clip = null;
            speed = 1;
        } else {
            fields(animated, "appearance.animation", "asset", "clip", "speed");
            animation = AssetInputs.read(animated.get("asset"), AssetKind.ANIMATION);
            clip = animated.get("clip").checkjstring();
            speed = number(animated.get("speed"), 1, 0, 100, "animation.speed");
        }
        LuaTable callbackDefinition = new LuaTable();
        LuaValue poseCallback = value.get("onPose");
        procedural = !poseCallback.isnil();
        if (poseCallback.isfunction()) {
            LuaTable action = new LuaTable();
            action.set("action", poseCallback);
            callbackDefinition.set("onPose", action);
        } else {
            callbackDefinition.set("onPose", poseCallback);
        }
        callbacks = new LuaCallbackDispatcher<>(
                LuaCallbackDeclarations.builder(Callback.class, "model " + model.getCacheKey())
                        .parse(callbackDefinition, Callback.POSE).build());
        transform = new Transform(value);
        Map<String, Transform> contexts = new LinkedHashMap<>();
        LuaValue displays = value.get("display");
        if (!displays.isnil()) {
            fields(displays, "appearance.display", "gui", "held", "ground", "block");
            for (LuaValue name : displays.checktable().keys()) {
                LuaValue entry = displays.get(name);
                fields(entry, "appearance.display." + name, "position", "rotation", "scale");
                contexts.put(name.checkjstring(), new Transform(entry));
            }
        }
        display = Collections.unmodifiableMap(contexts);
        List<ModelAppearanceDeclaration> children = new ArrayList<>();
        if (!value.get("layers").isnil()) {
            LuaValue array = value.get("layers").checktable();
            if (depth > 0 || array.length() > 7 || array.checktable().keys().length != array.length()) {
                throw new LuaError("appearance.layers supports up to 7 flat, densely indexed layers");
            }
            for (int i = 1; i <= array.length(); i++) {
                children.add(new ModelAppearanceDeclaration(array.get(i), depth + 1));
            }
        }
        layers = Collections.unmodifiableList(children);
    }

    public boolean isDynamic() {
        if (forceDynamic || animation != null || procedural) {
            return true;
        }
        for (ModelAppearanceDeclaration layer : layers) {
            if (layer.isDynamic()) {
                return true;
            }
        }
        return false;
    }

    public static final class Transform {
        public final ModelVector position;
        public final ModelRotation rotation;
        public final ModelVector scale;

        Transform(LuaValue value) {
            position = value.get("position").isnil()
                    ? ModelVector.ZERO
                    : PoseReference.readVector(value.get("position"));
            rotation = value.get("rotation").isnil()
                    ? ModelRotation.IDENTITY
                    : ModelRotation.euler(PoseReference.readVector(value.get("rotation")));
            LuaValue size = value.get("scale");
            scale = size.istable()
                    ? PoseReference.readVector(size)
                    : ModelVector.ONE.times(number(size, 1, 0, 1024, "appearance.scale"));
            if (scale.x < 0 || scale.y < 0 || scale.z < 0) {
                throw new LuaError("appearance.scale cannot be negative");
            }
        }

        public ModelVector apply(ModelVector point) {
            return rotation.transform(point.multiply(scale)).add(position);
        }
    }

    public static final class Material {
        public final AssetLocation texture;
        public final String blend;
        public final boolean unlit;
        public final boolean cull;
        public final int tint;
        public final float opacity;
        public final double u;
        public final double v;
        public final double scaleU;
        public final double scaleV;

        Material(LuaValue value, LuaValue shorthand) {
            if (value.isnil()) {
                value = new LuaTable();
            }
            fields(value, "material", "texture", "blend", "lighting", "cull", "tint", "opacity", "uv");
            texture = AssetInputs.texture(value.get("texture").isnil() ? shorthand : value.get("texture"));
            blend = choice(value.get("blend"), "cutout", "opaque", "cutout", "alpha", "additive");
            unlit = choice(value.get("lighting"), "world", "world", "unlit").equals("unlit");
            if (!value.get("cull").isnil() && !value.get("cull").isboolean()) {
                throw new LuaError("material.cull must be boolean");
            }
            cull = value.get("cull").optboolean(false);
            double color = number(value.get("tint"), 0xffffff, 0, 0xffffff, "material.tint");
            if (color != (int) color) {
                throw new LuaError("material.tint must be an integer RGB value");
            }
            tint = (int) color;
            opacity = (float) number(value.get("opacity"), 1, 0, 1, "material.opacity");
            LuaValue uv = value.get("uv");
            if (uv.isnil()) {
                uv = new LuaTable();
            }
            fields(uv, "material.uv", "u", "v", "scaleU", "scaleV");
            u = number(uv.get("u"), 0, -10000, 10000, "uv.u");
            v = number(uv.get("v"), 0, -10000, 10000, "uv.v");
            scaleU = number(uv.get("scaleU"), 1, -10000, 10000, "uv.scaleU");
            scaleV = number(uv.get("scaleV"), 1, -10000, 10000, "uv.scaleV");
        }
    }

    private static String choice(LuaValue value, String fallback, String... options) {
        String text = value.optjstring(fallback);
        for (String option : options) {
            if (text.equals(option)) {
                return text;
            }
        }
        throw new LuaError("Unsupported material value: " + text);
    }

    private static double number(LuaValue value, double fallback, double min, double max, String path) {
        double number = value.optdouble(fallback);
        if (!Double.isFinite(number) || number < min || number > max) {
            throw new LuaError(path + " must be finite and within " + min + "-" + max);
        }
        return number;
    }
}

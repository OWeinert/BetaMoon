package betamoon.luaapi.fluid;

import betamoon.client.assets.AssetLocation;
import betamoon.client.assets.ClientAssets;
import betamoon.content.ContentKey;
import betamoon.content.ContentKeyException;
import betamoon.content.ContentType;
import betamoon.fluid.FluidAppearance;
import betamoon.fluid.FluidCameraPolicy;
import betamoon.fluid.FluidCarrier;
import betamoon.fluid.FluidDisplacementPolicy;
import betamoon.fluid.FluidEcologyPolicy;
import betamoon.fluid.FluidEntityPolicy;
import betamoon.fluid.FluidFlowPolicy;
import betamoon.luaapi.LuaApiUtils;
import betamoon.luaapi.asset.AssetInputs;
import betamoon.luaapi.utils.LuaContentNamespaces;
import betamoon.luaapi.utils.LuaDeclarationValues;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.resources.EnumTexAtlas;
import betamoon.runtime.RuntimeEnvironment;
import java.io.IOException;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;
import static betamoon.luaapi.utils.LuaDeclarationValues.number;
import static betamoon.luaapi.utils.LuaDeclarationValues.required;

/**
 * Strict parsing and preflight for the common fluid portion of both creation
 * paths.
 */
final class FluidDeclaration {
    final FluidDraft draft;
    final LuaValue blocks;
    final LuaValue bucket;

    private FluidDeclaration(FluidDraft draft, LuaValue blocks, LuaValue bucket) {
        this.draft = draft;
        this.blocks = blocks;
        this.bucket = bucket;
    }

    static FluidDeclaration parse(LuaValue value, boolean shorthand) {
        if (!value.istable()) {
            throw new LuaError("Fluid definition must be a table.");
        }
        fields(value, "fluid", "key", "material", "preset", "appearance", "flow", "displacement", "entity", "camera",
                "ecology", "safeTeleportArrival", shorthand ? "blocks" : "__no_blocks",
                shorthand ? "bucket" : "__no_bucket");

        String owner = LuaScriptRegistry.getCurrentScriptFile();
        if (owner == null) {
            throw new LuaError("Fluids may only be declared by a Lua script.");
        }
        ContentKey key = fluidKey(required(value, "key"));
        FluidCarrier carrier = carrier(required(value, "material"));
        String preset = value.get("preset").optjstring("neutral");
        FluidFlowPolicy base = preset(preset);
        FluidFlowPolicy flow = flow(value.get("flow"), base);
        FluidAppearance appearance = appearance(required(value, "appearance"), preset);
        FluidDisplacementPolicy displacement = displacement(value.get("displacement"), preset);
        FluidEntityPolicy entity = entity(value.get("entity"), preset);
        FluidCameraPolicy camera = camera(value.get("camera"), preset);
        LuaValue blocks = value.get("blocks");
        if (shorthand && !blocks.istable()) {
            throw new LuaError("fluid.blocks must be a table with flowing and still declarations.");
        }
        return new FluidDeclaration(new FluidDraft(key, owner, preset, carrier, flow, appearance, displacement, entity,
                camera, ecology(value.get("ecology")),
                LuaDeclarationValues.bool(value.get("safeTeleportArrival"), "fluid.safeTeleportArrival", false)),
                blocks, value.get("bucket"));
    }

    private static FluidEcologyPolicy ecology(LuaValue value) {
        if (value.isnil()) {
            return FluidEcologyPolicy.defaults(false, "neutral");
        }
        fields(value, "fluid.ecology", "fallingBlocksPassThrough", "hydratesFarmland", "supportsReeds",
                "absorbedBySponge", "boatsFloat", "aquaticHabitat", "pathfinding");
        try {
            return new FluidEcologyPolicy(value.get("fallingBlocksPassThrough").optboolean(false),
                    value.get("hydratesFarmland").optboolean(false), value.get("supportsReeds").optboolean(false),
                    value.get("absorbedBySponge").optboolean(false), value.get("boatsFloat").optboolean(false),
                    value.get("aquaticHabitat").optboolean(false), value.get("pathfinding").optjstring("blocked"));
        } catch (IllegalArgumentException exception) {
            throw new LuaError("fluid.ecology: " + exception.getMessage());
        }
    }

    private static ContentKey fluidKey(LuaValue value) {
        try {
            ContentKey key = ContentKey.parseForType(value.checkjstring(), ContentType.FLUID);
            LuaContentNamespaces.requireDeclarationNamespace(key);
            return key;
        } catch (ContentKeyException error) {
            throw new LuaError("fluid.key: " + error.getMessage());
        }
    }

    private static FluidCarrier carrier(LuaValue value) {
        try {
            return FluidCarrier.fromName(value.checkjstring());
        } catch (IllegalArgumentException error) {
            throw new LuaError("fluid.material: " + error.getMessage());
        }
    }

    private static FluidFlowPolicy preset(String preset) {
        if ("neutral".equals(preset)) {
            return FluidFlowPolicy.neutral();
        }
        if ("water".equals(preset)) {
            return FluidFlowPolicy.water();
        }
        if ("lava".equals(preset)) {
            return FluidFlowPolicy.lava();
        }
        throw new LuaError("fluid.preset must be neutral, water, or lava.");
    }

    private static FluidFlowPolicy flow(LuaValue value, FluidFlowPolicy base) {
        if (value.isnil()) {
            return base;
        }
        fields(value, "fluid.flow", "tickRate", "levelStep", "slopeSearch", "finite", "sourceCreation",
                "retreatDelayChance", "dimensions");
        int tickRate = optionalInteger(value.get("tickRate"), base.tickRate(), 1, 1200, "fluid.flow.tickRate");
        int levelStep = optionalInteger(value.get("levelStep"), base.levelStep(), 1, 7, "fluid.flow.levelStep");
        int slopeSearch = optionalInteger(value.get("slopeSearch"), base.slopeSearch(), 0, 8, "fluid.flow.slopeSearch");
        FluidFlowPolicy.SourceCreation source = sourceCreation(value, base.sourceCreation());
        float retreat = optionalFloat(value.get("retreatDelayChance"), base.retreatDelayChance(), 0.0F, 1.0F,
                "fluid.flow.retreatDelayChance");
        Integer hellTickRate = base.hellTickRate();
        Integer hellLevelStep = base.hellLevelStep();
        LuaValue dimensions = value.get("dimensions");
        if (!dimensions.isnil()) {
            if (!dimensions.istable()) {
                throw new LuaError("fluid.flow.dimensions must be a table.");
            }
            LuaValue[] dimensionKeys = dimensions.checktable().keys();
            if (dimensionKeys.length > 1) {
                throw new LuaError("fluid.flow.dimensions declares the Nether more than once.");
            }
            for (LuaValue dimensionKey : dimensionKeys) {
                String dimension = dimensionKey.checkjstring();
                if (!"nether".equals(dimension) && !"hell".equals(dimension)
                        && !"minecraft:dimension/hell".equals(dimension)) {
                    throw new LuaError("fluid.flow.dimensions contains unsupported dimension '" + dimension + "'.");
                }
                LuaValue override = dimensions.get(dimensionKey);
                fields(override, "fluid.flow.dimensions." + dimension, "tickRate", "levelStep");
                if (override.get("tickRate").isnil() && override.get("levelStep").isnil()) {
                    throw new LuaError("fluid.flow.dimensions." + dimension + " requires tickRate or levelStep.");
                }
                if (!override.get("tickRate").isnil()) {
                    hellTickRate = Integer.valueOf(integer(override.get("tickRate"),
                            "fluid.flow.dimensions." + dimension + ".tickRate", 1, 1200));
                }
                if (!override.get("levelStep").isnil()) {
                    hellLevelStep = Integer.valueOf(integer(override.get("levelStep"),
                            "fluid.flow.dimensions." + dimension + ".levelStep", 1, 7));
                }
            }
        }
        return new FluidFlowPolicy(tickRate, levelStep, slopeSearch, source, retreat, hellTickRate, hellLevelStep);
    }

    private static FluidFlowPolicy.SourceCreation sourceCreation(LuaValue flow,
            FluidFlowPolicy.SourceCreation fallback) {
        LuaValue finiteValue = flow.get("finite");
        LuaValue sourceValue = flow.get("sourceCreation");
        FluidFlowPolicy.SourceCreation finiteSource = fallback;
        if (!finiteValue.isnil()) {
            if (!finiteValue.isboolean()) {
                throw new LuaError("fluid.flow.finite must be a boolean.");
            }
            finiteSource = finiteValue.toboolean()
                    ? FluidFlowPolicy.SourceCreation.NEVER
                    : FluidFlowPolicy.SourceCreation.TWO_ADJACENT;
        }
        if (sourceValue.isnil()) {
            return finiteSource;
        }

        FluidFlowPolicy.SourceCreation source = sourceCreation(sourceValue.checkjstring());
        if (!finiteValue.isnil() && source != finiteSource) {
            throw new LuaError("fluid.flow.finite conflicts with fluid.flow.sourceCreation.");
        }
        return source;
    }

    private static FluidFlowPolicy.SourceCreation sourceCreation(String value) {
        if ("never".equals(value)) {
            return FluidFlowPolicy.SourceCreation.NEVER;
        }
        if ("two_adjacent".equals(value)) {
            return FluidFlowPolicy.SourceCreation.TWO_ADJACENT;
        }
        throw new LuaError("fluid.flow.sourceCreation must be never or two_adjacent.");
    }

    private static FluidAppearance appearance(LuaValue value, String preset) {
        fields(value, "fluid.appearance", "still", "flowing", "tint", "renderPass", "light", "lightOpacity");
        int still = texture(required(value, "still"));
        int flowing = texture(required(value, "flowing"));
        int tint = optionalInteger(value.get("tint"), 0xFFFFFF, 0, 0xFFFFFF, "fluid.appearance.tint");
        String passName = value.get("renderPass").optjstring("lava".equals(preset) ? "solid" : "translucent");
        int renderPass;
        if ("solid".equals(passName)) {
            renderPass = 0;
        } else if ("translucent".equals(passName)) {
            renderPass = 1;
        } else {
            throw new LuaError("fluid.appearance.renderPass must be solid or translucent.");
        }
        int light = optionalInteger(value.get("light"), "lava".equals(preset) ? 15 : 0, 0, 15,
                "fluid.appearance.light");
        int opacity = optionalInteger(value.get("lightOpacity"), "lava".equals(preset) ? 255 : 3, 0, 255,
                "fluid.appearance.lightOpacity");
        return new FluidAppearance(still, flowing, tint, renderPass, light, opacity);
    }

    private static int texture(LuaValue value) {
        if (value.isnumber()) {
            int texture = value.checkint();
            if (texture < 0) {
                throw new LuaError("Fluid texture index must be nonnegative.");
            }
            return texture;
        }
        return LuaApiUtils.registerTexture(EnumTexAtlas.BLOCKS, AssetInputs.texture(value));
    }

    private static FluidDisplacementPolicy displacement(LuaValue value, String preset) {
        String fallback = "lava".equals(preset) ? "destroy" : "drop";
        String selected;
        if (value.isnil()) {
            selected = fallback;
        } else {
            fields(value, "fluid.displacement", "blocks");
            selected = value.get("blocks").optjstring(fallback);
        }
        if ("drop".equals(selected)) {
            return FluidDisplacementPolicy.DROP;
        }
        if ("destroy".equals(selected)) {
            return FluidDisplacementPolicy.DESTROY;
        }
        if ("block".equals(selected)) {
            return FluidDisplacementPolicy.BLOCK;
        }
        throw new LuaError("fluid.displacement.blocks must be drop, destroy, or block.");
    }

    private static FluidEntityPolicy entity(LuaValue value, String preset) {
        FluidEntityPolicy base = FluidEntityPolicy.forPreset(preset);
        if (value.isnil()) {
            return base;
        }
        fields(value, "fluid.entity", "movement", "currentScale", "drowning", "countsAsWet", "extinguishesFire",
                "ignitesEntities", "contactDamage", "contactIntervalTicks");
        String movementName = value.get("movement").optjstring(base.movement().value());
        FluidEntityPolicy.Movement movement;
        if ("none".equals(movementName)) {
            movement = FluidEntityPolicy.Movement.NONE;
        } else if ("water".equals(movementName)) {
            movement = FluidEntityPolicy.Movement.WATER;
        } else if ("lava".equals(movementName)) {
            movement = FluidEntityPolicy.Movement.LAVA;
        } else {
            throw new LuaError("fluid.entity.movement must be none, water, or lava.");
        }
        float currentScale = optionalFloat(value.get("currentScale"), base.currentScale(), 0.0F, 8.0F,
                "fluid.entity.currentScale");
        int damage = optionalInteger(value.get("contactDamage"), base.contactDamage(), 0, Integer.MAX_VALUE,
                "fluid.entity.contactDamage");
        int interval = optionalInteger(value.get("contactIntervalTicks"), base.contactIntervalTicks(), 5, 200,
                "fluid.entity.contactIntervalTicks");
        return new FluidEntityPolicy(movement, currentScale,
                optionalBoolean(value.get("drowning"), base.drowning(), "fluid.entity.drowning"),
                optionalBoolean(value.get("countsAsWet"), base.countsAsWet(), "fluid.entity.countsAsWet"),
                optionalBoolean(value.get("extinguishesFire"), base.extinguishesFire(),
                        "fluid.entity.extinguishesFire"),
                optionalBoolean(value.get("ignitesEntities"), base.ignitesEntities(), "fluid.entity.ignitesEntities"),
                damage, interval);
    }

    private static boolean optionalBoolean(LuaValue value, boolean fallback, String path) {
        if (value.isnil()) {
            return fallback;
        }
        if (!value.isboolean()) {
            throw new LuaError(path + " must be a boolean.");
        }
        return value.toboolean();
    }

    private static FluidCameraPolicy camera(LuaValue value, String preset) {
        FluidCameraPolicy base = FluidCameraPolicy.forPreset(preset);
        if (value.isnil()) {
            return base;
        }
        fields(value, "fluid.camera", "fogColor", "fogDensity", "overlay");
        Integer color = base.fogColor();
        if (!value.get("fogColor").isnil()) {
            color = Integer.valueOf(integer(value.get("fogColor"), "fluid.camera.fogColor", 0, 0xFFFFFF));
        }
        Float density = base.fogDensity();
        if (!value.get("fogDensity").isnil()) {
            density = Float
                    .valueOf(optionalFloat(value.get("fogDensity"), 0.0F, 0.0F, 1.0F, "fluid.camera.fogDensity"));
        }
        LuaValue overlay = value.get("overlay");
        if (overlay.isnil()) {
            return new FluidCameraPolicy(color, density, base.nativeOverlay(), base.overlayAsset());
        }
        if (overlay.isboolean() && !overlay.toboolean()) {
            return new FluidCameraPolicy(color, density, null, null);
        }
        AssetLocation location = AssetInputs.texture(overlay);
        if (RuntimeEnvironment.isClient()) {
            try {
                ClientAssets.resolveTexture(location);
            } catch (IOException error) {
                throw new LuaError("fluid.camera.overlay: " + error.getMessage());
            }
        }
        return new FluidCameraPolicy(color, density, null, location);
    }

    private static int optionalInteger(LuaValue value, int fallback, int minimum, int maximum, String path) {
        return value.isnil() ? fallback : integer(value, path, minimum, maximum);
    }

    private static float optionalFloat(LuaValue value, float fallback, float minimum, float maximum, String path) {
        if (value.isnil()) {
            return fallback;
        }
        double result = number(value, path);
        if (result < minimum || result > maximum) {
            throw new LuaError(path + " must be within " + minimum + ".." + maximum + ".");
        }
        return (float) result;
    }
}

package betamoon.client.render;

import betamoon.assets.model.BedrockAnimations;
import betamoon.assets.model.ModelAnimations;
import betamoon.assets.model.ModelGeometry;
import betamoon.assets.model.ModelPose;
import betamoon.assets.model.ModelVector;
import betamoon.client.assets.ClientAssets;
import betamoon.client.assets.ClientModelAssets;
import betamoon.client.assets.ModelAsset;
import betamoon.entity.EntityPresentationState;
import betamoon.luaapi.LuaApiUtils;
import betamoon.luaapi.asset.ModelAppearanceDeclaration;
import betamoon.luaapi.asset.PoseReference;
import betamoon.resources.LuaTextureResources;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.src.ModLoader;
import org.lwjgl.opengl.GL11;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * Owns a coherent visual binding. Pack failures retain the last complete usable
 * model/clip pair.
 */
public final class ModelAppearance implements AutoCloseable {
    private static final List<ModelAppearance> LIVE = new ArrayList<>();
    private static boolean texturesNeedPreload;
    public final ModelAppearanceDeclaration definition;
    private final ModelAppearance parent;
    public final Map<String, String> textures = new LinkedHashMap<>();
    public final List<ModelAppearance> layers = new ArrayList<>();
    private ModelAsset<ModelGeometry> model;
    private ModelAsset<ModelAnimations> animations;
    private ModelGeometry geometry;
    private ModelAnimations.Clip clip;
    private boolean closed;
    private boolean failed;
    private final Map<String, ModelAnimations.Clip> selectedClips = new HashMap<>();
    private final Set<String> missingClips = new HashSet<>();
    private Runnable changed = () -> {
    };

    public ModelAppearance(ModelAppearanceDeclaration definition) throws IOException {
        this(definition, null);
    }

    private ModelAppearance(ModelAppearanceDeclaration definition, ModelAppearance parent) throws IOException {
        this.definition = definition;
        this.parent = parent;
        try {
            model = ClientModelAssets.model(definition.model);
            if (definition.animation != null) {
                animations = ClientModelAssets.animations(definition.animation);
            }
            for (Map.Entry<String, ModelAppearanceDeclaration.Material> material : definition.materials.entrySet()) {
                textures.put(material.getKey(), LuaTextureResources.register(material.getValue().texture));
            }
            refresh();
            for (ModelAppearanceDeclaration layer : definition.layers) {
                layers.add(new ModelAppearance(layer, this));
            }
            LIVE.add(this);
            texturesNeedPreload = true;
        } catch (IOException | RuntimeException error) {
            close();
            throw error;
        }
    }

    public void onChange(Runnable callback) {
        changed = callback;
        for (ModelAppearance layer : layers) {
            layer.onChange(callback);
        }
    }

    public boolean isFailed() {
        return failed;
    }

    public void fail(RuntimeException error) {
        if (!failed) {
            failed = true;
            LuaApiUtils.warn("Models", definition.model.getCacheKey() + ": rendering suspended until asset refresh: "
                    + error.getMessage());
        }
    }

    public static void preloadTextures() {
        Minecraft minecraft = ModLoader.getMinecraftInstance();
        if (minecraft == null || minecraft.renderEngine == null || LIVE.isEmpty() || !texturesNeedPreload) {
            return;
        }
        int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            for (ModelAppearance appearance : new ArrayList<>(LIVE)) {
                for (String texture : appearance.textures.values()) {
                    minecraft.renderEngine.getTexture(texture);
                }
            }
            texturesNeedPreload = false;
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, bound);
        }
    }

    public ModelGeometry geometry() {
        return geometry;
    }

    public static void refreshAll() {
        texturesNeedPreload = true;
        for (ModelAppearance appearance : new ArrayList<>(LIVE)) {
            try {
                appearance.refresh();
                appearance.changed.run();
            } catch (IOException | IllegalArgumentException error) {
                LuaApiUtils.warn("Models", appearance.definition.model.getCacheKey() + ": " + error.getMessage()
                        + "; retaining the previous complete appearance");
            }
        }
    }

    private void refresh() throws IOException {
        ModelGeometry candidate = model.getContent().getValue();
        ModelAnimations.Clip candidateClip;
        try {
            candidateClip = animations == null ? null : animations.getContent().getValue().clip(definition.clip);
            validate(candidate, candidateClip);
        } catch (IOException | IllegalArgumentException error) {
            LuaApiUtils.warn("Models",
                    definition.model.getCacheKey() + ": incompatible model/animation/material group: "
                            + error.getMessage() + "; trying bundled geometry and clips together");
            candidate = ClientModelAssets.defaultModel(definition.model).getValue();
            candidateClip = animations == null
                    ? null
                    : ClientAssets.getResolver(definition.animation)
                            .resolveDefault(definition.animation.getCacheKey(), definition.animation.getFallback(),
                                    ClientModelAssets.MAX_JSON_BYTES, BedrockAnimations::decode)
                            .getValue().clip(definition.clip);
            validate(candidate, candidateClip);
        }
        geometry = candidate;
        clip = candidateClip;
        selectedClips.clear();
        missingClips.clear();
        failed = false;
    }

    private void validate(ModelGeometry candidate, ModelAnimations.Clip candidateClip) throws IOException {
        if (candidateClip != null) {
            candidateClip.validate(candidate);
        }
        for (ModelGeometry.Bone bone : candidate.bones) {
            for (ModelGeometry.Quad face : bone.faces) {
                if (!textures.containsKey(face.material)) {
                    throw new IOException("Missing material binding: " + face.material);
                }
            }
        }
    }

    public ModelPose evaluate(String display, double ageTicks, int metadata, double x, double y, double z) {
        return evaluate(display, ageTicks, metadata, x, y, z, null, true);
    }

    public ModelPose evaluate(String display, double ageTicks, int metadata, double x, double y, double z,
            EntityPresentationState.Animation playback) {
        return evaluate(display, ageTicks, metadata, x, y, z, playback, true);
    }

    ModelPose evaluate(String display, double ageTicks, int metadata, double x, double y, double z,
            EntityPresentationState.Animation playback, boolean warnMissing) {
        ModelPose pose = new ModelPose(geometry);
        ModelAnimations.Clip selected = clip;
        double seconds = Math.max(0, ageTicks) / 20 * definition.speed;
        if (playback != null && animations != null) {
            if (!missingClips.contains(playback.clip)) {
                try {
                    selected = selectedClips.get(playback.clip);
                    if (selected == null) {
                        selected = animations.getContent().getValue().clip(playback.clip);
                        selected.validate(geometry);
                        selectedClips.put(playback.clip, selected);
                    }
                    seconds = playback.seconds(ageTicks);
                } catch (IOException | IllegalArgumentException error) {
                    selected = clip;
                    if (missingClips.add(playback.clip) && warnMissing) {
                        LuaApiUtils.warn("Entities", definition.model.getCacheKey() + ": animation '"
                                + playback.clip + "' unavailable; using the default clip");
                    }
                }
            }
        }
        if (selected != null) {
            selected.apply(pose, seconds, 1, null);
        }
        if (definition.callbacks.has(ModelAppearanceDeclaration.Callback.POSE)) {
            PoseReference writer = new PoseReference(pose, true);
            LuaTable context = new LuaTable();
            LuaTable state = new LuaTable();
            state.set("ageTicks", LuaValue.valueOf(ageTicks));
            state.set("display", display);
            state.set("metadata", metadata);
            state.set("x", LuaValue.valueOf(x));
            state.set("y", LuaValue.valueOf(y));
            state.set("z", LuaValue.valueOf(z));
            context.set("pose", writer);
            context.set("state", state);
            try {
                definition.callbacks.call(ModelAppearanceDeclaration.Callback.POSE, context, LuaValue.NIL);
            } finally {
                writer.expire();
            }
        }
        return pose;
    }

    public ModelVector transform(ModelVector point, String context) {
        point = definition.transform.apply(point);
        ModelAppearanceDeclaration.Transform display = definition.display.get(context);
        point = display == null ? point : display.apply(point);
        return parent == null ? point : parent.transform(point, context);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        LIVE.remove(this);
        for (ModelAppearance layer : layers) {
            layer.close();
        }
        for (String texture : textures.values()) {
            LuaTextureResources.release(texture);
        }
        if (model != null) {
            model.close();
        }
        if (animations != null) {
            animations.close();
        }
    }
}

package betamoon.client.assets;

import betamoon.assets.AssetKind;
import betamoon.assets.io.AssetDecoder;
import betamoon.assets.io.ResolvedAsset;
import betamoon.assets.model.BedrockAnimations;
import betamoon.assets.model.BedrockGeometry;
import betamoon.assets.model.ModelAnimations;
import betamoon.assets.model.ModelGeometry;
import betamoon.assets.io.AssetResolver;
import betamoon.assets.io.BuiltinAssetProvider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Shared decoded geometry and clips; all reads happen during registration or
 * refresh.
 */
public final class ClientModelAssets {
    public static final int MAX_JSON_BYTES = 2 * 1024 * 1024;
    private static final Map<String, ModelAsset<ModelGeometry>> MODELS = new LinkedHashMap<>();
    private static final Map<String, ModelAsset<ModelAnimations>> ANIMATIONS = new LinkedHashMap<>();

    private ClientModelAssets() {
    }

    public static ModelAsset<ModelGeometry> model(AssetLocation location) throws IOException {
        if (location.getKind() != AssetKind.MODEL) {
            throw new IllegalArgumentException("Expected a model asset");
        }
        ModelAsset<ModelGeometry> current = MODELS.get(location.getCacheKey());
        if (current != null) {
            return current.retain();
        }
        ModelGeometry contract = defaultModel(location).getValue();
        ModelAsset<ModelGeometry> created = new ModelAsset<>(location, resolveModel(location, contract), contract);
        MODELS.put(location.getCacheKey(), created);
        return created;
    }

    public static ModelAsset<ModelAnimations> animations(AssetLocation location) throws IOException {
        if (location.getKind() != AssetKind.ANIMATION) {
            throw new IllegalArgumentException("Expected an animation asset");
        }
        ModelAsset<ModelAnimations> current = ANIMATIONS.get(location.getCacheKey());
        if (current != null) {
            return current.retain();
        }
        ModelAnimations contract = ClientAssets.getResolver().resolveDefault(location.getCacheKey(),
                location.getFallback(), MAX_JSON_BYTES, BedrockAnimations::decode).getValue();
        ModelAsset<ModelAnimations> created = new ModelAsset<>(location, resolveAnimations(location, contract),
                contract);
        ANIMATIONS.put(location.getCacheKey(), created);
        return created;
    }

    private static <T> ResolvedAsset<T> resolve(AssetLocation location, AssetDecoder<T> decoder) throws IOException {
        return ClientAssets.getResolver().resolve(location.getCacheKey(), location.getFallback(),
                location.getOverride(), MAX_JSON_BYTES, decoder);
    }

    public static boolean hasAnimations(AssetLocation location) {
        return ANIMATIONS.containsKey(location.getCacheKey());
    }

    static void release(ModelAsset<?> asset) {
        MODELS.remove(asset.getLocation().getCacheKey(), asset);
        ANIMATIONS.remove(asset.getLocation().getCacheKey(), asset);
    }

    /**
     * Preserve contracts used by existing bindings; malformed or incompatible packs
     * fall back together.
     */
    public static void refresh(Consumer<String> warnings) {
        for (ModelAsset<ModelGeometry> asset : new ArrayList<>(MODELS.values())) {
            try {
                ModelGeometry previous = asset.contract();
                asset.replace(resolveModel(asset.getLocation(), previous));
            } catch (IOException error) {
                warnings.accept("Model " + asset.getLocation().getCacheKey() + ": " + error.getMessage()
                        + "; keeping previous geometry");
            }
        }
        for (ModelAsset<ModelAnimations> asset : new ArrayList<>(ANIMATIONS.values())) {
            try {
                ModelAnimations previous = asset.contract();
                asset.replace(resolveAnimations(asset.getLocation(), previous));
            } catch (IOException error) {
                warnings.accept("Animation " + asset.getLocation().getCacheKey() + ": " + error.getMessage()
                        + "; keeping previous clips");
            }
        }
    }

    private static ResolvedAsset<ModelGeometry> resolveModel(AssetLocation location, ModelGeometry previous)
            throws IOException {
        AssetResolver resolver = modelResolver(location);
        return resolver.resolve(location.getCacheKey(), location.getFallback(), location.getOverride(), MAX_JSON_BYTES,
                bytes -> {
                    ModelGeometry candidate = BedrockGeometry.decode(bytes);
                    compatible(previous, candidate);
                    return candidate;
                }, bytes -> {
                    ModelGeometry candidate = BedrockGeometry.decode(bytes);
                    compatible(previous, candidate);
                    return candidate;
                });
    }

    public static ResolvedAsset<ModelGeometry> defaultModel(AssetLocation location) throws IOException {
        return modelResolver(location).resolveDefault(location.getCacheKey(), location.getFallback(), MAX_JSON_BYTES,
                BedrockGeometry::decode);
    }

    private static AssetResolver modelResolver(AssetLocation location) throws IOException {
        AssetResolver resolver = ClientAssets.getResolver();
        return location.isBuiltin() ? resolver.withDefaults(BuiltinAssetProvider.INSTANCE, "builtin") : resolver;
    }

    private static ResolvedAsset<ModelAnimations> resolveAnimations(AssetLocation location, ModelAnimations previous)
            throws IOException {
        return resolve(location, bytes -> {
            ModelAnimations candidate = BedrockAnimations.decode(bytes);
            compatible(previous, candidate);
            return candidate;
        });
    }

    private static void compatible(ModelAnimations required, ModelAnimations candidate) throws IOException {
        for (Map.Entry<String, ModelAnimations.Clip> entry : required.clips.entrySet()) {
            ModelAnimations.Clip next = candidate.clips.get(entry.getKey());
            if (next == null || !entry.getValue().bones.keySet().containsAll(next.bones.keySet())) {
                throw new IOException("Animation replacement must retain clip names and cannot add bone requirements: "
                        + entry.getKey());
            }
        }
    }

    private static void compatible(ModelGeometry previous, ModelGeometry candidate) throws IOException {
        for (ModelGeometry.Bone bone : previous.bones) {
            if (!candidate.hasPart(bone.name)) {
                throw new IOException("Replacement is missing required model part: " + bone.name);
            }
        }
        if (!candidate.sockets.keySet().containsAll(previous.sockets.keySet())) {
            throw new IOException("Replacement is missing required model locators");
        }
        if (!materials(previous).containsAll(materials(candidate))) {
            throw new IOException("Replacement introduces unbound material slots");
        }
    }

    private static Set<String> materials(ModelGeometry geometry) {
        Set<String> result = new HashSet<>();
        for (ModelGeometry.Bone bone : geometry.bones) {
            for (ModelGeometry.Quad face : bone.faces) {
                result.add(face.material);
            }
        }
        return result;
    }
}

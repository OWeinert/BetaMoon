package betamoon.client.assets;

import betamoon.assets.io.AssetResolver;
import betamoon.assets.io.AssetProvider;
import betamoon.assets.io.PackageAssetProvider;
import betamoon.assets.io.ResolvedAsset;
import betamoon.assets.io.ZipAssetProvider;
import betamoon.client.audio.ClientSounds;
import betamoon.client.render.ModelAppearance;
import betamoon.client.render.EntityVisuals;
import betamoon.io.IoUtils;
import betamoon.luaapi.LuaApiUtils;
import betamoon.luaapi.block.BlockModelRegistry;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.src.ModLoader;
import net.minecraft.src.TexturePackBase;
import net.minecraft.src.TexturePackCustom;

/**
 * Main-client-thread coordinator; providers and decoders remain independently
 * testable.
 */
public final class ClientAssets {
    public static final int MAX_TEXTURE_BYTES = 8 * 1024 * 1024;
    private static final Map<String, TextureAsset> TEXTURES = new LinkedHashMap<>();
    private static AssetResolver resolver;
    private static TexturePackBase selectedPack;
    private static boolean testProviders;
    private static boolean refreshRequested;
    private static boolean preparedRefresh;
    private static Consumer<String> diagnostics = message -> LuaApiUtils.warn("Assets", message);

    private ClientAssets() {
    }

    public static TextureAsset acquireTexture(AssetLocation location) throws IOException {
        ensureProviders();
        TextureAsset current = TEXTURES.get(location.getCacheKey());
        if (current != null) {
            return current.retain();
        }
        TextureAsset created = new TextureAsset(location, resolveTexture(location));
        TEXTURES.put(location.getCacheKey(), created);
        return created;
    }

    static void releaseTexture(TextureAsset asset) {
        TEXTURES.remove(asset.getLocation().getCacheKey(), asset);
    }

    public static ResolvedAsset<TextureImage> resolveTexture(AssetLocation location) throws IOException {
        ensureProviders();
        return resolver.resolve(location.getCacheKey(), location.getFallback(), location.getOverride(),
                MAX_TEXTURE_BYTES, TextureImage::decode);
    }

    public static AssetResolver getResolver() throws IOException {
        ensureProviders();
        return resolver;
    }

    /**
     * Called at native refresh entry, before Minecraft reads cached virtual
     * resource paths.
     */
    public static void refresh() {
        try {
            if (!testProviders) {
                installNativeProviders();
            }
            ClientSounds.refresh(diagnostics);
            ClientModelAssets.refresh(diagnostics);
            for (TextureAsset asset : new ArrayList<>(TEXTURES.values())) {
                try {
                    asset.replace(resolveTexture(asset.getLocation()));
                } catch (IOException | IllegalArgumentException error) {
                    diagnostics.accept("Asset " + asset.getLocation().getCacheKey() + ": " + error.getMessage()
                            + "; keeping the previous usable texture");
                }
            }
            ModelAppearance.refreshAll();
            EntityVisuals.retryFailed();
        } catch (IOException error) {
            diagnostics.accept("Asset refresh failed: " + error.getMessage());
        }
    }

    public static void requestRefresh() {
        refreshRequested = true;
    }

    public static void onNativeRefreshFinished() {
        ModelAppearance.preloadTextures();
        if (!preparedRefresh) {
            AtlasTextures.refresh();
        }
    }

    public static void onNativeRefresh() {
        if (!preparedRefresh) {
            refresh();
        }
        refreshRequested = false;
    }

    /** Detect selection and perform requested reloads at a client tick boundary. */
    public static void poll() {
        if (testProviders) {
            return;
        }
        Minecraft minecraft = ModLoader.getMinecraftInstance();
        if (minecraft == null || minecraft.texturePackList == null) {
            return;
        }
        if (refreshRequested || minecraft.texturePackList.selectedTexturePack != selectedPack) {
            preparedRefresh = true;
            try {
                refresh();
                if (minecraft.renderEngine != null) {
                    minecraft.renderEngine.refreshTextures();
                }
                AtlasTextures.refresh();
                refreshRequested = false;
            } finally {
                preparedRefresh = false;
            }
        }
        ModelAppearance.preloadTextures();
        AtlasTextures.uploadChanged();
        BlockModelRegistry.flushInvalidation();
    }

    private static void ensureProviders() throws IOException {
        if (resolver == null) {
            installNativeProviders();
        }
    }

    private static void installNativeProviders() throws IOException {
        File root = IoUtils.resolveLuaModsDir(ClientAssets.class, false);
        if (root == null) {
            throw new IOException("Lua scripts directory not found");
        }
        Minecraft minecraft = ModLoader.getMinecraftInstance();
        selectedPack = minecraft == null || minecraft.texturePackList == null
                ? null
                : minecraft.texturePackList.selectedTexturePack;
        AssetProvider pack = null;
        if (selectedPack instanceof TexturePackCustom) {
            for (Field field : TexturePackCustom.class.getDeclaredFields()) {
                if (field.getType() == File.class) {
                    try {
                        field.setAccessible(true);
                        pack = new ZipAssetProvider((File) field.get(selectedPack));
                    } catch (IllegalAccessException error) {
                        throw new IOException("Cannot read selected texture pack location", error);
                    }
                    break;
                }
            }
            if (pack == null) {
                throw new IOException("Selected texture pack file is unavailable");
            }
        }
        resolver = new AssetResolver(new PackageAssetProvider(root), pack, diagnostics);
    }

    /**
     * Explicit provider injection for headless integration tests; never exposed to
     * Lua.
     */
    public static void useProviders(AssetProvider defaults, AssetProvider pack, Consumer<String> warnings) {
        diagnostics = warnings;
        resolver = new AssetResolver(defaults, pack, warnings);
        testProviders = true;
    }
}

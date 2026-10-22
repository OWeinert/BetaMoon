package betamoon.client.assets;

import betamoon.assets.AssetDefinition;
import betamoon.assets.AssetId;
import betamoon.assets.AssetKey;
import betamoon.assets.AssetKind;
import betamoon.assets.BuiltinAssets;
import betamoon.assets.NativeTextureSource;
import betamoon.assets.io.FileAssetProvider;
import betamoon.client.render.ModelAppearance;
import betamoon.client.render.ModelTextureBinding;
import betamoon.luaapi.asset.AssetsApi;
import betamoon.luaapi.asset.ModelAppearanceDeclaration;
import betamoon.resources.EnumTexAtlas;
import betamoon.resources.LuaTextureResources;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import net.minecraft.src.TexturePackCustom;
import net.minecraft.src.TexturePackDefault;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.lib.jse.JsePlatform;

/** Actual vanilla pack lookup, headless catalog, Lua references and shared model UV bindings. */
public final class NativeTextureTest {
    private NativeTextureTest() {
    }

    public static void main(String[] args) throws Exception {
        run();
    }

    public static void run() throws Exception {
        verifyCatalog();
        Path root = Files.createTempDirectory("betamoon-native-textures-");
        try {
            ClientAssets.useProviders(new FileAssetProvider(root.toFile()), null, message -> { });
            AssetLocation sand = location("block/sand");
            AssetLocation raw = location("image/terrain");
            NativeTextureSource source = sand.getNativeTexture();
            require(source.isCell() && source.getIndex() == 18 && source.getColumn() == 2 && source.getRow() == 1,
                    "Sand must identify terrain cell 18");
            require(sand.getCacheKey().equals(location("atlas/terrain/18").getCacheKey()),
                    "Aliases share canonical content identity");
            require("terrain.png".equals(sand.getOverride().toString()), "Native pack paths stay at ZIP root");
            require(AtlasTextures.register(EnumTexAtlas.BLOCKS, sand) == 18,
                    "Matching ordinary block textures use the existing native slot");
            require(AtlasTextures.register(EnumTexAtlas.ITEMS, location("atlas/items/7")) == 7,
                    "Matching ordinary item textures use the existing native slot");
            rejects(() -> AtlasTextures.register(EnumTexAtlas.ITEMS, sand));
            rejects(() -> ClientAssets.resolveTexture(sand));

            NativeTextureResources.useTexturePack(new TexturePackDefault());
            require(NativeTextureResources.inspect(source).width == 256, "Default image comes from client resources");
            require("jar".equals(NativeTextureResources.inspect(source).origin), "Default origin is known");
            require(Math.abs(source.mapU(0.5, 256) - 2.5 / 16) < 0.000001
                    && Math.abs(source.mapV(0.5, 256) - 1.5 / 16) < 0.000001,
                    "Cell-local UVs address only the selected cell");
            require(source.mapU(1, 512) < 3.0 / 16 && source.mapU(0, 512) > 2.0 / 16,
                    "HD atlas UVs retain a pixel-scaled inset");
            rejects(() -> source.mapU(1.1, 256));

            Path packFile = root.resolve("native.zip");
            writePack(packFile, "bm_assets/minecraft/textures/atlas/terrain/18.png", AssetResourceTest.png(0xff00ff00, 512));
            TexturePackCustom pack = open(packFile);
            try {
                NativeTextureResources.useTexturePack(pack);
                require(NativeTextureResources.inspect(source).width == 256,
                        "A bm_assets cell image must not intercept vanilla JAR fallback");
            } finally {
                pack.closeTexturePackFile();
            }

            writePack(packFile, "terrain.png", AssetResourceTest.png(0xff00ff00, 512));
            pack = open(packFile);
            try {
                NativeTextureResources.useTexturePack(pack);
                verifyLuaAndModels(source, raw);
                require(NativeTextureResources.inspect(source).width == 512,
                        "Vanilla custom pack lookup selects root terrain.png with 32-pixel cells");
                pack.closeTexturePackFile();
                writePack(packFile, "terrain.png", AssetResourceTest.png(0xff00ff00, 256));
                ClientAssets.refresh();
                require(NativeTextureResources.inspect(source).width == 256,
                        "Asset refresh reopens the same selected vanilla pack and invalidates cached dimensions");
            } finally {
                pack.closeTexturePackFile();
            }
            writePack(packFile, "terrain.png", AssetResourceTest.png(0xff00ff00, 255));
            pack = open(packFile);
            try {
                NativeTextureResources.useTexturePack(pack);
                NativeTextureResources.inspect(raw.getNativeTexture());
                rejects(() -> NativeTextureResources.inspect(source));
            } finally {
                pack.closeTexturePackFile();
            }
            writePack(packFile, "terrain.png", new byte[]{0});
            pack = open(packFile);
            try {
                NativeTextureResources.useTexturePack(pack);
                rejects(() -> NativeTextureResources.inspect(source));
            } finally {
                pack.closeTexturePackFile();
            }
            NativeTextureResources.useTexturePack(new TexturePackDefault());
            require(NativeTextureResources.inspect(source).width == 256, "Pack change recovers after a broken image");
            System.out.println("Native textures passed: verified catalog, vanilla ZIP/JAR lookup, Lua metadata, "
                    + "HD cell UVs, cache identity, refresh and consumer boundaries.");
        } finally {
            NativeTextureResources.useTexturePack(null);
            AssetResourceTest.deleteTree(root);
        }
    }

    private static void verifyLuaAndModels(NativeTextureSource source, AssetLocation raw) throws Exception {
        Globals lua = JsePlatform.standardGlobals();
        LuaTable api = new LuaTable();
        AssetsApi.attach(api);
        lua.set("betamoon", api);
        lua.load("sand=betamoon.assets.textures:getRequired('minecraft:block/sand'); "
                + "assert(sand:getKey()=='minecraft:block/sand' and sand:getPath()=='terrain.png'); "
                + "assert(sand:getOverridePath()=='terrain.png'); local s=sand:getSource(); "
                + "assert(s.kind=='minecraft' and s.path=='terrain.png' and s.width==512); "
                + "assert(s.origin=='unknown' and s.region.index==18 and s.region.columns==16); "
                + "assert(betamoon.assets.textures:get('minecraft:atlas/terrain/256')==nil); "
                + "appearance={model='minecraft:block/full_block',texture=sand}").call();
        try (ModelAppearance appearance = new ModelAppearance(new ModelAppearanceDeclaration(lua.get("appearance")))) {
            ModelTextureBinding binding = appearance.textures.get("default");
            require("/terrain.png".equals(binding.resource()), "Models bind the live vanilla atlas directly");
            require(Math.abs(binding.mapU(0.5) - source.mapU(0.5, 512)) < 0.000001,
                    "Model material binding applies the same native cell mapping");
            int[] changed = {0};
            appearance.onChange(() -> changed[0]++);
            ClientAssets.refresh();
            require(changed[0] == 1 && !appearance.isFailed(), "Native visual refresh invalidates existing appearances");
        }
        try (ModelTextureBinding binding = new ModelTextureBinding(raw)) {
            require(binding.mapU(0) == 0 && binding.mapU(1) == 1 && binding.mapV(1) == 1,
                    "Whole-image textures retain full-image UVs");
        }
        String image = LuaTextureResources.register(raw);
        require("/terrain.png".equals(image) && LuaTextureResources.dimensions(image)[0] == 512,
                "Whole-image GUI/armor resources retain native paths and dimensions");
        LuaTextureResources.release(image);
        rejects(() -> LuaTextureResources.register(location("block/sand")));
        lua.load("appearance.material={uv={scaleU=2}}; appearance.texture=sand").call();
        rejects(() -> new ModelAppearance(new ModelAppearanceDeclaration(lua.get("appearance"))));
    }

    private static void verifyCatalog() throws Exception {
        Set<String> catalog = new HashSet<>();
        int cells = 0;
        for (AssetDefinition definition : BuiltinAssets.textures().values()) {
            NativeTextureSource source = definition.getNativeTexture();
            if (source != null && !source.isCell()) {
                catalog.add(source.getPath().toString());
            } else if (source != null) {
                cells++;
            }
        }
        Set<String> jarImages = new HashSet<>();
        try (ZipFile jar = new ZipFile(Paths.get("tools/rmcp/jars/minecraft.jar").toFile())) {
            Enumeration<? extends ZipEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.endsWith(".png")) {
                    jarImages.add(name);
                }
            }
        }
        require(catalog.equals(jarImages), "The metadata-only image catalog must match the supported client JAR");
        require(cells == 513, "Catalog includes 512 atlas cells plus the sand alias");
    }

    private static AssetLocation location(String key) {
        return new AssetLocation(BuiltinAssets.find(new AssetId(AssetKind.TEXTURE, AssetKey.parse("minecraft:" + key))));
    }

    private static TexturePackCustom open(Path file) {
        TexturePackCustom pack = new TexturePackCustom(file.toFile());
        pack.func_6482_a();
        return pack;
    }

    private static void writePack(Path file, String path, byte[] bytes) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry(path));
            zip.write(bytes);
            zip.closeEntry();
        }
    }

    private static void rejects(Attempt action) throws Exception {
        try {
            action.run();
        } catch (IOException | IllegalArgumentException | LuaError expected) {
            return;
        }
        throw new AssertionError("Invalid native texture use was accepted");
    }

    private interface Attempt {
        void run() throws Exception;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

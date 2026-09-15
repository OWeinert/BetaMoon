package betamoon.luamodloader;

import betamoon.assets.io.FileAssetProvider;
import betamoon.client.assets.ClientAssets;
import betamoon.client.assets.AtlasTextures;
import betamoon.resources.EnumTexAtlas;
import betamoon.client.assets.AssetResourceTest;
import java.io.File;
import java.lang.reflect.Field;
import net.minecraft.src.Session;
import net.minecraft.src.ModLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Runs the public API inside the real parser/lifecycle, including recoverable
 * Lua errors.
 */
public final class AssetLuaApiTest {
    private AssetLuaApiTest() {
    }

    public static void main(String[] arguments) throws Exception {
        Path root = Files.createTempDirectory("betamoon-lua-assets-");
        try {
            Files.write(root.resolve("guard.png"), AssetResourceTest.png(0xff00ff00, 16));
            Files.write(root.resolve("hit.wav"), AssetResourceTest.wav());
            Files.write(root.resolve("broken.png"), new byte[]{0});
            List<String> warnings = new ArrayList<>();
            ClientAssets.useProviders(new FileAssetProvider(root.toFile()), null, warnings::add);
            AtlasTextures.useBackend(new AtlasTextures.Backend() {
                public int allocate(EnumTexAtlas atlas) {
                    return 200;
                }

                public boolean isReady() {
                    return false;
                }

                public boolean isAnaglyph() {
                    return false;
                }

                public void upload(EnumTexAtlas atlas, int index, byte[] pixels) {
                }
            });
            Field blockList = Session.class.getDeclaredFields()[0];
            blockList.setAccessible(true);
            Field modLoaderList = ModLoader.class.getDeclaredField("field_blockList");
            modLoaderList.setAccessible(true);
            modLoaderList.set(null, blockList);
            String source = "name='Asset API test'\n"
                    + "assert(not pcall(function() betamoon.assets.textures:add{key='mymod:early',path='guard.png'} end))\n"
                    + "function modInit()\n"
                    + " local t=betamoon.assets.textures:add{key='mymod:guard',path='guard.png'}\n"
                    + " assert(t:getKey()=='mymod:guard' and t:getKind()=='textures')\n"
                    + " assert(t:getPath()=='guard.png' and t:getOverridePath()=='betamoon/mymod/textures/guard.png')\n"
                    + " assert(t:getSource().kind=='script')\n"
                    + " assert(betamoon.assets.textures:get('mymod:guard')~=nil)\n"
                    + " assert(betamoon.assets.textures:get('other:guard')==nil)\n"
                    + " assert(not pcall(function() betamoon.assets.textures:getRequired('other:guard') end))\n"
                    + " assert(not pcall(function() betamoon.assets.textures:add{key='mymod:guard',path='guard.png'} end))\n"
                    + " assert(betamoon.assets.textures:getRequired('mymod:guard')~=nil)\n"
                    + " assert(not pcall(function() betamoon.assets.textures:add{key='mymod:broken',path='broken.png'} end))\n"
                    + " assert(betamoon.assets.textures:get('mymod:broken')==nil)\n"
                    + " betamoon.blocks:add{id=231,key='asset_block',material='rock',texture=t,textures={top='mymod:guard'}}\n"
                    + " betamoon.items:add{id=29001,key='asset_item',texture=t}\n"
                    + " betamoon.items:getRequired(29001):override{texture=t}\n"
                    + " local s=betamoon.assets.sounds:add{key='mymod:guard',path='hit.wav'}\n"
                    + " assert(s:getKind()=='sounds' and s:getSource().kind=='script')\n"
                    + " local event=betamoon.soundEvents:add{key='mymod:hit',clips={{sound=s,weight=2},{sound='hit.wav'}},pitch={min=0.9,max=1.1}}\n"
                    + " assert(event:getKey()=='mymod:hit' and betamoon.soundEvents:get('mymod:hit')~=nil)\n"
                    + " assert(not pcall(function() betamoon.soundEvents:add{key='mymod:wrong',sound=t} end))\n"
                    + " assert(not pcall(function() betamoon.audio:play(event,{range=-1}) end))\n"
                    + " assert(not pcall(function() betamoon.soundEvents:add{key='mymod:bad',sound=s,pitch=0/0} end))\n"
                    + " betamoon.assets:refresh()\n"
                    + " betamoon.modules:export('asset_test',{texture=t,sound=s,event=event})\n" + "end\n";
            List<String> errors = new ArrayList<>();
            ScriptMod mod = new ScriptModParser().parse(new File("asset_api_test.lua"), source, errors);
            require(mod != null && errors.isEmpty(), "API script must parse: " + errors);
            new ScriptLifecycleRunner(new RetainedScriptCatalog(), errors::add).run(Collections.singletonList(mod),
                    new ArrayList<>(), false);
            require(mod.isLoaded() && errors.isEmpty(), "API script must initialize: " + errors);
            ScriptResourceTracker.unload("asset_api_test.lua");
            String consumer = "name='Asset cleanup test'\nfunction modInit()\n"
                    + "assert(betamoon.assets.textures:get('mymod:guard')==nil)\n"
                    + "assert(betamoon.assets.sounds:get('mymod:guard')==nil)\n"
                    + "assert(betamoon.soundEvents:get('mymod:hit')==nil)\nend";
            ScriptMod cleanup = new ScriptModParser().parse(new File("asset_cleanup_test.lua"), consumer, errors);
            new ScriptLifecycleRunner(new RetainedScriptCatalog(), errors::add).run(Collections.singletonList(cleanup),
                    new ArrayList<>(), false);
            require(cleanup.isLoaded() && errors.isEmpty(), "Unloading must release registrations: " + errors);
            System.out.println(
                    "Asset Lua API passed: declarations, references, events, validation, publication and cleanup.");
        } finally {
            ScriptResourceTracker.unloadAll();
            LuaScriptRegistry.clear();
            AssetResourceTest.deleteTree(root);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

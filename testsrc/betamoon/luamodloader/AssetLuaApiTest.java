package betamoon.luamodloader;

import betamoon.assets.io.FileAssetProvider;
import betamoon.assets.io.ZipAssetProvider;
import betamoon.client.assets.AssetResourceTest;
import betamoon.client.assets.AtlasTextures;
import betamoon.client.assets.ClientAssets;
import betamoon.instrumentation.agent.AgentRuntime;
import betamoon.instrumentation.hooks.audio.StepSoundCallbacks;
import betamoon.resources.EnumTexAtlas;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import net.minecraft.src.Block;
import net.minecraft.src.ModLoader;
import net.minecraft.src.Session;

/**
 * Runs the public API inside the real parser/lifecycle, including recoverable
 * Lua errors.
 */
public final class AssetLuaApiTest {
    private AssetLuaApiTest() {
    }

    public static void main(String[] arguments) throws Exception {
        System.setProperty(AgentRuntime.STATUS_PROPERTY, "ACTIVE");
        AgentRuntime.getReport().applied("betamoon:step_sound:play", "test");
        Path root = Files.createTempDirectory("betamoon-lua-assets-");
        try {
            Files.write(root.resolve("guard.png"), AssetResourceTest.png(0xff00ff00, 16));
            ImageIO.write(new BufferedImage(16, 32, BufferedImage.TYPE_INT_ARGB), "png",
                    root.resolve("strip.png").toFile());
            Files.write(root.resolve("hit.wav"), AssetResourceTest.wav());
            Files.write(root.resolve("broken.png"), new byte[]{0});
            Path textureFolder = root.resolve("mymod/textures/lessons");
            Files.createDirectories(textureFolder);
            Files.write(textureFolder.resolve("mosaic.png"), AssetResourceTest.png(0xff00ff00, 16));
            Path soundFolder = root.resolve("mymod/sounds/machine");
            Files.createDirectories(soundFolder);
            Files.write(soundFolder.resolve("click.wav"), AssetResourceTest.wav());
            Path packFile = root.resolve("alternate-sound.zip");
            try (ZipOutputStream pack = new ZipOutputStream(Files.newOutputStream(packFile))) {
                pack.putNextEntry(new ZipEntry("bm_assets/mymod/sounds/machine/click.ogg"));
                pack.write(Files.readAllBytes(Paths.get("testsrc/betamoon/assets/fixtures/tone.ogg")));
                pack.closeEntry();
            }
            List<String> warnings = new ArrayList<>();
            ClientAssets.useProviders(new FileAssetProvider(root.toFile()), new ZipAssetProvider(packFile.toFile()),
                    warnings::add);
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
                    + " local moving=betamoon.assets.textures:add{key='mymod:moving',path='strip.png',"
                    + "animation={frameTicks=2}}\n"
                    + " assert(not pcall(function() betamoon.assets.textures:add{key='mymod:bad_anim',"
                    + "path='strip.png',animation={layout='grid'}} end))\n"
                    + " assert(t:getKey()=='mymod:guard' and t:getKind()=='textures')\n"
                    + " assert(t:getPath()=='guard.png' and t:getOverridePath()=='bm_assets/guard.png')\n"
                    + " local inferred=betamoon.assets.textures:add{key='mymod:lessons/mosaic'}\n"
                    + " assert(inferred:getPath()=='mymod/textures/lessons/mosaic.png')\n"
                    + " assert(inferred:getOverridePath()=='bm_assets/mymod/textures/lessons/mosaic.png')\n"
                    + " assert(not pcall(function() betamoon.assets.textures:add{key='mymod:slate.png'} end))\n"
                    + " assert(t:getSource().kind=='script')\n"
                    + " assert(betamoon.assets.textures:get('mymod:guard')~=nil)\n"
                    + " assert(betamoon.assets.textures:get('other:guard')==nil)\n"
                    + " assert(not pcall(function() betamoon.assets.textures:getRequired('other:guard') end))\n"
                    + " assert(not pcall(function() betamoon.assets.textures:add{key='mymod:guard',path='guard.png'} end))\n"
                    + " assert(betamoon.assets.textures:getRequired('mymod:guard')~=nil)\n"
                    + " assert(not pcall(function() betamoon.assets.textures:add{key='mymod:broken',path='broken.png'} end))\n"
                    + " assert(betamoon.assets.textures:get('mymod:broken')==nil)\n"
                    + " betamoon.blocks:add{id=231,key='asset_block',material='rock',texture=t,textures={top=moving}}\n"
                    + " betamoon.items:add{id=29001,key='asset_item',texture=t}\n"
                    + " betamoon.items:getRequired(29001):override{texture=t}\n"
                    + " local s=betamoon.assets.sounds:add{key='mymod:guard',path='hit.wav'}\n"
                    + " local click=betamoon.assets.sounds:add{key='mymod:machine/click'}\n"
                    + " assert(click:getPath()=='mymod/sounds/machine/click.wav')\n"
                    + " assert(click:getOverridePath()=='bm_assets/mymod/sounds/machine/click.wav')\n"
                    + " assert(click:getSource().kind=='script')\n"
                    + " assert(s:getKind()=='sounds' and s:getSource().kind=='script')\n"
                    + " local event=betamoon.soundEvents:add{key='mymod:hit',clips={{sound=s,weight=2},{sound='hit.wav'}},pitch={min=0.9,max=1.1}}\n"
                    + " assert(event:getKey()=='mymod:hit' and betamoon.soundEvents:get('mymod:hit')~=nil)\n"
                    + " assert(event.key=='mymod:hit' and event.enabled and math.abs(event.pitch.min-0.9)<0.0001)\n"
                    + " assert(betamoon.soundEvents:one{key='mymod:hit'}.key==event.key)\n"
                    + " local step=betamoon.stepSounds:add{key='mymod:step_sound/hit',event=event,volume=0.8}\n"
                    + " assert(betamoon.stepSounds:getRequired(step.key).key==step.key)\n"
                    + " assert(betamoon.stepSounds:get('stone')=='stone')\n"
                    + " assert(betamoon.stepSounds:get('missing')==nil)\n"
                    + " betamoon.blocks:add{id=232,key='asset_step_block',material=betamoon.materials.blocks:getRequired('rock'),stepSound=step}\n"
                    + " assert(betamoon.blocks:getRequired(232).stepSound==step.key)\n"
                    + " assert(not pcall(function() betamoon.stepSounds:add{key='mymod:step_sound/invalid',event='mymod:absent'} end))\n"
                    + " local low=event:override{priority=1,changes={volume=0.5,range=20}}\n"
                    + " local high=event:override{priority=2,changes={volume=0.25,enabled=false}}\n"
                    + " assert(event.volume==0.25 and event.range==20 and not event.enabled)\n"
                    + " high:remove(); assert(event.volume==0.5 and event.enabled)\n"
                    + " low:remove(); assert(event.volume==1 and event.range==16)\n"
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
            require(StepSoundCallbacks.play(Block.blocksList[232].stepSound.stepSoundDir(), 0, 0, 0, 0, 1) == 1,
                    "Custom step sound reaches the audio marker callback");
            require(StepSoundCallbacks.play("step.stone", 0, 0, 0, 1, 1) == 0,
                    "Vanilla step sounds keep their native playback path");
            ScriptResourceTracker.unload("asset_api_test.lua");
            String consumer = "name='Asset cleanup test'\nfunction modInit()\n"
                    + "assert(betamoon.assets.textures:get('mymod:guard')==nil)\n"
                    + "assert(betamoon.assets.textures:get('mymod:moving')==nil)\n"
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
            System.clearProperty(AgentRuntime.STATUS_PROPERTY);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

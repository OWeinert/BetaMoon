package betamoon.luamodloader;

import betamoon.assets.io.FileAssetProvider;
import betamoon.client.assets.AssetResourceTest;
import betamoon.client.assets.ClientAssets;
import betamoon.fluid.FluidRegistry;
import betamoon.instrumentation.agent.AgentRuntime;
import betamoon.instrumentation.agent.AgentStatus;
import betamoon.instrumentation.hooks.audio.StepSoundCallbacks;
import betamoon.luaapi.config.ScriptConfigRegistration;
import betamoon.luaapi.config.ScriptConfigRegistry;
import betamoon.luaapi.material.WorldMaterialSignatures;
import betamoon.luaapi.module.ModuleApi;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.src.Block;
import net.minecraft.src.ModLoader;
import net.minecraft.src.RegionFileCache;
import net.minecraft.src.SaveOldDir;
import net.minecraft.src.Session;
import net.minecraft.src.World;
import net.minecraft.src.WorldInfo;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** One isolated process exercising the integrated 0.11 public APIs and save contracts. */
public final class Integrated011Test {
    private Integrated011Test() {
    }

    public static void main(String[] arguments) throws Exception {
        Path root = Files.createTempDirectory("betamoon-011-integration-");
        Path scripts = root.resolve("betamoon/scripts");
        Files.createDirectories(scripts);
        Files.write(scripts.resolve("hit.wav"), AssetResourceTest.wav());
        Files.write(scripts.resolve("integration.lua"), source().getBytes(StandardCharsets.UTF_8));
        Field blockList = ModLoader.class.getDeclaredField("field_blockList");
        blockList.setAccessible(true);
        Object previousBlockList = blockList.get(null);
        Field initialized = ModLoader.class.getDeclaredField("hasInit");
        initialized.setAccessible(true);
        boolean previousInit = initialized.getBoolean(null);
        blockList.set(null, Session.class.getDeclaredFields()[0]);
        initialized.setBoolean(null, true);
        if (Boolean.getBoolean("betamoon.integration.requireAgent")) {
            require(AgentRuntime.getStatus() == AgentStatus.ACTIVE,
                    "Integrated agent test requires the packaged instrumentation agent");
        } else {
            enableTestHooks();
        }
        ClientAssets.useProviders(new FileAssetProvider(scripts.toFile()), null, message -> {
        });
        try {
            LuaModLoader loader = new LuaModLoader(scripts.toFile());
            loader.loadAndRun();
            ScriptMod loaded = LuaScriptRegistry.findByFile("integration.lua");
            require(loaded != null && loaded.isLoaded(), "Integrated script must load successfully");
            require(Block.blocksList[233] != null && FluidRegistry.instance().getByBlockId(231) != null,
                    "Ordinary block and paired custom-material fluid must register together");
            require(Block.blocksList[233].blockMaterial != FluidRegistry.instance().getByBlockId(231)
                    .definition().carrier().material(), "Solid block and fluid keep distinct native materials");
            require(StepSoundCallbacks.play(Block.blocksList[233].stepSound.stepSoundDir(), 0, 0, 0, 1, 1) == 1,
                    "Packaged audio step sound must reach the custom playback route");

            LuaTable module = new LuaTable();
            ModuleApi.attach(module);
            LuaValue exports = module.get("modules").get("import").call(LuaValue.valueOf("integration_state"));
            require("before".equals(exports.get("message").call().checkjstring()),
                    "Running callback must read the initial config state");
            List<ScriptConfigRegistration> configs = ScriptConfigRegistry.forScript("integration.lua");
            require(configs.size() == 1, "Integrated script must publish one editable config");
            java.util.Map<String, Object> draft = configs.get(0).draft();
            draft.put("message", "after");
            configs.get(0).saveDraft(draft);
            ScriptConfigRegistry.tick();
            require("after".equals(exports.get("message").call().checkjstring()),
                    "Saving config must update the running callback without a script reload");

            Path saveRoot = root.resolve("saves");
            SaveOldDir save = new SaveOldDir(saveRoot.toFile(), "world", false);
            WorldInfo info = new WorldInfo(42, "world");
            info.setSpawn(0, 65, 0);
            save.saveWorldInfo(info);
            World world = new World(save, "world", 42, null);
            world.setBlockWithNotify(0, 65, 0, 233);
            world.saveWorld(true, null);
            Path worldPath = saveRoot.resolve("world");
            WorldMaterialSignatures.record(worldPath);
            WorldMaterialSignatures.preflight(worldPath);
            World reopened = new World(new SaveOldDir(saveRoot.toFile(), "world", false), "world", 42, null);
            require(reopened.getBlockId(0, 65, 0) == 233, "Saved custom block must survive world reopen");

            loader.reloadAll();
            require(LuaScriptRegistry.findByFile("integration.lua").isLoaded(),
                    "Integrated script must survive a compatible hot reload");
            LuaTable reloadedModule = new LuaTable();
            ModuleApi.attach(reloadedModule);
            exports = reloadedModule.get("modules").get("import").call(LuaValue.valueOf("integration_state"));
            require("after".equals(exports.get("message").call().checkjstring()),
                    "Hot reload must restore saved config values");
            configs = ScriptConfigRegistry.forScript("integration.lua");

            Path configFile = root.resolve("betamoon/config/integration.json");
            byte[] validConfig = Files.readAllBytes(configFile);
            Files.write(configFile, "{invalid".getBytes(StandardCharsets.UTF_8));
            boolean rejected = false;
            try {
                configs.get(0).reloadDraftFromDisk();
            } catch (java.io.IOException expected) {
                rejected = true;
            }
            require(rejected && "after".equals(exports.get("message").call().checkjstring()),
                    "Invalid disk edits must not change the running callback");
            require("{invalid".equals(new String(Files.readAllBytes(configFile), StandardCharsets.UTF_8)),
                    "Rejected config reload must not replace the invalid file");
            Files.write(configFile, validConfig);
            configs.get(0).reloadDraftFromDisk();

            String changedMaterial = source().replace("kind='solid',mapColor='stone'",
                    "kind='solid',mapColor='ice'");
            Files.write(scripts.resolve("integration.lua"), changedMaterial.getBytes(StandardCharsets.UTF_8));
            loader.reloadAll();
            require(!LuaScriptRegistry.findByFile("integration.lua").isLoaded(),
                    "A hot reload changing native material structure must fail");
            boolean structuralIssue = false;
            for (LuaScriptErrors.ScriptIssue issue : LuaScriptErrors.getIssuesFor("Integrated 0.11",
                    "integration.lua")) {
                structuralIssue |= issue.getMessage().contains("changed native properties");
            }
            require(structuralIssue, "Structural reload failure must explain the native material mismatch");
            WorldMaterialSignatures.preflight(worldPath);
            System.out.println("Integrated 0.11 checks passed: material fluid/block, packaged step sound, "
                    + "live config, world reopen, structural rejection and invalid config edit.");
        } finally {
            RegionFileCache.func_22192_a();
            ScriptResourceTracker.unloadAll();
            LuaScriptRegistry.clear();
            blockList.set(null, previousBlockList);
            initialized.setBoolean(null, previousInit);
            System.clearProperty(AgentRuntime.STATUS_PROPERTY);
            AssetResourceTest.deleteTree(root);
        }
    }

    private static void enableTestHooks() {
        System.setProperty(AgentRuntime.STATUS_PROPERTY, "ACTIVE");
        AgentRuntime.getReport().applied("betamoon:step_sound:play", "integration test");
        for (String id : new String[]{"falling", "farmland", "reeds", "habitat", "habitat_position",
                "pathfinding", "sponge", "boat"}) {
            AgentRuntime.getReport().applied("betamoon:fluid_ecology:" + id, "integration test");
        }
        for (String id : new String[]{"water_movement", "lava_movement", "eye_material", "entity_tick",
                "native_lava_damage", "breathing", "living_tick", "item_water_movement", "item_lava_material"}) {
            AgentRuntime.getReport().applied("betamoon:fluid_contact:" + id, "integration test");
        }
        for (String id : new String[]{"wet", "air_hud", "updateFogColor_material", "updateFogColor",
                "setupFog_material", "setupFog", "overlay_visibility", "overlay_texture"}) {
            AgentRuntime.getReport().applied("betamoon:fluid_camera:" + id, "integration test");
        }
    }

    private static String source() {
        return "name='Integrated 0.11'\n"
                + "local active='before'\n"
                + "local config=betamoon.config('integration',{message='before'},"
                + "{onChange=function(values) active=values.message end})\n"
                + "active=config.message\n"
                + "function modInit()\n"
                + " local sound=betamoon.assets.sounds:add{key='integration:hit',path='hit.wav'}\n"
                + " local event=betamoon.soundEvents:add{key='integration:step',sound=sound}\n"
                + " local step=betamoon.stepSounds:add{key='integration:step_sound/tile',event=event}\n"
                + " local solid=betamoon.materials.blocks:add{key='integration:material/ceramic',"
                + "kind='solid',mapColor='stone'}\n"
                + " local liquid=betamoon.materials.blocks:add{key='integration:material/brine',"
                + "kind='liquid',mapColor='water'}\n"
                + " betamoon.blocks:add{id=233,key='integration:block/tile',material=solid,"
                + "texture=1,stepSound=step}\n"
                + " betamoon.fluids:add{key='integration:fluid/brine',material=liquid,"
                + "blocks={flowing={id=231,key='integration:block/brine_flowing'},"
                + "still={id=232,key='integration:block/brine_still'}},"
                + "appearance={still=17,flowing=18}}\n"
                + " betamoon.modules:export('integration_state',{message=function() return active end})\n"
                + "end\n";
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

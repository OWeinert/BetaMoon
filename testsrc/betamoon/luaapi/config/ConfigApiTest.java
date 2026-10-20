package betamoon.luaapi.config;

import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/** Exercises config file paths, table access, explicit writes and safe reloads. */
public final class ConfigApiTest {
    private ConfigApiTest() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("betamoon-config-api-");
        try {
            Globals lua = JsePlatform.standardGlobals();
            LuaTable module = new LuaTable();
            ConfigApi.attach(module, root.toFile(), "config_api_test.lua");
            lua.set("betamoon", module);

            run(lua, "config=betamoon.config('mymod',{enemyHealth=1.0,loot={dropRate=0.25},"
                    + "enabled=true,tags={'rare','magic'}})\n"
                    + "assert(type(config)=='table' and config.enemyHealth==1 and config.loot.dropRate==0.25)\n"
                    + "local count=0; for key in pairs(config) do count=count+1; "
                    + "assert(key~='save' and key~='reload') end; assert(count==4)\n"
                    + "assert(type(config.save)=='function' and type(config.reload)=='function')\n");
            run(lua, "assert(not pcall(function() config.save=1 end))\n");
            Path flat = root.resolve("mymod.json");
            require(Files.isRegularFile(flat), "A simple key creates a flat config file");

            run(lua, "config.enemyHealth=1.25; config.loot.dropRate=0.5; config.enabled=nil; "
                    + "config.tags={}; assert(config.save())\n"
                    + "assert(config.enabled and #config.tags==0)\n"
                    + "heldLoot=config.loot; config.enemyHealth=9; assert(config.reload())\n"
                    + "assert(config.enemyHealth==1.25 and config.loot.dropRate==0.5)\n"
                    + "assert(config.loot==heldLoot)\n");
            require(read(flat).contains("\"enemyHealth\": 1.25"), "Explicit save writes current values");
            require(read(flat).contains("\"tags\": []"), "An emptied declared array stays a JSON array");

            Files.write(flat, "{\"enemyHealth\":2,\"loot\":{\"dropRate\":0.75}}"
                    .getBytes(StandardCharsets.UTF_8));
            run(lua, "assert(not pcall(function() config.save() end))\n"
                    + "assert(config.reload())\n"
                    + "assert(config.enemyHealth==2 and config.loot.dropRate==0.75 and config.enabled)\n"
                    + "assert(config.loot==heldLoot)\n");
            Files.write(flat, "{broken".getBytes(StandardCharsets.UTF_8));
            run(lua, "assert(not pcall(function() config.reload() end))\n"
                    + "assert(config.enemyHealth==2 and config.loot.dropRate==0.75)\n");
            require(read(flat).equals("{broken"), "Failed reload leaves the file untouched");

            run(lua, "named=betamoon.config('mymod:gameplay',{difficulty=3})\n"
                    + "assert(named.difficulty==3)\n"
                    + "assert(not pcall(function() betamoon.config('../escape',{}) end))\n"
                    + "assert(not pcall(function() betamoon.config('mymod:../escape',{}) end))\n"
                    + "assert(not pcall(function() betamoon.config('bad',{save=1}) end))\n"
                    + "named.difficulty='hard'; assert(not pcall(function() named.save() end))\n");
            require(Files.isRegularFile(root.resolve("mymod/gameplay.json")),
                    "A named key creates a file below its namespace folder");
            require(!Files.exists(root.resolve("escape.json")), "Invalid keys cannot escape the config folder");

            Path broken = root.resolve("broken.json");
            Files.write(broken, "not-json".getBytes(StandardCharsets.UTF_8));
            run(lua, "broken=betamoon.config('broken',{enabled=true})\n"
                    + "assert(broken.enabled and broken.save())\n");
            require(read(broken).contains("\"enabled\": true"),
                    "An invalid initial file uses defaults and can be replaced explicitly");

            run(lua, "changes=0; paths=nil; ui=betamoon.config('mymod:ui',"
                    + "{volume=2,enabled=true,nested={ratio=0.5},items={}},"
                    + "{label='Gameplay',fields={volume={label='Volume',min=0,max=10,step=1},"
                    + "enabled={readOnly=true,readOnlyReason='Startup choice'},"
                    + "items={element={default='new'},maxItems=3},"
                    + "nested={fields={ratio={min=0,max=1}}}},"
                    + "onChange=function(values,previous,changed) changes=changes+1;"
                    + " paths=changed; assert(previous.volume~=values.volume) end})\n");
            ScriptConfigRegistration ui = find("config_api_test.lua", "mymod:ui");
            require(ui.fields().get("items").defaultValue() instanceof List,
                    "Element metadata retains an empty array default");
            Map<String, Object> draft = ui.draft();
            draft.put("volume", Double.valueOf(4));
            @SuppressWarnings("unchecked")
            List<Object> items = (List<Object>) draft.get("items");
            items.add("first");
            ui.saveDraft(draft);
            ScriptConfigRegistry.tick();
            run(lua, "assert(ui.volume==4 and ui.items[1]=='first' and changes==1)\n"
                    + "assert(paths[1]=='volume' or paths[2]=='volume')\n");
            ui.saveDraft(ui.draft());
            ScriptConfigRegistry.tick();
            run(lua, "assert(changes==1)\n");
            Map<String, Object> invalid = ui.draft();
            invalid.put("volume", Double.valueOf(11));
            require(!canSave(ui, invalid), "GUI draft enforces numeric bounds");
            Map<String, Object> locked = ui.draft();
            locked.put("enabled", Boolean.FALSE);
            require(!canSave(ui, locked), "GUI draft cannot change a read-only field");
            require(read(root.resolve("mymod/ui.json")).contains("\"volume\": 4"),
                    "Rejected GUI draft does not overwrite the file");
            Files.write(root.resolve("mymod/ui.json"), "{\"volume\":7}".getBytes(StandardCharsets.UTF_8));
            require(!canSave(ui, ui.draft()), "GUI draft detects an external disk revision");
            ScriptConfigRegistration.DiskDraft diskDraft = ui.reloadDraftFromDisk();
            Map<String, Object> reloadedDraft = diskDraft.values();
            require(((Number) reloadedDraft.get("volume")).intValue() == 7,
                    "Explicit draft reload accepts external edits");
            require(!canSave(ui, reloadedDraft),
                    "Staging disk values does not silently advance the live table's revision");
            run(lua, "ui.volume=6; assert(not pcall(function() ui.save() end)); ui.volume=4\n");
            ui.saveDraft(reloadedDraft, ui.draft(), diskDraft);
            ScriptConfigRegistry.tick();
            run(lua, "assert(ui.volume==7 and changes==2)\n");
            Map<String, Object> stale = ui.draft();
            run(lua, "ui.volume=8\n");
            require(!canSave(ui, stale, stale), "GUI draft cannot overwrite unsaved Lua changes");
            run(lua, "ui.volume=7\n");
            run(lua, "assert(not pcall(function() betamoon.config('mymod:ui',{volume=1}) end))\n");

            run(lua, "failApply=true; applied=0; unstable=betamoon.config('mymod:unstable',"
                    + "{value=1},{onChange=function(values,previous,paths) "
                    + "if failApply then error('transient failure') end; applied=applied+1 end})\n"
                    + "unstable.value=2; assert(unstable.save())\n");
            ScriptConfigRegistry.tick();
            ScriptConfigRegistration unstable = find("config_api_test.lua", "mymod:unstable");
            require(unstable.applyError() != null, "Callback failures remain visible after disk commit");
            run(lua, "assert(unstable.value==2 and applied==0); failApply=false\n");
            unstable.retryApply();
            ScriptConfigRegistry.tick();
            require(unstable.applyError() == null, "Retry clears the apply failure after success");
            run(lua, "assert(applied==1)\n");

            Globals scoped = JsePlatform.standardGlobals();
            LuaTable scopedModule = new LuaTable();
            ConfigApi.attach(scopedModule, root.toFile(), "scoped.lua");
            scoped.set("betamoon", scopedModule);
            ScriptExecution.invoke("scoped.lua", scoped.load("betamoon.config('scoped',{value=1})"), LuaValue.NONE);
            require(ScriptConfigRegistry.forScript("scoped.lua").size() == 1,
                    "Config opened in a script scope is registered");
            ScriptResourceTracker.unload("scoped.lua");
            require(ScriptConfigRegistry.forScript("scoped.lua").isEmpty(),
                    "Unloading the script removes its editable definition");
            System.out.println("Script config API passed: paths, table access, save, reload, validation "
                    + "file conflicts, GUI drafts and live callbacks.");
        } finally {
            deleteIfPresent(root.resolve("scoped.json"));
            deleteIfPresent(root.resolve("mymod/unstable.json"));
            deleteIfPresent(root.resolve("mymod/ui.json"));
            deleteIfPresent(root.resolve("mymod/gameplay.json"));
            deleteIfPresent(root.resolve("mymod"));
            deleteIfPresent(root.resolve("mymod.json"));
            deleteIfPresent(root.resolve("broken.json"));
            deleteIfPresent(root);
        }
    }

    private static void run(Globals lua, String source) {
        lua.load(source, "config_api_test.lua").call();
    }

    private static ScriptConfigRegistration find(String owner, String key) {
        for (ScriptConfigRegistration registration : ScriptConfigRegistry.forScript(owner)) {
            if (registration.key().equals(key)) {
                return registration;
            }
        }
        throw new AssertionError("Missing config registration: " + key);
    }

    private static boolean canSave(ScriptConfigRegistration registration, Map<String, Object> draft) {
        try {
            registration.saveDraft(draft);
            return true;
        } catch (IOException | RuntimeException expected) {
            return false;
        }
    }

    private static boolean canSave(ScriptConfigRegistration registration, Map<String, Object> draft,
            Map<String, Object> expectedLive) {
        try {
            registration.saveDraft(draft, expectedLive);
            return true;
        } catch (IOException | RuntimeException expected) {
            return false;
        }
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static void deleteIfPresent(Path path) throws IOException {
        Files.deleteIfExists(path);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

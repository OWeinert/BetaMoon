package betamoon.luaapi.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
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
            System.out.println("Script config API passed: paths, table access, save, reload, validation "
                    + "and file conflicts.");
        } finally {
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

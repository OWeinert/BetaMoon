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

/**
 * Exercises config file paths, table access, explicit writes and safe reloads.
 */
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
                    + "config.tags={}; assert(config.save())\n" + "assert(config.enabled and #config.tags==0)\n"
                    + "heldLoot=config.loot; config.enemyHealth=9; assert(config.reload())\n"
                    + "assert(config.enemyHealth==1.25 and config.loot.dropRate==0.5)\n"
                    + "assert(config.loot==heldLoot)\n");
            require(read(flat).contains("\"enemyHealth\": 1.25"), "Explicit save writes current values");
            require(read(flat).contains("\"tags\": []"), "An emptied declared array stays a JSON array");

            Files.write(flat, "{\"enemyHealth\":2,\"loot\":{\"dropRate\":0.75}}".getBytes(StandardCharsets.UTF_8));
            run(lua, "assert(not pcall(function() config.save() end))\n" + "assert(config.reload())\n"
                    + "assert(config.enemyHealth==2 and config.loot.dropRate==0.75 and config.enabled)\n"
                    + "assert(config.loot==heldLoot)\n");
            Files.write(flat, "{broken".getBytes(StandardCharsets.UTF_8));
            run(lua, "assert(not pcall(function() config.reload() end))\n"
                    + "assert(config.enemyHealth==2 and config.loot.dropRate==0.75)\n");
            require(read(flat).equals("{broken"), "Failed reload leaves the file untouched");

            run(lua, "named=betamoon.config('mymod:gameplay',{difficulty=3})\n" + "assert(named.difficulty==3)\n"
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

            Path schema = root.resolve("schema.json");
            Files.write(schema, "{\"oldName\":5,\"extra\":\"kept\",\"nested\":{\"value\":1}}"
                    .getBytes(StandardCharsets.UTF_8));
            run(lua, "migrationCalls=0; migrated=betamoon.config('schema',{newName=1},"
                    + "{schemaVersion=2,migrate=function(old,version) "
                    + "migrationCalls=migrationCalls+1; assert(version==0 and old.oldName==5); "
                    + "assert(not pcall(function() old.oldName=7 end)); "
                    + "assert(not pcall(function() old.nested.value=7 end)); "
                    + "return {newName=old.oldName,extra=old.extra} end})\n"
                    + "assert(migrated.newName==5 and migrated.extra=='kept' and migrationCalls==1)\n");
            require(read(schema).contains("\"$schemaVersion\": 2")
                    && !read(schema).contains("oldName"), "Migration rewrites the validated result atomically");
            run(lua, "assert(migrated.reload() and migrationCalls==1); migrated.newName=6; "
                    + "assert(migrated.save())\n");
            require(read(schema).contains("\"newName\": 6"), "Versioned saves retain the schema marker");

            Path rejectedSchema = root.resolve("rejected_schema.json");
            String invalidMigration = "{\"oldName\":7}";
            Files.write(rejectedSchema, invalidMigration.getBytes(StandardCharsets.UTF_8));
            run(lua, "rejected=betamoon.config('rejected_schema',{newName=1},"
                    + "{schemaVersion=1,migrate=function(old) return {newName='wrong'} end})\n"
                    + "assert(rejected.newName==1 and not pcall(function() rejected.save() end))\n");
            require(read(rejectedSchema).equals(invalidMigration), "Failed migration preserves the original file");
            Files.write(rejectedSchema, "{\"$schemaVersion\":4,\"newName\":9}"
                    .getBytes(StandardCharsets.UTF_8));
            run(lua, "assert(not pcall(function() rejected.reload() end))\n"
                    + "assert(not pcall(function() rejected.save() end))\n");
            require(read(rejectedSchema).contains("\"$schemaVersion\":4"),
                    "A newer schema cannot be overwritten by fallback defaults");

            run(lua, "changes=0; paths=nil; ui=betamoon.config('mymod:ui',"
                    + "{volume=2,enabled=true,nested={ratio=0.5},items={}},"
                    + "{label='Gameplay',fields={volume={label='Volume',min=0,max=10,step=1},"
                    + "enabled={readOnly=true,readOnlyReason='Startup choice'},"
                    + "items={element={default='new'},maxItems=3}," + "nested={fields={ratio={min=0,max=1}}}},"
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

            run(lua, "inputs=betamoon.config('mymod:inputs',"
                    + "{title='Ada',mode='normal',count=2,speed=0.5,level=2,enabled=true,items={}},"
                    + "{fields={mode={input='choice',choices={{value='normal',label='Normal'},"
                    + "{value='hard',label='Hard'}}}," + "count={input='choice',choices={{value=1,label='Low'},2,3}},"
                    + "speed={input='slider',min=0,max=1,step=0.25}," + "level={input='cycle',choices={1,2,3}},"
                    + "items={element={default=1,input='slider',min=0,max=4,step=1}}}})\n");
            ScriptConfigRegistration inputs = find("config_api_test.lua", "mymod:inputs");
            require("text".equals(inputs.fields().get("title").input()), "Strings default to text input");
            require("cycle".equals(inputs.fields().get("enabled").input()), "Booleans default to a cycle button");
            require("choice".equals(inputs.fields().get("mode").input()), "String choice uses a dropdown");
            require("Normal".equals(inputs.fields().get("mode").choice("normal").label()),
                    "Choice labels are separate from persisted values");
            require("Low".equals(inputs.fields().get("count").choice(Double.valueOf(1)).label()),
                    "Numeric choices accept labels and numeric values");
            require("slider".equals(inputs.fields().get("items").element().input()),
                    "Array elements inherit their declared input method");
            Map<String, Object> invalidChoice = inputs.draft();
            invalidChoice.put("count", Double.valueOf(4));
            require(!canSave(inputs, invalidChoice), "GUI draft rejects a number outside its choices");
            invalidChoice = inputs.draft();
            invalidChoice.put("mode", "unknown");
            require(!canSave(inputs, invalidChoice), "GUI draft rejects a string outside its choices");
            run(lua, "assert(not pcall(function() betamoon.config('mymod:bad-input',{value=1},"
                    + "{fields={value={input='other'}}}) end))\n"
                    + "assert(not pcall(function() betamoon.config('mymod:bad-slider',{value=1},"
                    + "{fields={value={input='slider',min=0,max=2}}}) end))\n"
                    + "assert(not pcall(function() betamoon.config('mymod:bad-choice',{value=1},"
                    + "{fields={value={input='choice',choices={2,3}}}}) end))\n"
                    + "assert(not pcall(function() betamoon.config('mymod:bad-type',{value=true},"
                    + "{fields={value={input='text'}}}) end))\n");

            run(lua, "multi=betamoon.config('mymod:multi',{effects={},audio={music=0.5,ambient=0.25}},"
                    + "{layout={{heading='Visual'},'effects',{divider=true},{heading='Audio'},'audio'},"
                    + "fields={effects={input='multiSelect',choices={{value='fog',label='Fog'},'particles'}},"
                    + "audio={layout={{heading='Levels'},'music','ambient'},fields={music={min=0,max=1}}}}})\n"
                    + "assert(type(multi.effects)=='table' and #multi.effects==0)\n");
            ScriptConfigRegistration multi = find("config_api_test.lua", "mymod:multi");
            require(multi.fields().get("effects").defaultValue() instanceof List,
                    "An empty multi-select default is an array");
            require(multi.layout().size() == 5 && multi.fields().get("audio").layout().size() == 3,
                    "Top-level and nested layouts retain their presentation order");
            Map<String, Object> multiDraft = multi.draft();
            @SuppressWarnings("unchecked")
            List<Object> effects = (List<Object>) multiDraft.get("effects");
            effects.add("particles");
            effects.add("fog");
            multi.saveDraft(multiDraft);
            run(lua, "assert(multi.effects[1]=='particles' and multi.effects[2]=='fog')\n"
                    + "multi.effects={'missing'}; assert(not pcall(function() multi.save() end))\n"
                    + "multi.effects={'particles','fog'}\n");
            require(read(root.resolve("mymod/multi.json")).contains("[\"particles\", \"fog\"]"),
                    "A multi-select saves ordinary JSON arrays without reordering untouched values");
            multiDraft = multi.draft();
            @SuppressWarnings("unchecked")
            List<Object> repeated = (List<Object>) multiDraft.get("effects");
            repeated.add("fog");
            require(!canSave(multi, multiDraft), "A repeated multi-select value cannot be saved");
            String unknownChoice = "{\"effects\":[\"removed\"],\"audio\":{\"music\":0.5,\"ambient\":0.25}}";
            Files.write(root.resolve("mymod/multi.json"), unknownChoice.getBytes(StandardCharsets.UTF_8));
            try {
                multi.reloadDraftFromDisk();
                throw new AssertionError("Removed choices must fail on reload");
            } catch (IOException expected) {
                require(expected.getMessage().contains("effects[1]"), "Reload error identifies the selected value");
            }
            require(read(root.resolve("mymod/multi.json")).equals(unknownChoice),
                    "Failed multi-select reload preserves the original file");
            run(lua, "assert(not pcall(function() betamoon.config('mymod:bad-multi',{values={'fog','fog'}},"
                    + "{fields={values={input='multiSelect',choices={'fog'}}}}) end))\n"
                    + "assert(not pcall(function() betamoon.config('mymod:bad-layout',{value=1},"
                    + "{layout={'unknown'}}) end))\n"
                    + "assert(not pcall(function() betamoon.config('mymod:bad-divider',{value=1},"
                    + "{layout={{divider=true},'value'}}) end))\n");

            run(lua, "collections=betamoon.config('mymod:collections',"
                    + "{tasks={'first'},volumes={music=0.5,effects=0.75},empty={}},"
                    + "{fields={tasks={input='collection',kind='list',expanded=true,maxItems=3,"
                    + "element={default='new',input='text'}},"
                    + "volumes={input='collection',kind='dictionary',maxItems=3,"
                    + "key={input='text'},value={default=0.5,input='slider',min=0,max=1,step=0.25}},"
                    + "empty={input='collection',kind='dictionary',maxItems=2,"
                    + "key={input='text'},value={default='new',input='text'}}}})\n");
            ScriptConfigRegistration collections = find("config_api_test.lua", "mymod:collections");
            require("list".equals(collections.fields().get("tasks").collectionKind()),
                    "List collection retains its entry descriptor");
            require(collections.fields().get("tasks").expanded(), "Collection may opt into initial expansion");
            require("dictionary".equals(collections.fields().get("volumes").collectionKind()),
                    "Dictionary collection retains its key and value descriptors");
            require(!collections.fields().get("volumes").expanded(),
                    "Collections without expanded metadata start closed");
            Map<String, Object> collectionDraft = collections.draft();
            @SuppressWarnings("unchecked")
            Map<String, Object> volumes = (Map<String, Object>) collectionDraft.get("volumes");
            volumes.remove("music");
            volumes.put("voice", Double.valueOf(0.25));
            @SuppressWarnings("unchecked")
            List<Object> tasks = (List<Object>) collectionDraft.get("tasks");
            tasks.add("second");
            collections.saveDraft(collectionDraft);
            run(lua, "assert(collections.volumes.music==nil and collections.volumes.voice==0.25)\n"
                    + "assert(collections.tasks[2]=='second')\n"
                    + "assert(collections.reload() and collections.volumes.music==nil)\n"
                    + "collections.volumes.effects=nil; assert(collections.save())\n"
                    + "assert(collections.reload() and collections.volumes.effects==nil)\n");
            require(!read(root.resolve("mymod/collections.json")).contains("\"music\""),
                    "Removed default dictionary keys remain absent on disk");
            Map<String, Object> badCollection = collections.draft();
            @SuppressWarnings("unchecked")
            Map<String, Object> badVolumes = (Map<String, Object>) badCollection.get("volumes");
            badVolumes.put("invalid", "wrong type");
            require(!canSave(collections, badCollection), "Dictionary values follow the shared descriptor");
            Map<String, Object> tooManyEntries = collections.draft();
            @SuppressWarnings("unchecked")
            List<Object> extraTasks = (List<Object>) tooManyEntries.get("tasks");
            extraTasks.add("third");
            extraTasks.add("fourth");
            require(!canSave(collections, tooManyEntries), "Collection maxItems limits GUI drafts");
            run(lua, "collections.tasks[3]='third'; collections.tasks[4]='fourth'; "
                    + "assert(collections.save() and collections.reload() and #collections.tasks==4)\n");
            run(lua, "assert(not pcall(function() betamoon.config('mymod:bad-kind',{values={1}},"
                    + "{fields={values={input='collection',kind='dictionary',key={input='text'},"
                    + "value={default=1}}}}) end))\n"
                    + "assert(not pcall(function() betamoon.config('mymod:bad-value',{values={a=1}},"
                    + "{fields={values={input='collection',kind='dictionary',key={input='text'},"
                    + "value={default='text'}}}}) end))\n"
                    + "assert(not pcall(function() betamoon.config('mymod:bad-expanded-scalar',{value=1},"
                    + "{fields={value={expanded=true}}}) end))\n"
                    + "assert(not pcall(function() betamoon.config('mymod:bad-expanded-type',{value={a=1}},"
                    + "{fields={value={expanded='yes'}}}) end))\n");

            run(lua, "compact=betamoon.config('mymod:compact',"
                    + "{tags={'red','blue'},rates={music=0.5},volumes={music=0.5,effects=0.75}},"
                    + "{fields={tags={input='text',kind='list',element={default='new'}},"
                    + "rates={input='text',kind='dictionary',key={input='text'},value={default=0.5}},"
                    + "volumes={expanded=true,fields={music={min=0,max=1},effects={min=0,max=1}}}}})\n");
            ScriptConfigRegistration compact = find("config_api_test.lua", "mymod:compact");
            require("list".equals(compact.fields().get("tags").collectionKind())
                    && "text".equals(compact.fields().get("tags").input()),
                    "Compact list keeps its element descriptor");
            require("dictionary".equals(compact.fields().get("rates").collectionKind())
                    && "text".equals(compact.fields().get("rates").input()),
                    "Compact dictionary keeps its key and value descriptors");
            require(compact.fields().get("volumes").collectionKind() == null
                    && compact.fields().get("volumes").children().size() == 2
                    && compact.fields().get("volumes").expanded(),
                    "Declared nested fields may opt into initial expansion");
            run(lua, "assert(not pcall(function() betamoon.config('mymod:bad-text-kind',{values={'a'}},"
                    + "{fields={values={input='text',element={default='new'}}}}) end))\n"
                    + "assert(not pcall(function() betamoon.config('mymod:bad-fixed-text',{values={a=1}},"
                    + "{fields={values={input='text',fields={a={}}}}}) end))\n"
                    + "assert(not pcall(function() betamoon.config('mymod:bad-text-value',{values={{x=1}}},"
                    + "{fields={values={input='text',kind='list',element={default={x=1}}}}}) end))\n"
                    + "assert(not pcall(function() betamoon.config('mymod:bad-text-expanded',{values={'a'}},"
                    + "{fields={values={input='text',kind='list',expanded=true,element={default='new'}}}}) end))\n");

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
            System.out
                    .println("Script config API passed: paths, table access, save, reload, input metadata, validation "
                            + "file conflicts, GUI drafts and live callbacks.");
        } finally {
            deleteIfPresent(root.resolve("scoped.json"));
            deleteIfPresent(root.resolve("mymod/unstable.json"));
            deleteIfPresent(root.resolve("mymod/inputs.json"));
            deleteIfPresent(root.resolve("mymod/multi.json"));
            deleteIfPresent(root.resolve("mymod/collections.json"));
            deleteIfPresent(root.resolve("mymod/compact.json"));
            deleteIfPresent(root.resolve("mymod/ui.json"));
            deleteIfPresent(root.resolve("mymod/gameplay.json"));
            deleteIfPresent(root.resolve("mymod"));
            deleteIfPresent(root.resolve("mymod.json"));
            deleteIfPresent(root.resolve("broken.json"));
            deleteIfPresent(root.resolve("schema.json"));
            deleteIfPresent(root.resolve("rejected_schema.json"));
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

package betamoon.hooks;

import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.interop.catalog.CatalogJson;
import betamoon.luamodloader.HookPackages;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Package integrity, root selection and atomic owner preparation using the real
 * shared source discovery.
 */
final class HookPackageTest {
    static int verify() throws Exception {
        int checks = 0;
        Path work = Files.createTempDirectory(Paths.get("build").toAbsolutePath(), "hook-packages-");
        Path instance = Files.createDirectories(work.resolve("instance"));
        Path scripts = Files.createDirectories(instance.resolve("betamoon/scripts"));
        Path root = Files.createDirectories(scripts.resolve("owner/hooks"));
        Files.write(root.getParent().resolve("betamoon.mod.json"),
                "{\"name\":\"Package test\",\"entrypoint\":\"main.lua\"}".getBytes(StandardCharsets.UTF_8));
        Files.write(root.getParent().resolve("main.lua"),
                "error('entrypoint must remain dormant')".getBytes(StandardCharsets.UTF_8));
        Path json = root.getParent().resolve(HookPackages.FILE_NAME);
        Files.write(root.resolve("valid.lua"),
                "local r=betamoon.hooks:createRegistry()".getBytes(StandardCharsets.UTF_8));
        List<String> failures = new ArrayList<>();
        Files.write(json, "[\"hooks/valid.lua\"]".getBytes(StandardCharsets.UTF_8));
        require(HookPackages.discover(scripts.toFile(), failures).size() == 1 && failures.isEmpty(), "valid package");
        checks++;
        for (String text : Arrays.asList("{}", "[null]", "[\"\"]", "[\"../outside.lua\"]", "[\"/absolute.lua\"]",
                "[\"C:/absolute.lua\"]", "[\"hooks\\\\valid.lua\"]", "[\"hooks/./valid.lua\"]",
                "[\"hooks/valid.lua\",\"hooks/valid.lua\"]", "[\"hooks/valid.lua\",\"HOOKS/VALID.LUA\"]",
                "[\"hooks/Valid.lua\"]", "[\"hooks/missing.lua\"]", "[\"hooks/\\u0000.lua\"]")) {
            Files.write(json, text.getBytes(StandardCharsets.UTF_8));
            failures.clear();
            require(HookPackages.discover(scripts.toFile(), failures).isEmpty() && !failures.isEmpty(),
                    "invalid path list accepted: " + text);
            checks++;
        }
        Files.write(root.resolve("invalid.lua"), new byte[]{(byte) 0xC3, (byte) 0x28});
        Files.write(json, "[\"hooks/invalid.lua\"]".getBytes(StandardCharsets.UTF_8));
        failures.clear();
        require(HookPackages.discover(scripts.toFile(), failures).isEmpty() && !failures.isEmpty(),
                "invalid UTF-8 accepted");
        checks++;
        Files.write(root.resolve("oversized.lua"), new byte[262145]);
        Files.write(json, "[\"hooks/oversized.lua\"]".getBytes(StandardCharsets.UTF_8));
        failures.clear();
        require(HookPackages.discover(scripts.toFile(), failures).isEmpty() && !failures.isEmpty(),
                "source bound ignored");
        checks++;
        List<String> paths = new ArrayList<>();
        for (int index = 0; index < 33; index++) {
            paths.add("hooks/" + index + ".lua");
        }
        Files.write(json, CatalogJson.encode(paths).getBytes(StandardCharsets.UTF_8));
        failures.clear();
        require(HookPackages.discover(scripts.toFile(), failures).isEmpty() && !failures.isEmpty(),
                "file count bound ignored");
        checks++;
        // Keep oversized/invalid files out of the following source-discovery cases;
        // only listed files execute.
        Files.write(json, "[\"hooks/valid.lua\"]".getBytes(StandardCharsets.UTF_8));
        System.setProperty("betamoon.instanceDir", instance.toString());
        TinyMappingResolver mappings = TinyMappingResolver.readStrict(
                new ByteArrayInputStream("tiny\t2\t0\tnamed\tclient\tserver\n".getBytes(StandardCharsets.UTF_8)));
        HookPreparation prepared = HookPreparation.prepare(mappings);
        require(prepared.status().isEmpty(), "unlisted Lua files were executed in preparation");
        checks++;
        Files.write(root.resolve("second.lua"), "return {}".getBytes(StandardCharsets.UTF_8));
        String first = "local r=betamoon.hooks:createRegistry(); r:register({id='first',target={"
                + "class='fixture.hooks.ChildHookFixture',method='call',descriptor='(I)I'},at='before'},function() end)";
        Files.write(root.resolve("valid.lua"), first.getBytes(StandardCharsets.UTF_8));
        Files.write(json, "[\"hooks/valid.lua\",\"hooks/second.lua\"]".getBytes(StandardCharsets.UTF_8));
        prepared = HookPreparation.prepare(mappings);
        require(prepared.sites("fixture/hooks/ChildHookFixture").isEmpty(), "partial failed owner plan published");
        checks++;
        Files.write(root.resolve("second.lua"), first.getBytes(StandardCharsets.UTF_8));
        prepared = HookPreparation.prepare(mappings);
        require(prepared.sites("fixture/hooks/ChildHookFixture").isEmpty(),
                "duplicate owner IDs across files accepted");
        checks++;
        Files.write(json, "[\"hooks/valid.lua\"]".getBytes(StandardCharsets.UTF_8));
        prepared = HookPreparation.prepare(mappings);
        String oldFingerprint = prepared.sites("fixture/hooks/ChildHookFixture").get(0).fingerprint;
        failures.clear();
        HookPackages.Snapshot snapshot = HookPackages.discover(scripts.toFile(), failures).get(0);
        List<HookFileCollectionContext.Registration> expectedDeclarations = prepared.preflight(snapshot);
        String sourceHash = expectedDeclarations.get(0).definition.sourceSha256;
        Files.write(root.resolve("valid.lua"),
                first.replace("id='first'", "id='different'").getBytes(StandardCharsets.UTF_8));
        HookPackages.Snapshot mismatched = HookPackages.discover(scripts.toFile(), failures).get(0);
        Globals runtime = JsePlatform.standardGlobals();
        LuaTable module = new LuaTable();
        module.set("native", new LuaTable());
        runtime.set("betamoon", module);
        try {
            prepared.runtime(mismatched, runtime, expectedDeclarations);
            throw new AssertionError("phase metadata mismatch accepted");
        } catch (HookFailure failure) {
            require(failure.reason.equals("declaration_mismatch"), "normalized metadata mismatch reason");
            checks++;
        }
        Files.write(root.resolve("valid.lua"),
                (first + "\n-- callback source changed\n").getBytes(StandardCharsets.UTF_8));
        snapshot = HookPackages.discover(scripts.toFile(), failures).get(0);
        require(!prepared.preflight(snapshot).get(0).definition.sourceSha256.equals(sourceHash)
                && prepared.sites("fixture/hooks/ChildHookFixture").get(0).fingerprint.equals(oldFingerprint),
                "source hashes changed structural authorization");
        checks++;
        Path archives = Files.createDirectories(work.resolve("archives/betamoon/scripts"));
        writeZip(archives.resolve("valid.zip"), false);
        failures.clear();
        require(HookPackages.discover(archives.toFile(), failures).size() == 1 && failures.isEmpty(),
                "valid ZIP package");
        checks++;
        HookPackages.Snapshot foreign = HookPackages.discover(archives.toFile(), failures).get(0);
        try {
            prepared.preflight(foreign);
            throw new AssertionError("instance mismatch accepted");
        } catch (HookFailure expected) {
            require(expected.reason.equals("declaration_mismatch"), "instance mismatch reason");
            checks++;
        }
        writeZip(archives.resolve("collision.zip"), true);
        failures.clear();
        HookPackages.discover(archives.toFile(), failures);
        require(failures.stream().anyMatch(value -> value.contains("case-colliding")),
                "ambiguous ZIP entries accepted");
        checks++;
        Path legacyInstance = Files.createDirectories(work.resolve("legacy-instance"));
        Path legacy = Files.createDirectories(legacyInstance.resolve("lua_scripts"));
        require(HookPackages.scriptsDirectory(legacyInstance.toFile()).toPath().equals(legacy),
                "legacy root selection");
        System.setProperty("betamoon.instanceDir", legacyInstance.toString());
        HookPreparation.prepare(mappings);
        require(Files.isDirectory(legacy) && !Files.exists(legacyInstance.resolve("betamoon/scripts")),
                "premain migrated the script directory");
        Files.createDirectories(legacyInstance.resolve("betamoon/scripts"));
        require(HookPackages.scriptsDirectory(legacyInstance.toFile()).toPath()
                .equals(legacyInstance.resolve("betamoon/scripts")), "current root did not take precedence");
        checks += 3;
        Path escapeScripts = Files.createDirectories(work.resolve("escape-instance/betamoon/scripts"));
        Path escapeOwner = Files.createDirectories(escapeScripts.resolve("escape"));
        Path outside = Files.createDirectories(work.resolve("outside"));
        Files.write(outside.resolve("escaped.lua"), first.getBytes(StandardCharsets.UTF_8));
        Files.write(escapeOwner.resolve("betamoon.mod.json"),
                "{\"name\":\"Escape\",\"entrypoint\":\"main.lua\"}".getBytes(StandardCharsets.UTF_8));
        Files.write(escapeOwner.resolve("main.lua"), new byte[0]);
        Files.write(escapeOwner.resolve(HookPackages.FILE_NAME),
                "[\"link/escaped.lua\"]".getBytes(StandardCharsets.UTF_8));
        Path link = escapeOwner.resolve("link");
        if (System.getProperty("os.name").startsWith("Windows")) {
            Path script = work.resolve("junction.ps1");
            Files.write(script, ("param([string]$hookLink,[string]$hookTarget)\n"
                    + "New-Item -ItemType Junction -Path $hookLink -Target $hookTarget -ErrorAction Stop | Out-Null\n")
                    .getBytes(StandardCharsets.UTF_8));
            Process junction = new ProcessBuilder("C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe",
                    "-NoProfile", "-NonInteractive", "-File", script.toString(), link.toString(), outside.toString())
                    .inheritIO().start();
            require(junction.waitFor() == 0, "could not create the containment test junction");
        } else {
            Files.createSymbolicLink(link, outside);
        }
        failures.clear();
        require(HookPackages.discover(escapeScripts.toFile(), failures).isEmpty() && !failures.isEmpty(),
                "filesystem link escaped the hook package: " + link);
        require(Files.exists(outside.resolve("escaped.lua")), "containment validation changed the external file");
        checks += 2;
        return checks;
    }

    private static void writeZip(Path path, boolean collision) throws Exception {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            entry(output, "betamoon.mod.json", "{\"name\":\"Archive\",\"entrypoint\":\"main.lua\"}");
            entry(output, "main.lua", "error('must not execute')");
            entry(output, "betamoon.hooks.json", "[\"hooks.lua\"]");
            entry(output, "hooks.lua", "local r=betamoon.hooks:createRegistry()");
            if (collision) {
                entry(output, "HOOKS.lua", "return {}");
            }
        }
    }

    private static void entry(ZipOutputStream output, String path, String text) throws Exception {
        output.putNextEntry(new ZipEntry(path));
        output.write(text.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}

package betamoon.luamodloader;

import betamoon.assets.AssetPath;
import betamoon.assets.io.AssetResolver;
import betamoon.assets.io.PackageAssetProvider;
import betamoon.assets.io.ResolvedAsset;
import betamoon.assets.io.ZipAssetProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Verifies manifest-based Lua mods and their isolated private modules. */
public final class LuaModPackagesTest {
    private LuaModPackagesTest() {
    }

    public static void main(String[] arguments) throws Exception {
        File root = Files.createTempDirectory("betamoon-mod-packages").toFile();
        try {
            verifyDistributedExamples(new File("examples"));
            verifyDiscoveryAndManifestMetadata(root);
            verifyRequireCachingAndInitModules(root);
            verifyPrivateModuleIsolation(root);
            verifyMetadataConflicts(root);
            verifyContainmentAndCollisions(root);
            verifyRecursivePreflightAndFingerprint(root);
            verifyStableStructuralRetention(root);
            verifyZipPackages(root);
            System.out.println("Lua mod packages passed: directories, ZIPs, require isolation, metadata and reload.");
        } finally {
            ScriptResourceTracker.unloadAll();
            LuaScriptRegistry.setCurrentScriptFile(null);
            LuaScriptRegistry.clear();
            LuaScriptErrors.clear();
            deleteTree(root);
        }
    }

    private static void verifyDistributedExamples(File examples) throws IOException {
        LuaScriptFiles files = new LuaScriptFiles();
        List<LuaScriptFiles.PreflightFailure> failures = files.preflight(examples);
        require(failures.isEmpty(), "Distributed example package preflight failed: " + failures);

        List<LuaScriptFiles.PreflightFailure> discoveryFailures = new ArrayList<>();
        List<LuaModSource> sources = files.discover(examples, discoveryFailures);
        require(discoveryFailures.isEmpty(), "Distributed example discovery failed: " + discoveryFailures);
        List<String> expected = new ArrayList<>();
        expected.add("03_adv_03_basic_storage");
        expected.add("03_adv_08_simple_alloy");
        expected.add("03_adv_09_contextual_processor");
        expected.add("03_adv_10_advanced_fabrication");
        expected.add("03_adv_11_matcher_cookbook");
        LuaScriptRegistry.clear();
        for (int i = 0; i < sources.size(); i++) {
            LuaModSource source = sources.get(i);
            if (!expected.remove(source.ownerId())) {
                continue;
            }
            require(source.layout() == LuaModSource.Layout.DIRECTORY && "main.lua".equals(source.entrypointPath()),
                    "Distributed multi-script example is not a main.lua directory package: " + source.ownerId());
            List<String> parseErrors = new ArrayList<>();
            ScriptMod parsed = new ScriptModParser().parse(source, source.readEntrypoint(), parseErrors);
            require(parsed != null && parseErrors.isEmpty(),
                    "Distributed example manifest metadata did not parse: " + source.ownerId() + " " + parseErrors);
        }
        LuaScriptRegistry.clear();
        require(expected.isEmpty(), "Missing distributed example packages: " + expected);
    }

    private static void verifyDiscoveryAndManifestMetadata(File root) throws IOException {
        File scripts = directory(root, "discovery");
        write(new File(scripts, "loose.lua"), "name='Loose'\nfunction modInit() end\n");
        File ignored = directory(scripts, "assets_only");
        write(new File(ignored, "helper.lua"), "return true\n");
        File packaged = directory(scripts, "clockwork");
        write(new File(packaged, "betamoon.mod.json"), "{\n"
                + "  \"entrypoint\": \"src/clockwork.lua\",\n"
                + "  \"name\": \"Clockwork\",\n"
                + "  \"version\": \"1.2.0\",\n"
                + "  \"description\": \"Machines\",\n"
                + "  \"dependencies\": [\"Foundation\"],\n"
                + "  \"image\": \"icon.png\"\n}\n");
        write(new File(packaged, "src/clockwork.lua"), "name='Clockwork'\nversion='1.2.0'\n"
                + "dependencies={'Foundation'}\nimage='icon.png'\nfunction modInit() end\n");

        List<LuaScriptFiles.PreflightFailure> failures = new ArrayList<>();
        List<LuaModSource> sources = new LuaScriptFiles().discover(scripts, failures);
        require(failures.isEmpty() && sources.size() == 2,
                "Discovery must load loose files and manifested directories while ignoring other directories");
        LuaModSource packageSource = findDirectorySource(sources);
        require("clockwork/src/clockwork.lua".equals(packageSource.entrypointRelative()),
                "A manifest may select an arbitrary nested Lua entrypoint");

        LuaScriptRegistry.clear();
        List<String> errors = new ArrayList<>();
        ScriptMod parsed = new ScriptModParser().parse(packageSource,
                packageSource.readEntrypoint(), errors);
        require(parsed != null && errors.isEmpty(), "Manifest-only metadata must produce a valid mod");
        require("Clockwork".equals(parsed.getDisplayName()) && "1.2.0".equals(parsed.getVersion())
                && parsed.getDependencies().equals(Collections.singletonList("Foundation")),
                "Manifest metadata must fall back when the entrypoint omits it");
        require(parsed.resolveImageFile(scripts).equals(new File(packaged, "icon.png").getCanonicalFile()),
                "A package image must resolve relative to its mod root");
    }

    private static void verifyRequireCachingAndInitModules(File root) throws IOException {
        File scripts = directory(root, "require_cache");
        File packaged = directory(scripts, "cache_test");
        write(new File(packaged, "betamoon.mod.json"),
                "{\"entrypoint\":\"entry.lua\",\"name\":\"Cache test\"}\n");
        write(new File(packaged, "entry.lua"), "local first = require('content.value')\n"
                + "local second = require('content.value')\n"
                + "local nested = require('nested')\n"
                + "function modInit() assert(first == second and first.loads == 1 and nested.source == 'file') end\n");
        write(new File(packaged, "content/value.lua"),
                "module_loads = (module_loads or 0) + 1\nreturn { loads = module_loads }\n");
        write(new File(packaged, "nested.lua"), "return { source = 'file' }\n");
        write(new File(packaged, "nested/init.lua"), "return { source = 'init' }\n");

        ScriptMod mod = parseOnlyPackage(scripts);
        List<String> failures = new ArrayList<>();
        new ScriptLifecycleRunner(new RetainedScriptCatalog(), failures::add)
                .run(Collections.singletonList(mod), failures, false);
        require(mod.isLoaded() && failures.isEmpty(),
                "Private require must cache modules and support the module/init.lua convention");
    }

    private static void verifyPrivateModuleIsolation(File root) throws IOException {
        File scripts = directory(root, "isolation");
        ScriptMod first = createValuePackage(scripts, "first", "First", 11);
        ScriptMod second = createValuePackage(scripts, "second", "Second", 22);
        List<String> failures = new ArrayList<>();
        new ScriptLifecycleRunner(new RetainedScriptCatalog(), failures::add)
                .run(java.util.Arrays.asList(first, second), failures, false);
        require(first.isLoaded() && second.isLoaded() && failures.isEmpty(),
                "Separate mods must resolve identical private module names in their own roots");
    }

    private static ScriptMod createValuePackage(File scripts, String folder, String name, int expected)
            throws IOException {
        File packaged = directory(scripts, folder);
        write(new File(packaged, "betamoon.mod.json"),
                "{\"entrypoint\":\"entry.lua\",\"name\":\"" + name + "\"}\n");
        write(new File(packaged, "entry.lua"), "local value = require('shared.value')\n"
                + "function modInit() assert(value == " + expected + ") end\n");
        write(new File(packaged, "shared/value.lua"), "return " + expected + "\n");
        return parsePackage(scripts, folder);
    }

    private static void verifyMetadataConflicts(File root) throws IOException {
        File scripts = directory(root, "metadata_conflict");
        File packaged = directory(scripts, "conflict");
        write(new File(packaged, "betamoon.mod.json"),
                "{\"entrypoint\":\"entry.lua\",\"name\":\"Manifest name\",\"dependencies\":[\"A\"]}\n");
        write(new File(packaged, "entry.lua"),
                "name='Lua name'\ndependencies={'A'}\nfunction modInit() end\n");

        LuaScriptRegistry.clear();
        List<LuaScriptFiles.PreflightFailure> discoveryFailures = new ArrayList<>();
        LuaModSource source = new LuaScriptFiles().discover(scripts, discoveryFailures).get(0);
        List<String> errors = new ArrayList<>();
        ScriptMod parsed = new ScriptModParser().parse(source, source.readEntrypoint(), errors);
        require(parsed == null && contains(errors, "Metadata conflict for 'name'"),
                "Differing manifest and entrypoint metadata must be rejected clearly");

        write(new File(packaged, "entry.lua"),
                "name='Manifest name'\ndependencies={'B'}\nfunction modInit() end\n");
        LuaScriptRegistry.clear();
        errors.clear();
        parsed = new ScriptModParser().parse(source, source.readEntrypoint(), errors);
        require(parsed == null && contains(errors, "Metadata conflict for 'dependencies'"),
                "Dependency lists must match exactly when duplicated");
    }

    private static void verifyContainmentAndCollisions(File root) throws IOException {
        File traversal = directory(root, "traversal");
        File packaged = directory(traversal, "unsafe");
        write(new File(packaged, "betamoon.mod.json"), "{\"entrypoint\":\"../outside.lua\"}\n");
        List<LuaScriptFiles.PreflightFailure> failures = new ArrayList<>();
        require(new LuaScriptFiles().discover(traversal, failures).isEmpty()
                && containsFailures(failures, "invalid path segment"),
                "Manifest entrypoints must not escape their mod directory");

        File collisions = directory(root, "collisions");
        write(new File(collisions, "same.lua"), "name='Loose'\nfunction modInit() end\n");
        File sameDirectory = directory(collisions, "same");
        write(new File(sameDirectory, "betamoon.mod.json"), "{\"entrypoint\":\"entry.lua\"}\n");
        write(new File(sameDirectory, "entry.lua"), "name='Directory'\nfunction modInit() end\n");
        failures.clear();
        require(new LuaScriptFiles().discover(collisions, failures).isEmpty()
                && containsFailures(failures, "both use owner 'same'"),
                "A loose script and same-name package must be rejected as ambiguous");
    }

    private static void verifyRecursivePreflightAndFingerprint(File root) throws IOException {
        File scripts = directory(root, "reload");
        File packaged = directory(scripts, "reloadable");
        write(new File(packaged, "betamoon.mod.json"), "{\"entrypoint\":\"entry.lua\"}\n");
        write(new File(packaged, "entry.lua"), "name='Reloadable'\nfunction modInit() end\n");
        File helper = new File(packaged, "helpers/value.lua");
        write(helper, "return true\n");
        LuaScriptFiles files = new LuaScriptFiles();
        long fingerprint = files.fingerprint(scripts);
        write(helper, "return false -- changed length\n");
        require(fingerprint != files.fingerprint(scripts), "Changing a helper must alter the package fingerprint");
        File texture = new File(packaged, "mymod/textures/machine.png");
        write(texture, "first asset revision");
        long assetFingerprint = files.fingerprint(scripts);
        write(texture, "second asset revision with a different length");
        require(assetFingerprint != files.fingerprint(scripts),
                "Changing a package asset must alter the package fingerprint");
        write(helper, "function(\n");
        List<LuaScriptFiles.PreflightFailure> failures = files.preflight(scripts);
        require(failures.size() == 1 && "reloadable/helpers/value.lua".equals(failures.get(0).getFileName()),
                "Preflight must compile nested helper sources with their relative paths");

        File invalidManifest = new File(packaged, "betamoon.mod.json");
        write(invalidManifest, "{\n");
        long invalidFingerprint = files.fingerprint(scripts);
        write(invalidManifest, "{{\n");
        require(invalidFingerprint != files.fingerprint(scripts),
                "Editing an invalid manifest must still alter the reload fingerprint");
    }

    private static void verifyStableStructuralRetention(File root) throws IOException {
        File scripts = directory(root, "retention");
        File packaged = directory(scripts, "structural");
        File manifest = new File(packaged, "betamoon.mod.json");
        write(manifest, "{\"entrypoint\":\"old.lua\",\"name\":\"Structural\"}\n");
        write(new File(packaged, "old.lua"), "function modInit() end\n");
        write(new File(packaged, "new.lua"), "function modInit() end\n");
        List<LuaScriptFiles.PreflightFailure> failures = new ArrayList<>();
        LuaModSource oldSource = new LuaScriptFiles().discover(scripts, failures).get(0);
        LuaScriptRegistry.clear();
        ScriptMod active = new ScriptModParser().parse(oldSource, oldSource.readEntrypoint(), new ArrayList<>());
        NonReloadableScriptRegistry.mark(active.sourceFileName, "test structural content");
        try {
            RetainedScriptCatalog retained = new RetainedScriptCatalog();
            retained.rememberIfStructural(active);
            write(manifest, "{\"entrypoint\":\"new.lua\",\"name\":\"Structural\"}\n");
            failures.clear();
            LuaModSource changedSource = new LuaScriptFiles().discover(scripts, failures).get(0);
            LuaScriptRegistry.clear();
            List<ScriptMod> retainedMods = new ArrayList<>();
            require(retained.appendActiveSource(changedSource, retainedMods)
                    && retainedMods.equals(Collections.singletonList(active)),
                    "Changing a package entrypoint must retain its active structural generation by package identity");
        } finally {
            NonReloadableScriptRegistry.unmark(active.sourceFileName);
        }
    }

    private static void verifyZipPackages(File root) throws IOException {
        File scripts = directory(root, "zip_packages");
        File archive = new File(scripts, "clockwork.zip");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("betamoon.mod.json", utf8("{\"entrypoint\":\"main.lua\",\"name\":\"ZIP Clockwork\","
                + "\"image\":\"icon.png\"}\n"));
        entries.put("main.lua", utf8("local value=require('content.value')\n"
                + "function modInit() assert(value==42) end\n"));
        entries.put("content/value.lua", utf8("return 42\n"));
        entries.put("icon.png", new byte[]{1, 2, 3, 4});
        entries.put("mymod/textures/archive.png", new byte[]{5, 6, 7});
        writeZip(archive, entries);

        LuaScriptFiles files = new LuaScriptFiles();
        List<LuaScriptFiles.PreflightFailure> discoveryFailures = new ArrayList<>();
        List<LuaModSource> sources = files.discover(scripts, discoveryFailures);
        require(discoveryFailures.isEmpty() && sources.size() == 1
                && sources.get(0).layout() == LuaModSource.Layout.ZIP,
                "A root-manifested ZIP must be discovered as one mod");
        LuaModSource source = sources.get(0);
        LuaScriptRegistry.clear();
        List<String> errors = new ArrayList<>();
        ScriptMod mod = new ScriptModParser().parse(source, source.readEntrypoint(), errors);
        require(mod != null && errors.isEmpty(), "A ZIP entrypoint and private module must parse");
        List<String> lifecycleFailures = new ArrayList<>();
        new ScriptLifecycleRunner(new RetainedScriptCatalog(), lifecycleFailures::add)
                .run(Collections.singletonList(mod), lifecycleFailures, false);
        require(mod.isLoaded() && lifecycleFailures.isEmpty(), "A ZIP-backed mod must complete its lifecycle");
        require(java.util.Arrays.equals(new byte[]{1, 2, 3, 4}, mod.readArchivedImage(1024)),
                "A manifest image must be readable from its ZIP package");

        PackageAssetProvider assets = new PackageAssetProvider(scripts);
        byte[] packagedAsset = assets.read(AssetPath.parse("mymod/textures/archive.png"), 1024);
        require(java.util.Arrays.equals(new byte[]{5, 6, 7}, packagedAsset),
                "Registered asset fallback paths must resolve inside ZIP packages");
        File texturePack = new File(root, "texture_pack.zip");
        Map<String, byte[]> packEntries = new LinkedHashMap<>();
        packEntries.put("bm_assets/mymod/textures/archive.png", new byte[]{9, 8, 7});
        writeZip(texturePack, packEntries);
        AssetPath fallback = AssetPath.parse("mymod/textures/archive.png");
        AssetResolver resolver = new AssetResolver(assets, new ZipAssetProvider(texturePack), warning -> {
        });
        ResolvedAsset<byte[]> overridden = resolver.resolve("archive texture", fallback,
                fallback.getDirectOverridePath(), 1024, bytes -> bytes);
        require(java.util.Arrays.equals(new byte[]{9, 8, 7}, overridden.getValue())
                && "pack".equals(overridden.getSourceKind()),
                "A texture-pack ZIP override must take priority over a mod-package ZIP default");

        long fingerprint = files.fingerprint(scripts);
        entries.put("content/value.lua", utf8("return 43 -- archive changed\n"));
        writeZip(archive, entries);
        require(fingerprint != files.fingerprint(scripts), "Changing a ZIP package must alter the reload fingerprint");
        entries.put("content/unused.lua", utf8("function(\n"));
        writeZip(archive, entries);
        List<LuaScriptFiles.PreflightFailure> preflightFailures = files.preflight(scripts);
        require(preflightFailures.size() == 1
                && "clockwork.zip!/content/unused.lua".equals(preflightFailures.get(0).getFileName()),
                "ZIP helper syntax errors must retain their archive-relative source path");
        LuaScriptRegistry.clear();
        List<String> initialLoadErrors = new ArrayList<>();
        require(new LuaModLoader(scripts).loadLuaMods(initialLoadErrors).isEmpty()
                && contains(initialLoadErrors, "clockwork.zip!/content/unused.lua"),
                "An invalid unused helper must reject its package during initial loading");

        File unsafeRoot = directory(root, "unsafe_zips");
        Map<String, byte[]> unsafe = new LinkedHashMap<>();
        unsafe.put("betamoon.mod.json", utf8("{\"entrypoint\":\"main.lua\"}\n"));
        unsafe.put("main.lua", utf8("name='Unsafe'\nfunction modInit() end\n"));
        unsafe.put("../escape.lua", utf8("return true\n"));
        writeZip(new File(unsafeRoot, "unsafe.zip"), unsafe);
        discoveryFailures.clear();
        require(files.discover(unsafeRoot, discoveryFailures).isEmpty()
                && containsFailures(discoveryFailures, "invalid entry path"),
                "ZIP traversal entries must reject the complete package");

        Map<String, byte[]> colliding = new LinkedHashMap<>();
        colliding.put("betamoon.mod.json", utf8("{\"entrypoint\":\"Main.lua\"}\n"));
        colliding.put("Main.lua", utf8("name='Collision'\nfunction modInit() end\n"));
        colliding.put("main.lua", utf8("return true\n"));
        writeZip(new File(unsafeRoot, "collision.zip"), colliding);
        discoveryFailures.clear();
        files.discover(unsafeRoot, discoveryFailures);
        require(containsFailures(discoveryFailures, "case-colliding entries"),
                "Case-colliding ZIP entries must be rejected consistently across operating systems");
    }

    private static ScriptMod parseOnlyPackage(File scripts) throws IOException {
        List<LuaScriptFiles.PreflightFailure> failures = new ArrayList<>();
        List<LuaModSource> sources = new LuaScriptFiles().discover(scripts, failures);
        require(failures.isEmpty() && sources.size() == 1, "Expected one valid package fixture");
        LuaScriptRegistry.clear();
        List<String> errors = new ArrayList<>();
        ScriptMod mod = new ScriptModParser().parse(sources.get(0), sources.get(0).readEntrypoint(), errors);
        require(mod != null && errors.isEmpty(), "Expected package fixture to parse: " + errors);
        return mod;
    }

    private static ScriptMod parsePackage(File scripts, String folder) throws IOException {
        List<LuaScriptFiles.PreflightFailure> failures = new ArrayList<>();
        List<LuaModSource> sources = new LuaScriptFiles().discover(scripts, failures);
        LuaModSource source = null;
        for (int i = 0; i < sources.size(); i++) {
            if (folder.equals(sources.get(i).ownerId())) {
                source = sources.get(i);
            }
        }
        require(source != null, "Expected package source " + folder);
        List<String> errors = new ArrayList<>();
        ScriptMod mod = new ScriptModParser().parse(source, source.readEntrypoint(), errors);
        require(mod != null && errors.isEmpty(), "Expected package " + folder + " to parse: " + errors);
        return mod;
    }

    private static LuaModSource findDirectorySource(List<LuaModSource> sources) {
        for (int i = 0; i < sources.size(); i++) {
            if (sources.get(i).layout() == LuaModSource.Layout.DIRECTORY) {
                return sources.get(i);
            }
        }
        throw new AssertionError("Expected a directory source");
    }

    private static File directory(File parent, String name) throws IOException {
        File directory = new File(parent, name);
        if (!directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Could not create " + directory);
        }
        return directory;
    }

    private static void write(File file, String source) throws IOException {
        File parent = file.getParentFile();
        if (!parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Could not create " + parent);
        }
        Files.write(file.toPath(), source.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static void writeZip(File archive, Map<String, byte[]> entries) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(archive))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
    }

    private static boolean contains(List<String> values, String fragment) {
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i).contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsFailures(List<LuaScriptFiles.PreflightFailure> failures, String fragment) {
        for (int i = 0; i < failures.size(); i++) {
            if (failures.get(i).getCause().contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static void deleteTree(File root) throws IOException {
        if (root == null || !root.exists()) {
            return;
        }
        List<java.nio.file.Path> paths = new ArrayList<>();
        Files.walk(root.toPath()).forEach(paths::add);
        Collections.sort(paths, Comparator.reverseOrder());
        for (int i = 0; i < paths.size(); i++) {
            Files.deleteIfExists(paths.get(i));
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

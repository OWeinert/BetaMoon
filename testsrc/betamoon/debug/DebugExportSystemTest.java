package betamoon.debug;

import betamoon.entity.EntityTypeRegistry;
import betamoon.luaapi.audio.SoundEvents;
import betamoon.luaapi.module.ModuleRegistry;
import betamoon.luamodloader.LuaContentRegistry;
import betamoon.luamodloader.LuaModLoader;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptAssetScope;
import betamoon.tileentity.TileEntityRegistry;
import betamoon.worldgen.BiomeGenRegistry;
import betamoon.worldgen.WorldGenRegistry;
import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Verifies catalog consistency, transactional runs, UTF-8 output, and partial success. */
public final class DebugExportSystemTest {
    private DebugExportSystemTest() {
    }

    public static void main(String[] args) throws Exception {
        verifyCatalog();
        verifyCompleteRun();
        verifyPartialRun();
        verifyEscaping();
        verifyScriptPathRedaction();
        verifySnapshotBoundaries();
        System.out.println("Debug export infrastructure checks passed.");
    }

    static void verifyLiveCatalogRun() throws Exception {
        File root = new File("build/debug-export-system-test/catalog");
        deleteTree(root.toPath());
        DebugExportResult result = DebugExportSession.run(root, "catalog", DebugExportCatalog.completeDefinitions());
        require(result.getStatus() == DebugExportResult.Status.COMPLETE,
                "Live catalog export was incomplete: " + firstFailure(result));
        for (DebugExportDefinition definition : DebugExportCatalog.definitions()) {
            for (String file : definition.getFileNames()) {
                require(new File(result.getDirectory(), file).isFile(), "Catalog export omitted " + file);
            }
        }
    }

    private static void verifyCatalog() {
        List<DebugExportDefinition> definitions = DebugExportCatalog.definitions();
        require(definitions.size() == 7, "Unexpected debug export category count");
        Set<String> ids = new HashSet<String>();
        Set<String> files = new HashSet<String>();
        for (DebugExportDefinition definition : definitions) {
            require(ids.add(definition.getId()), "Duplicate debug export category id");
            require(definition.isIncludedInComplete(), "Visible category missing from complete snapshot");
            require(!definition.getFileNames().isEmpty(), "Debug export category has no declared files");
            for (String file : definition.getFileNames()) {
                require(files.add(file), "Debug export file belongs to multiple categories: " + file);
            }
        }
        require(DebugExportCatalog.completeDefinitions().equals(definitions),
                "Complete snapshot is not driven by the visible catalog");
        try {
            definitions.clear();
            throw new AssertionError("Debug export catalog is mutable");
        } catch (UnsupportedOperationException expected) {
        }
    }

    private static void verifyCompleteRun() throws Exception {
        File root = new File("build/debug-export-system-test/complete");
        deleteTree(root.toPath());
        DebugExportDefinition definition = definition("utf8", "utf8.txt", new DebugExporter() {
            @Override
            public void export(DebugExportSession session) throws Exception {
                session.writeTextFile("utf8.txt", new DebugExportSession.TextContent() {
                    @Override
                    public int write(BufferedWriter writer) throws Exception {
                        writer.write("Grüße from BetaMoon");
                        writer.newLine();
                        writer.write("second line");
                        writer.newLine();
                        return 2;
                    }
                });
            }
        });
        DebugExportResult result = DebugExportSession.run(root, "utf8", Arrays.asList(definition));
        require(result.getStatus() == DebugExportResult.Status.COMPLETE, "Complete export reported failure");
        require(result.getFileCount() == 2 && result.getRecordCount() == 2, "Complete export counts are incorrect");
        Path output = new File(result.getDirectory(), "utf8.txt").toPath();
        byte[] bytes = Files.readAllBytes(output);
        String text = new String(bytes, StandardCharsets.UTF_8);
        require(text.contains("Grüße from BetaMoon\nsecond line\n"), "UTF-8 body was not preserved");
        require(text.indexOf('\r') < 0, "Debug export used platform line endings");
        require(new File(result.getDirectory(), "manifest.txt").isFile(), "Manifest was not committed with the run");
        require(noTemporaryDirectories(root), "Temporary run directory remained after commit");
    }

    private static void verifyPartialRun() throws Exception {
        File root = new File("build/debug-export-system-test/partial");
        deleteTree(root.toPath());
        DebugExportDefinition first = definition("first", "first.txt", fileExporter("first.txt", "first"));
        DebugExportDefinition broken = definition("broken", "broken.txt", new DebugExporter() {
            @Override
            public void export(DebugExportSession session) throws Exception {
                throw new IllegalStateException("expected test failure");
            }
        });
        DebugExportDefinition last = definition("last", "last.txt", fileExporter("last.txt", "last"));
        DebugExportResult result = DebugExportSession.run(root, "partial", Arrays.asList(first, broken, last));
        require(result.getStatus() == DebugExportResult.Status.INCOMPLETE, "Partial export was not marked incomplete");
        require(result.getFailureCount() == 1, "Partial export failure count is incorrect");
        require(new File(result.getDirectory(), "first.txt").isFile(), "First successful category was discarded");
        require(new File(result.getDirectory(), "last.txt").isFile(), "Later category did not run after failure");
        String manifest = new String(Files.readAllBytes(new File(result.getDirectory(), "manifest.txt").toPath()),
                StandardCharsets.UTF_8);
        require(manifest.contains("Status: INCOMPLETE"), "Manifest omitted incomplete state");
        require(manifest.contains("expected test failure"), "Manifest omitted category failure");
    }

    private static DebugExporter fileExporter(final String name, final String content) {
        return new DebugExporter() {
            @Override
            public void export(DebugExportSession session) throws Exception {
                session.writeTextFile(name, new DebugExportSession.TextContent() {
                    @Override
                    public int write(BufferedWriter writer) throws Exception {
                        writer.write(content);
                        writer.newLine();
                        return 1;
                    }
                });
            }
        };
    }

    private static DebugExportDefinition definition(String id, String file, DebugExporter exporter) {
        return new DebugExportDefinition(id, id, "test", true, exporter, file);
    }

    private static void verifyEscaping() {
        require("a\\\\b\\\"c\\nd\\re\\tf\\u0001".equals(
                DebugExportNames.escapeText("a\\b\"c\nd\re\tf\u0001")), "Debug text escaping is incomplete");
    }

    private static void verifyScriptPathRedaction() throws Exception {
        File scriptsDirectory = LuaModLoader.getLuaModsDir();
        require(scriptsDirectory != null, "Lua script directory is unavailable for path-redaction test");
        LuaScriptErrors.clear();
        String absoluteSource = new File(scriptsDirectory, "private/example.lua").getAbsolutePath();
        LuaScriptErrors.add("private/example.lua", absoluteSource + ":7: expected test error");

        File root = new File("build/debug-export-system-test/redaction");
        deleteTree(root.toPath());
        DebugExportResult result = DebugExportSession.run(root, "redaction",
                Arrays.asList(DebugExportCatalog.find("scripts")));
        require(result.getStatus() == DebugExportResult.Status.COMPLETE, "Script diagnostic export failed");
        String issues = new String(Files.readAllBytes(
                new File(result.getDirectory(), "script_issues.txt").toPath()), StandardCharsets.UTF_8);
        require(!issues.contains(scriptsDirectory.getAbsolutePath()), "Script export disclosed its absolute root");
        require(issues.contains("<lua_scripts>"), "Script export omitted the redacted path marker");
        LuaScriptErrors.clear();
    }

    private static void verifySnapshotBoundaries() {
        requireImmutable(ScriptAssetScope.snapshot(), "asset snapshot");
        requireImmutable(SoundEvents.snapshot(), "sound-event snapshot");
        requireImmutable(LuaContentRegistry.snapshot(), "content snapshot");
        requireImmutable(LuaScriptRegistry.snapshot(), "script snapshot");
        requireImmutable(EntityTypeRegistry.snapshot(), "entity snapshot");
        requireImmutable(TileEntityRegistry.tileEntityDescriptions(), "tile-entity snapshot");
        requireImmutable(TileEntityRegistry.containerDescriptions(), "container snapshot");
        requireImmutable(TileEntityRegistry.guiDescriptions(), "GUI snapshot");
        requireImmutable(TileEntityRegistry.blockBindings(), "block-binding snapshot");
        requireImmutable(WorldGenRegistry.snapshot(), "world-generation snapshot");
        requireImmutable(BiomeGenRegistry.snapshot(), "biome snapshot");
        requireImmutable(ModuleRegistry.snapshot(), "module snapshot");
    }

    private static void requireImmutable(List<?> values, String label) {
        try {
            values.clear();
            throw new AssertionError(label + " is mutable");
        } catch (UnsupportedOperationException expected) {
        }
    }

    private static boolean noTemporaryDirectories(File root) {
        File[] children = root.listFiles();
        if (children == null) {
            return true;
        }
        for (File child : children) {
            if (child.getName().contains(".tmp")) {
                return false;
            }
        }
        return true;
    }

    private static String firstFailure(DebugExportResult result) {
        for (DebugExportResult.CategoryResult category : result.getCategories()) {
            if (!category.isComplete()) {
                return category.getTitle() + ": " + category.getFailure();
            }
        }
        return result.getFailure();
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

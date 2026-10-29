package betamoon.interop;

import betamoon.interop.catalog.CatalogJson;
import betamoon.interop.catalog.NativeAuthoringCatalog;
import betamoon.interop.discovery.BundledNativeCatalog;
import betamoon.interop.discovery.NativeIndexSnapshot;
import betamoon.interop.discovery.NativeLoaderRegistry;
import betamoon.interop.discovery.NativeOriginCache;
import betamoon.interop.discovery.NativeOriginInput;
import betamoon.interop.source.NativeSourceIndex;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Build-only adapter: the verified N1/N2 model becomes the same portable
 * handoff used by runtime exports.
 */
public final class NativeAuthoringCatalogGenerator {
    private NativeAuthoringCatalogGenerator() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected named game/Forge JAR and output root");
        }
        Path game = Paths.get(args[0]).toRealPath();
        Path output = Paths.get(args[1]).toAbsolutePath().normalize();
        Files.createDirectories(output);
        NativeOriginCache cache = new NativeOriginCache(output.resolve("cache"));
        BundledNativeCatalog bundled = new BundledNativeCatalog("named");
        NativeOriginInput origin = new NativeOriginInput("minecraft-forge", game, Collections.emptyList(),
                Collections.emptyList(), true, "verified_build_baseline");
        NativeLoaderRegistry.Capture capture = new NativeLoaderRegistry.Capture(Collections.singletonList(origin),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), false);
        List<String> warnings = new ArrayList<>();
        NativeSourceIndex sources = new NativeSourceIndex(Collections.emptyList(), cache, bundled.sourceFiles,
                warnings);
        NativeIndexSnapshot.Builder builder = new NativeIndexSnapshot.Builder(1, "named", bundled, capture, sources);
        builder.add(origin, cache.load(game, bundled.classes, bundled.inputFingerprint, warnings));
        NativeIndexSnapshot snapshot = builder.build(warnings);
        if (!Boolean.TRUE.equals(snapshot.coverage.get("knownOriginsComplete")) || !warnings.isEmpty()) {
            throw new IllegalStateException("Incomplete native authoring baseline: " + warnings);
        }
        // Every run has an immutable directory; no partially replaced generation can be
        // selected as committed.
        Path directory = Files.createTempDirectory(output, "snapshot-");
        NativeAuthoringCatalog.Payload payload;
        try (BufferedWriter writer = Files.newBufferedWriter(directory.resolve(NativeAuthoringCatalog.RECORDS_FILE),
                StandardCharsets.UTF_8)) {
            payload = NativeAuthoringCatalog.writeBaseline(writer, snapshot);
        }
        Path manifest = directory.resolve(NativeAuthoringCatalog.MANIFEST_FILE);
        Files.write(manifest, (CatalogJson.encode(payload.manifest()) + "\n").getBytes(StandardCharsets.UTF_8));
        Files.write(output.resolve("baseline-manifest.txt"), manifest.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("Native authoring baseline: " + snapshot.rows("classes").size() + " classes; " + manifest);
    }
}

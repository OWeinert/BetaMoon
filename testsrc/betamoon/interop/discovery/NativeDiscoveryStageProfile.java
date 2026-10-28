package betamoon.interop.discovery;

import betamoon.interop.catalog.CatalogJson;
import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.source.NativeSourceIndex;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Informational stage profile used to decide whether a new cache encoding is
 * justified.
 */
public final class NativeDiscoveryStageProfile {
    public static void main(String[] arguments) throws Exception {
        Path cacheRoot = Files.createTempDirectory(Paths.get("build").toAbsolutePath(), "perf-metadata-stages-");
        NativeOriginCache cache = new NativeOriginCache(cacheRoot);
        List<NativeOriginInput> origins = Arrays.asList(
                new NativeOriginInput("game", Paths.get("tools/rmcp/minecraft/jars/deobfuscated.jar").toAbsolutePath(),
                        Collections.emptyList(), Collections.emptyList(), true, "verified game binary"),
                new NativeOriginInput("mod", Paths.get("build/native-performance-p10.jar").toAbsolutePath(),
                        Collections.emptyList(), Collections.emptyList(), false, "actual installed mod binary"));
        NativeLoaderRegistry.Capture capture = new NativeLoaderRegistry.Capture(origins, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), false);
        NativeIndexSnapshot retained = null;
        for (int round = 0; round < 4; round++) {
            Map<String, Object> result = new LinkedHashMap<>();
            List<String> warnings = new ArrayList<>();
            long start = System.nanoTime();
            BundledNativeCatalog bundled = new BundledNativeCatalog("named");
            result.put("bundledNanos", System.nanoTime() - start);
            start = System.nanoTime();
            NativeSourceIndex sources = new NativeSourceIndex(Collections.emptyList(), cache, bundled.sourceFiles,
                    warnings);
            NativeIndexSnapshot.Builder builder = new NativeIndexSnapshot.Builder(round + 1, "named", bundled, capture,
                    sources);
            result.put("sourceSetupNanos", System.nanoTime() - start);
            long load = 0;
            long rows = 0;
            for (NativeOriginInput input : origins) {
                start = System.nanoTime();
                NativeOriginCache.Result metadata = cache.load(input.path, input.gameOrigin ? bundled.classes : null,
                        input.gameOrigin ? bundled.inputFingerprint : null, warnings);
                load += System.nanoTime() - start;
                start = System.nanoTime();
                builder.add(input, metadata);
                rows += System.nanoTime() - start;
            }
            result.put("originCacheHashReadParseNanos", load);
            result.put("rowPreparationNanos", rows);
            start = System.nanoTime();
            retained = builder.build(warnings);
            result.put("freezeSortIndexNanos", System.nanoTime() - start);
            result.put("round", round);
            result.put("classes", retained.rows("classes").size());
            result.put("members", retained.rows("members").size());
            System.out.println(CatalogJson.encode(result));
        }
        if (retained == null) {
            throw new AssertionError();
        }
    }
}

package betamoon.interop.discovery;

import betamoon.interop.catalog.CatalogJson;
import betamoon.interop.catalog.CatalogValues;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Bounded refresh cancellation and measured real binary indexing without
 * loading Minecraft classes.
 */
public final class NativeDiscoveryLifecycleTest {
    private NativeDiscoveryLifecycleTest() {
    }

    public static void main(String[] args) throws Exception {
        Path work = Files.createTempDirectory(Paths.get("build").toAbsolutePath(), "native-n7-discovery-");
        cancellation(work.resolve("cancel-cache"));
        profile(work.resolve("profile-cache"));
        System.out.println(
                "Native discovery lifecycle passed: bounded cancellation, recovery, cached scans and release.");
    }

    private static void cancellation(Path cache) throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        NativeDiscoveryService service = new NativeDiscoveryService(cache, action -> {
            Thread thread = new Thread(() -> {
                try {
                    release.await();
                    action.run();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }, "N7 held discovery worker");
            thread.setDaemon(true);
            return thread;
        });
        NativeLoaderRegistry.Capture empty = new NativeLoaderRegistry.Capture(Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), false);
        try {
            for (int index = 0; index < 1000; index++) {
                NativeDiscoveryService.Ticket ticket = service.request(empty, "unknown", Collections.emptyList());
                Map<String, Object> work = CatalogValues.object(service.status().get("work"));
                require(((Number) work.get("queuedScans")).intValue() <= 1, "refresh work queue grew");
                ticket.close();
                require("cancelled".equals(ticket.status().get("status")), "cancelled ticket stayed pending");
                require(((Number) CatalogValues.object(service.status().get("work")).get("queuedScans"))
                        .intValue() == 0, "cancelled queued scan retained its work");
            }
            NativeDiscoveryService.Ticket finalRequest = service.request(empty, "unknown", Collections.emptyList());
            release.countDown();
            await(service);
            require("complete".equals(finalRequest.status().get("status")), "latest refresh did not recover");
            require(service.snapshot().generation == finalRequest.generation, "cancelled generation published");
            require(((Number) CatalogValues.object(service.status().get("work")).get("lastScanNanos")).longValue() > 0,
                    "accepted scan timing absent");
            finalRequest.close();
        } finally {
            release.countDown();
            service.close();
        }
        require(service.snapshot().generation == 0 && "closed".equals(service.status().get("status")),
                "closed discovery retained a published index");
    }

    private static void profile(Path cache) throws Exception {
        Path game = Paths.get("tools/rmcp/minecraft/jars/deobfuscated.jar").toAbsolutePath();
        NativeOriginInput input = new NativeOriginInput("n7-game", game, Collections.emptyList(),
                Collections.emptyList(), true, "verified test binary");
        NativeLoaderRegistry.Capture capture = new NativeLoaderRegistry.Capture(Collections.singletonList(input),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), false);
        System.gc();
        long heapBefore = usedHeap();
        Map<String, Object> result;
        try (NativeDiscoveryService service = new NativeDiscoveryService(cache)) {
            List<Long> scans = new ArrayList<>();
            for (int index = 0; index < 6; index++) {
                try (NativeDiscoveryService.Ticket ticket = service.request(capture, "named",
                        Collections.emptyList())) {
                    await(service);
                    require("complete".equals(ticket.status().get("status")), "scan did not complete");
                    NativeIndexSnapshot snapshot = service.snapshot();
                    require(!snapshot.rows("classes").isEmpty() && !snapshot.rows("members").isEmpty(),
                            "real binary index empty");
                    int hits = ((Number) snapshot.coverage.get("cacheHits")).intValue();
                    require(hits == (index == 0 ? 0 : 1), "content cache did not distinguish cold/cached scan");
                    scans.add(((Number) CatalogValues.object(service.status().get("work")).get("lastScanNanos"))
                            .longValue());
                }
            }
            NativeIndexSnapshot snapshot = service.snapshot();
            Map<String, Object> filter = CatalogValues.record("kind", "members", "declaringType",
                    "net.minecraft.src.World", "limit", 50);
            long[] query = new long[500];
            for (int index = -100; index < query.length; index++) {
                long start = System.nanoTime();
                Map<String, Object> page = NativeQueryService.query(snapshot, filter);
                require(!((List<?>) page.get("items")).isEmpty(), "measured query lost results");
                if (index >= 0) {
                    query[index] = System.nanoTime() - start;
                }
            }
            System.gc();
            result = CatalogValues.record("java", System.getProperty("java.version"), "coldScanNanos", scans.get(0),
                    "cachedScanNanos", scans.subList(1, scans.size()), "queryNanos", percentiles(query), "classes",
                    snapshot.rows("classes").size(), "members", snapshot.rows("members").size(),
                    "estimatedMetadataBytes", snapshot.coverage.get("estimatedMetadataBytes"), "retainedHeapDeltaBytes",
                    Math.max(0, usedHeap() - heapBefore), "heapMeasurement",
                    "GC-assisted whole-process delta, informational", "coverage", snapshot.coverage);
        }
        Files.write(Paths.get("build/native-n7-discovery-profile.json"),
                CatalogJson.encode(result).getBytes(StandardCharsets.UTF_8));
        System.out.println("N7 discovery profile: " + CatalogJson.encode(result));
    }

    private static Map<String, Object> percentiles(long[] values) {
        Arrays.sort(values);
        return CatalogValues.record("p50", values[values.length / 2], "p95", values[values.length * 95 / 100], "p99",
                values[values.length * 99 / 100]);
    }

    private static long usedHeap() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    private static void await(NativeDiscoveryService service) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!service.publishReady()) {
            require(System.nanoTime() < deadline, "discovery deadline exceeded: " + service.status());
            Thread.sleep(1);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

package betamoon.interop.discovery;

import betamoon.BetaMoonClient;
import betamoon.BetaMoonCommon;
import betamoon.instrumentation.agent.AgentLoadedTypes;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.binding.NativeBindings;
import betamoon.interop.binding.NativeNames;
import betamoon.interop.mapping.NativeNamespaceDetector;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.ModLoader;
import net.minecraft.src.World;

/**
 * Small game adapter. All classloader/code-source/census inspection stays on
 * the client thread.
 */
public final class NativeDiscoveryRuntime {
    private static NativeDiscoveryService service;
    private static final NativeLoaderRegistry LOADERS = new NativeLoaderRegistry();
    private static Path instance;
    private static NativeBindings bindings;

    private NativeDiscoveryRuntime() {
    }

    public static void startClient() {
        if (service != null) {
            return;
        }
        instance = Minecraft.getMinecraftDir().toPath().toAbsolutePath().normalize();
        service = new NativeDiscoveryService(instance.resolve("betamoon/cache/native-access"));
        try {
            refresh();
        } catch (RuntimeException exception) {
            BetaMoonCommon.LOGGER.warning("Native discovery could not start: " + exception.getMessage());
        }
    }

    public static NativeIndexSnapshot snapshot() {
        return service == null ? NativeIndexSnapshot.empty() : service.snapshot();
    }

    public static Map<String, Object> status() {
        return service == null ? CatalogValues.record("status", "not_started", "generation", 0) : service.status();
    }

    public static void requireClientAccess() {
        if (service == null) {
            throw new IllegalStateException("native_discovery_not_started");
        }
        service.requireClientAccess();
    }

    public static NativeBindings bindingsIfAvailable() {
        return bindings;
    }

    public static NativeDiscoveryService.Ticket refresh() {
        if (service == null) {
            throw new IllegalStateException("native_discovery_not_started");
        }
        service.requireClientAccess();
        List<String> warnings = new ArrayList<>();
        NativeDiscoverySettings settings = NativeDiscoverySettings.load(instance, warnings);
        String namespace = namespace(warnings);
        List<Class<?>> loaded = new ArrayList<>();
        boolean complete = AgentLoadedTypes.visit(loaded::add);
        List<Class<?>> mods = new ArrayList<>();
        for (Object mod : ModLoader.getLoadedMods()) {
            mods.add(mod.getClass());
        }
        NativeLoaderRegistry.Capture capture = LOADERS
                .capture(
                        Arrays.asList(World.class, Minecraft.class, EntityPlayer.class, GuiScreen.class,
                                ModLoader.class, BetaMoonClient.class),
                        mods, loaded, complete, instance.resolve("mods"), settings.includeJdk);
        if (bindings == null && (namespace.equals("named") || namespace.equals("client"))) {
            try (InputStream input = NativeDiscoveryRuntime.class
                    .getResourceAsStream("/betamoon/instrumentation/mapping/b1.7.3.tiny")) {
                bindings = new NativeBindings(
                        new NativeNames(TinyMappingResolver.readStrict(input).snapshot(), namespace), LOADERS,
                        NativeDiscoveryRuntime::snapshot);
            } catch (Exception exception) {
                NativeOriginCache.warn(warnings, "native_binding_unavailable: " + exception.getMessage());
            }
        }
        warnings.addAll(capture.warnings);
        capture = new NativeLoaderRegistry.Capture(capture.origins, capture.loaders, capture.loaded, warnings,
                capture.completeCensus);
        return service.request(capture, namespace, settings.sources);
    }

    private static String namespace(List<String> warnings) {
        try (InputStream input = NativeDiscoveryRuntime.class
                .getResourceAsStream("/betamoon/instrumentation/mapping/b1.7.3.tiny")) {
            TinyMappingResolver mappings = TinyMappingResolver.readStrict(input);
            Map<String, String> sentinels = new LinkedHashMap<>();
            sentinels.put("net/minecraft/src/World", World.class.getName());
            sentinels.put("net/minecraft/src/EntityPlayer", EntityPlayer.class.getName());
            sentinels.put("net/minecraft/client/Minecraft", Minecraft.class.getName());
            return NativeNamespaceDetector.detect(sentinels, mappings);
        } catch (Exception exception) {
            NativeOriginCache.warn(warnings, "namespace_unavailable: " + exception.getMessage());
            return "unknown";
        }
    }

    public static void poll() {
        if (service != null && service.publishReady()) {
            NativeIndexSnapshot snapshot = service.snapshot();
            BetaMoonCommon.LOGGER.info("Native discovery: " + snapshot.rows("classes").size() + " classes, "
                    + snapshot.coverage.get("indexedOrigins") + " indexed origins, "
                    + snapshot.coverage.get("failedOrigins") + " failed; generation " + snapshot.generation + ".");
        }
    }

    public static void close() {
        if (service != null) {
            service.close();
            service = null;
        }
        LOADERS.close();
        bindings = null;
        instance = null;
    }
}

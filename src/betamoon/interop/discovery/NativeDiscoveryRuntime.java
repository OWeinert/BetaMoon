package betamoon.interop.discovery;

import betamoon.BetaMoonClient;
import betamoon.BetaMoonCommon;
import betamoon.hooks.HookPreparation;
import betamoon.instrumentation.agent.AgentLoadedTypes;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.interop.attachments.NativeAttachmentDisposal;
import betamoon.interop.binding.NativeBindings;
import betamoon.interop.binding.NativeNames;
import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.mapping.NativeNamespaceDetector;
import betamoon.interop.runtime.NativeEventAccess;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.GuiScreen;
import net.minecraft.src.IWorldAccess;
import net.minecraft.src.ItemStack;
import net.minecraft.src.ModLoader;
import net.minecraft.src.World;

/**
 * Small game adapter. All classloader/code-source/census inspection stays on
 * the client thread.
 */
public final class NativeDiscoveryRuntime {
    private static volatile NativeDiscoveryService service;
    private static final NativeLoaderRegistry LOADERS = new NativeLoaderRegistry();
    private static Path instance;
    private static volatile NativeBindings bindings;
    private static volatile CatalogCapture catalogCapture;

    private NativeDiscoveryRuntime() {
    }

    public static List<ClassLoader> hookLoaders() {
        NativeBindings current = bindings;
        if (current != null) {
            current.requireThread();
        }
        return LOADERS.bindingLoaders();
    }

    public static String hookNamespace() {
        NativeBindings current = bindings;
        return current == null ? "unknown" : current.names.namespace();
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

    /**
     * Execution metadata never captures the unrelated live hook diagnostic graph.
     */
    public static NativeIndexSnapshot catalogSnapshot() {
        NativeDiscoveryService current = service;
        NativeIndexSnapshot base = current == null ? NativeIndexSnapshot.empty() : current.snapshot();
        HookPreparation hooks = HookPreparation.current();
        long revision = hooks == null ? 0 : hooks.revision();
        String status = hooks == null ? "agent_unavailable" : "available";
        CatalogCapture captured = catalogCapture;
        if (captured == null || captured.base != base || captured.revision != revision
                || !captured.status.equals(status)) {
            captured = new CatalogCapture(base, status, revision);
            catalogCapture = captured;
        }
        return captured.snapshot;
    }

    public static NativeIndexSnapshot snapshot() {
        NativeIndexSnapshot snapshot = catalogSnapshot();
        HookPreparation hooks = HookPreparation.current();
        return snapshot.withHooks(hooks == null ? HookPreparation.unavailableRecords() : hooks.status(),
                hooks == null ? 0 : hooks.revision(), hooks == null ? "agent_unavailable" : "available");
    }

    public static Map<String, Object> status() {
        NativeDiscoveryService current = service;
        NativeBindings execution = bindings;
        Map<String, Object> result = new LinkedHashMap<>(
                current == null ? CatalogValues.record("status", "not_started", "generation", 0) : current.status());
        if (execution != null) {
            result.put("execution", execution.diagnostics());
        }
        HookPreparation hooks = HookPreparation.current();
        result.put("hooks",
                CatalogValues.record("status", hooks == null ? "agent_unavailable" : "available", "revision",
                        hooks == null ? 0 : hooks.revision(), "items",
                        hooks == null ? HookPreparation.unavailableRecords() : hooks.status()));
        return result;
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
                        new NativeNames(TinyMappingResolver.readStrict(input).snapshot(), namespace, curatedTypes()),
                        LOADERS, NativeDiscoveryRuntime::catalogSnapshot);
                bindings.interfaces.entryFactory(NativeEventAccess::interfaceEntry);
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
        if (bindings != null) {
            bindings.clients.requireCloseBoundary();
        }
        NativeBindings execution = bindings;
        NativeDiscoveryService current = service;
        bindings = null;
        service = null;
        catalogCapture = null;
        try {
            if (execution != null) {
                execution.close();
            }
        } finally {
            try {
                if (current != null) {
                    current.close();
                }
            } finally {
                LOADERS.close();
                instance = null;
            }
        }
    }

    public static void observeClient(Minecraft client) {
        NativeAttachmentDisposal.drain();
        if (bindings != null && client != null) {
            bindings.clients.observe();
            bindings.owned.observe(client.theWorld, client.thePlayer);
            bindings.interfaces.observe(client.theWorld, client.thePlayer);
        }
    }

    private static final class CatalogCapture {
        private final NativeIndexSnapshot base;
        private final String status;
        private final long revision;
        private final NativeIndexSnapshot snapshot;

        private CatalogCapture(NativeIndexSnapshot base, String status, long revision) {
            this.base = base;
            this.status = status;
            this.revision = revision;
            snapshot = base.withHookAvailability(status, revision);
        }
    }

    private static Map<String, Class<?>> curatedTypes() {
        Map<String, Class<?>> result = new LinkedHashMap<>();
        result.put("net/minecraft/src/World", World.class);
        result.put("net/minecraft/src/ItemStack", ItemStack.class);
        result.put("net/minecraft/src/IWorldAccess", IWorldAccess.class);
        result.put("net/minecraft/src/GuiScreen", GuiScreen.class);
        return result;
    }
}

package betamoon.interop.discovery;

import betamoon.interop.catalog.CatalogValues;
import java.io.File;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Session-owned loader identities. Capture runs on the client; published
 * records contain strings only.
 */
public final class NativeLoaderRegistry implements AutoCloseable {
    private final IdentityHashMap<ClassLoader, String> loaders = new IdentityHashMap<>();
    private final Map<Path, String> origins = new LinkedHashMap<>();
    private final Map<String, Path> localOrigins = new LinkedHashMap<>();
    private long nextOriginId;
    private String session = UUID.randomUUID().toString();
    private long bindingRevision;

    public String id(ClassLoader loader) {
        if (loader == null) {
            return "bootstrap";
        }
        String id = loaders.get(loader);
        if (id == null) {
            if (loaders.size() >= 512) {
                return "untracked-loader";
            }
            id = "loader-" + session + "-" + (loaders.size() + 1);
            loaders.put(loader, id);
            bindingRevision++;
        }
        return id;
    }

    /**
     * Java binding routes only; snapshots continue to contain detached identity
     * strings.
     */
    public List<ClassLoader> bindingLoaders() {
        return new ArrayList<>(loaders.keySet());
    }

    public long bindingRevision() {
        return bindingRevision;
    }

    public ClassLoader bindingLoader(String identity) {
        for (Map.Entry<ClassLoader, String> entry : loaders.entrySet()) {
            if (entry.getValue().equals(identity)) {
                return entry.getKey();
            }
        }
        if (!"bootstrap".equals(identity)) {
            throw new IllegalArgumentException("stale_loader: " + identity);
        }
        return null;
    }

    public String originId(Class<?> type) {
        while (type.isArray()) {
            type = type.getComponentType();
        }
        try {
            if (type.getProtectionDomain().getCodeSource() == null) {
                return null;
            }
            URL location = type.getProtectionDomain().getCodeSource().getLocation();
            if (location == null) {
                return null;
            }
            Path path = localOrigins.get(location.toExternalForm());
            if (path == null && "file".equalsIgnoreCase(location.getProtocol())) {
                path = Paths.get(location.toURI()).toAbsolutePath().normalize();
            }
            return origins.get(path);
        } catch (Exception exception) {
            return null;
        }
    }

    public Capture capture(List<Class<?>> roots, List<Class<?>> modEntries, List<Class<?>> loaded,
            boolean completeCensus, Path modsDirectory, boolean includeJdk) {
        Builder builder = new Builder(includeJdk);
        for (Class<?> root : roots) {
            builder.type(root, false, root == roots.get(0));
        }
        for (Class<?> entry : modEntries) {
            builder.type(entry, true, false);
        }
        for (Class<?> type : loaded) {
            builder.type(type, false, false);
        }
        builder.loader(Thread.currentThread().getContextClassLoader());
        builder.loader(ClassLoader.getSystemClassLoader());
        String classpath = System.getProperty("java.class.path", "");
        for (String entry : classpath.split(File.pathSeparator)) {
            if (!entry.isEmpty()) {
                builder.origin(Paths.get(entry), null, null, false, "classpath_hint");
            }
        }
        if (modsDirectory != null && Files.isDirectory(modsDirectory)) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(modsDirectory)) {
                List<Path> sorted = new ArrayList<>();
                for (Path entry : entries) {
                    if (sorted.size() >= 4096) {
                        NativeOriginCache.warn(builder.warnings, "origin_count_limit");
                        break;
                    }
                    sorted.add(entry);
                }
                Collections.sort(sorted);
                for (Path entry : sorted) {
                    String name = entry.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (Files.isDirectory(entry) || name.endsWith(".jar") || name.endsWith(".zip")
                            || name.endsWith(".jar.disabled") || name.endsWith(".zip.disabled")) {
                        builder.origin(entry, null, null, false, "mods_candidate");
                    }
                }
            } catch (Exception exception) {
                NativeOriginCache.warn(builder.warnings, "mods_directory_unavailable: " + exception.getMessage());
            }
        }
        return builder.finish(completeCensus);
    }

    @Override
    public void close() {
        loaders.clear();
        origins.clear();
        localOrigins.clear();
        session = UUID.randomUUID().toString();
        bindingRevision++;
    }

    private final class Builder {
        private final boolean includeJdk;
        private final Map<String, Map<String, Object>> loaderRecords = new TreeMap<>();
        private final Map<Path, OriginBuilder> originRecords = new LinkedHashMap<>();
        private final List<Map<String, Object>> loadedRecords = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        private Builder(boolean includeJdk) {
            this.includeJdk = includeJdk;
        }

        private void type(Class<?> type, boolean mod, boolean root) {
            if (type.isPrimitive() || type.isArray()) {
                return;
            }
            ClassLoader definingLoader = type.getClassLoader();
            loader(definingLoader);
            Path source = null;
            try {
                if (type.getProtectionDomain().getCodeSource() != null) {
                    source = local(type.getProtectionDomain().getCodeSource().getLocation());
                    origin(source, id(definingLoader), mod ? type.getName() : null, root, "loaded_code_source");
                }
            } catch (SecurityException exception) {
                NativeOriginCache.warn(warnings, "code_source_unavailable: " + type.getName());
            }
            if (loadedRecords.size() < 100000) {
                loadedRecords.add(CatalogValues.record("name", type.getName(), "loaderId", id(definingLoader),
                        "originId", source == null ? null : origins.get(source)));
            }
        }

        private void loader(ClassLoader loader) {
            String loaderId = id(loader);
            if (loaderId.equals("untracked-loader")) {
                NativeOriginCache.warn(warnings, "loader_count_limit");
                return;
            }
            if (loaderRecords.containsKey(loaderId)) {
                return;
            }
            if (loader == null) {
                loaderRecords.put(loaderId, CatalogValues.record("id", loaderId, "name", "bootstrap", "parentId", null,
                        "enumerable", false));
                return;
            }
            loaderRecords.put(loaderId, CatalogValues.record("id", loaderId, "name", loader.getClass().getName(),
                    "parentId", id(loader.getParent()), "enumerable", loader instanceof URLClassLoader));
            loader(loader.getParent());
            if (loader instanceof URLClassLoader) {
                for (URL url : ((URLClassLoader) loader).getURLs()) {
                    origin(local(url), loaderId, null, false, "loader_url");
                }
            } else {
                NativeOriginCache.warn(warnings, "loader_not_enumerable: " + loaderId);
            }
        }

        private Path local(URL url) {
            if (url == null) {
                return null;
            }
            try {
                URI uri = url.toURI();
                if ("jar".equals(uri.getScheme())) {
                    String value = uri.getSchemeSpecificPart();
                    if (!value.endsWith("!/")) {
                        return null;
                    }
                    uri = new URI(value.substring(0, value.length() - 2));
                }
                Path path = "file".equalsIgnoreCase(uri.getScheme()) ? Paths.get(uri).toRealPath() : null;
                if (path != null && localOrigins.size() < 4096) {
                    localOrigins.put(url.toExternalForm(), path);
                }
                return path;
            } catch (Exception exception) {
                NativeOriginCache.warn(warnings, "origin_url_unavailable: " + url.getProtocol());
                return null;
            }
        }

        private void origin(Path path, String loader, String mod, boolean game, String evidence) {
            if (path == null) {
                return;
            }
            try {
                path = path.toRealPath();
                Path jdk = Paths.get(System.getProperty("java.home")).toRealPath();
                if (!includeJdk && (path.startsWith(jdk)
                        || (jdk.getFileName().toString().equals("jre") && path.startsWith(jdk.getParent())))) {
                    return;
                }
                OriginBuilder origin = originRecords.get(path);
                if (origin == null) {
                    if (originRecords.size() >= 128) {
                        NativeOriginCache.warn(warnings, "origin_count_limit");
                        return;
                    }
                    String originId = origins.get(path);
                    if (originId == null) {
                        if (origins.size() >= 4096) {
                            origins.remove(origins.keySet().iterator().next());
                        }
                        originId = "origin-" + session + "-" + (++nextOriginId);
                        origins.put(path, originId);
                        bindingRevision++;
                    }
                    origin = new OriginBuilder(originId, path, evidence);
                    originRecords.put(path, origin);
                }
                if (loader != null && !origin.loaders.contains(loader)) {
                    origin.loaders.add(loader);
                }
                if (mod != null && !origin.mods.contains(mod)) {
                    origin.mods.add(mod);
                }
                origin.game |= game;
                if ("loaded_code_source".equals(evidence) || "loader_url".equals(evidence)) {
                    origin.evidence = evidence;
                }
            } catch (Exception exception) {
                NativeOriginCache.warn(warnings, "origin_unavailable: " + exception.getMessage());
            }
        }

        private Capture finish(boolean complete) {
            List<NativeOriginInput> inputs = new ArrayList<>();
            for (OriginBuilder origin : originRecords.values()) {
                inputs.add(new NativeOriginInput(origin.id, origin.path, origin.loaders, origin.mods, origin.game,
                        origin.evidence));
            }
            return new Capture(inputs, new ArrayList<>(loaderRecords.values()), loadedRecords, warnings,
                    complete && !warnings.contains("loader_count_limit") && loadedRecords.size() < 100000);
        }
    }

    private static final class OriginBuilder {
        private final String id;
        private final Path path;
        private final List<String> loaders = new ArrayList<>();
        private final List<String> mods = new ArrayList<>();
        private boolean game;
        private String evidence;

        private OriginBuilder(String id, Path path, String evidence) {
            this.id = id;
            this.path = path;
            this.evidence = evidence;
        }
    }

    public static final class Capture {
        public final List<NativeOriginInput> origins;
        public final List<Map<String, Object>> loaders;
        public final List<Map<String, Object>> loaded;
        public final List<String> warnings;
        public final boolean completeCensus;

        public Capture(List<NativeOriginInput> origins, List<Map<String, Object>> loaders,
                List<Map<String, Object>> loaded, List<String> warnings, boolean completeCensus) {
            this.origins = Collections.unmodifiableList(new ArrayList<>(origins));
            this.loaders = freezeRecords(loaders);
            this.loaded = freezeRecords(loaded);
            this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
            this.completeCensus = completeCensus;
        }

        private static List<Map<String, Object>> freezeRecords(List<Map<String, Object>> records) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> record : records) {
                result.add(CatalogValues.object(CatalogValues.freeze(record)));
            }
            return Collections.unmodifiableList(result);
        }
    }
}

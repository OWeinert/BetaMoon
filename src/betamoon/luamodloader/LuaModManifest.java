package betamoon.luamodloader;

import betamoon.interop.catalog.CatalogJson;
import betamoon.io.FileIo;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Strict parser for the discovery-only manifest of a directory-based Lua mod.
 */
final class LuaModManifest {
    static final String FILE_NAME = "betamoon.mod.json";
    static final int MAX_BYTES = 64 * 1024;

    private final String entrypoint;
    private final String name;
    private final String version;
    private final String description;
    private final List<String> dependencies;
    private final Map<String, String> minimumDependencyVersions;
    private final boolean dependenciesDeclared;
    private final String image;

    private LuaModManifest(String entrypoint, String name, String version, String description,
            List<String> dependencies, Map<String, String> minimumDependencyVersions, boolean dependenciesDeclared,
            String image) {
        this.entrypoint = entrypoint;
        this.name = name;
        this.version = version;
        this.description = description;
        this.dependencies = dependencies == null ? null : Collections.unmodifiableList(new ArrayList<>(dependencies));
        this.minimumDependencyVersions = Collections.unmodifiableMap(new LinkedHashMap<>(minimumDependencyVersions));
        this.dependenciesDeclared = dependenciesDeclared;
        this.image = image;
    }

    static LuaModManifest read(File manifest) throws IOException {
        if (manifest.length() > MAX_BYTES) {
            throw new IOException("Manifest exceeds " + MAX_BYTES + " bytes");
        }
        return parse(FileIo.readUtf8Normalized(manifest));
    }

    static LuaModManifest parse(String source) throws IOException {
        if (source.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IOException("Manifest exceeds " + MAX_BYTES + " bytes");
        }
        Object root;
        try {
            root = CatalogJson.decode(source);
        } catch (IllegalArgumentException error) {
            throw new IOException("Manifest is not valid JSON", error);
        }
        if (!(root instanceof Map)) {
            throw new IOException("Manifest root must be a JSON object");
        }

        String entrypoint = null;
        String name = null;
        String version = null;
        String description = null;
        List<String> dependencies = null;
        Map<String, String> minimumDependencyVersions = new LinkedHashMap<>();
        boolean dependenciesDeclared = false;
        String image = null;
        for (Object rawEntry : ((Map<?, ?>) root).entrySet()) {
            Map.Entry<?, ?> field = (Map.Entry<?, ?>) rawEntry;
            String key = (String) field.getKey();
            Object value = field.getValue();
            String fieldName = key;
            if ("entrypoint".equals(fieldName)) {
                entrypoint = string(value, "entrypoint", false);
            } else if ("name".equals(fieldName)) {
                name = string(value, "name", false);
            } else if ("version".equals(fieldName)) {
                version = string(value, "version", false);
            } else if ("description".equals(fieldName)) {
                description = string(value, "description", true);
            } else if ("dependencies".equals(fieldName)) {
                dependencies = dependencies(value, minimumDependencyVersions);
                dependenciesDeclared = true;
            } else if ("image".equals(fieldName)) {
                image = string(value, "image", false);
            } else {
                throw new IOException("Manifest has unknown field '" + fieldName + "'");
            }
        }
        if (entrypoint == null || entrypoint.trim().isEmpty()) {
            throw new IOException("Manifest requires a non-empty entrypoint");
        }
        if (!entrypoint.equals(entrypoint.trim()) || entrypoint.indexOf('\\') >= 0
                || !entrypoint.toLowerCase(Locale.ROOT).endsWith(".lua")) {
            throw new IOException("Manifest entrypoint must be a relative .lua path using '/' separators");
        }
        validatePath(entrypoint, "entrypoint");
        if (image != null) {
            validatePath(image, "image");
        }
        return new LuaModManifest(entrypoint, name, version, description, dependencies, minimumDependencyVersions,
                dependenciesDeclared, image);
    }

    String entrypoint() {
        return entrypoint;
    }

    String name() {
        return name;
    }

    String version() {
        return version;
    }

    String description() {
        return description;
    }

    boolean hasDependencies() {
        return dependenciesDeclared;
    }

    List<String> dependencies() {
        return dependencies;
    }

    Map<String, String> minimumDependencyVersions() {
        return minimumDependencyVersions;
    }

    String image() {
        return image;
    }

    private static String string(Object value, String field, boolean allowEmpty) throws IOException {
        if (!(value instanceof String)) {
            throw new IOException("Manifest " + field + " must be a string");
        }
        String result = (String) value;
        if (!result.equals(result.trim()) || !allowEmpty && result.isEmpty()) {
            throw new IOException(
                    "Manifest " + field + " must be " + (allowEmpty ? "trimmed" : "a non-empty trimmed string"));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<String> dependencies(Object value, Map<String, String> minimumVersions) throws IOException {
        if (!(value instanceof List)) {
            throw new IOException("Manifest dependencies must be an array of names or version requirements");
        }
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Object dependency : (List<?>) value) {
            String name;
            if (dependency instanceof String) {
                name = string(dependency, "dependency", false);
            } else if (dependency instanceof Map) {
                String requiredName = null;
                String minimumVersion = null;
                for (Object rawEntry : ((Map<?, ?>) dependency).entrySet()) {
                    Map.Entry<?, ?> field = (Map.Entry<?, ?>) rawEntry;
                    String fieldName = (String) field.getKey();
                    Object fieldValue = field.getValue();
                    if ("name".equals(fieldName)) {
                        requiredName = string(fieldValue, "dependency name", false);
                    } else if ("minVersion".equals(fieldName)) {
                        minimumVersion = string(fieldValue, "dependency minVersion", false);
                    } else {
                        throw new IOException("Manifest dependency has unknown field '" + fieldName + "'");
                    }
                }
                if (requiredName == null || minimumVersion == null) {
                    throw new IOException("Manifest versioned dependency requires name and minVersion");
                }
                try {
                    PackageVersion.parse(minimumVersion);
                } catch (IllegalArgumentException error) {
                    throw new IOException("Manifest dependency '" + requiredName + "': " + error.getMessage(), error);
                }
                name = requiredName;
                minimumVersions.put(name, minimumVersion);
            } else {
                throw new IOException("Manifest dependency must be a string or version requirement object");
            }
            if (!seen.add(name)) {
                throw new IOException("Manifest dependencies contains duplicate '" + name + "'");
            }
            result.add(name);
        }
        return result;
    }

    private static void validatePath(String path, String field) throws IOException {
        String[] segments = path.split("/", -1);
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment) || segment.endsWith(".")) {
                throw new IOException("Manifest " + field + " contains an invalid path segment: " + path);
            }
            for (int characterIndex = 0; characterIndex < segment.length(); characterIndex++) {
                char character = segment.charAt(characterIndex);
                if (Character.isISOControl(character) || ":*?\"<>|".indexOf(character) >= 0) {
                    throw new IOException("Manifest " + field + " contains an invalid character: " + path);
                }
            }
        }
    }
}

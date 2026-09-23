package betamoon.luamodloader;

import betamoon.io.FileIo;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.src.EnumJsonNodeType;
import net.minecraft.src.J_InvalidSyntaxException;
import net.minecraft.src.J_JdomParser;
import net.minecraft.src.J_JsonNode;

/** Strict parser for the discovery-only manifest of a directory-based Lua mod. */
final class LuaModManifest {
    static final String FILE_NAME = "betamoon.mod.json";
    static final int MAX_BYTES = 64 * 1024;

    private final String entrypoint;
    private final String name;
    private final String version;
    private final String description;
    private final List<String> dependencies;
    private final boolean dependenciesDeclared;
    private final String image;

    private LuaModManifest(String entrypoint, String name, String version, String description,
            List<String> dependencies, boolean dependenciesDeclared, String image) {
        this.entrypoint = entrypoint;
        this.name = name;
        this.version = version;
        this.description = description;
        this.dependencies = dependencies == null ? null
                : Collections.unmodifiableList(new ArrayList<>(dependencies));
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
        if (source.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IOException("Manifest exceeds " + MAX_BYTES + " bytes");
        }
        J_JsonNode root;
        try {
            root = new J_JdomParser().func_27367_a(source);
        } catch (J_InvalidSyntaxException error) {
            throw new IOException("Manifest is not valid JSON", error);
        }
        if (root.func_27218_a() != EnumJsonNodeType.OBJECT) {
            throw new IOException("Manifest root must be a JSON object");
        }

        String entrypoint = null;
        String name = null;
        String version = null;
        String description = null;
        List<String> dependencies = null;
        boolean dependenciesDeclared = false;
        String image = null;
        for (Object rawEntry : root.func_27214_c().entrySet()) {
            Map.Entry<?, ?> field = (Map.Entry<?, ?>) rawEntry;
            J_JsonNode key = (J_JsonNode) field.getKey();
            J_JsonNode value = (J_JsonNode) field.getValue();
            String fieldName = key.func_27216_b();
            if ("entrypoint".equals(fieldName)) {
                entrypoint = string(value, "entrypoint", false);
            } else if ("name".equals(fieldName)) {
                name = string(value, "name", false);
            } else if ("version".equals(fieldName)) {
                version = string(value, "version", false);
            } else if ("description".equals(fieldName)) {
                description = string(value, "description", true);
            } else if ("dependencies".equals(fieldName)) {
                dependencies = dependencies(value);
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
                || !entrypoint.toLowerCase(java.util.Locale.ROOT).endsWith(".lua")) {
            throw new IOException("Manifest entrypoint must be a relative .lua path using '/' separators");
        }
        validatePath(entrypoint, "entrypoint");
        if (image != null) {
            validatePath(image, "image");
        }
        return new LuaModManifest(entrypoint, name, version, description, dependencies, dependenciesDeclared, image);
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

    String image() {
        return image;
    }

    private static String string(J_JsonNode value, String field, boolean allowEmpty) throws IOException {
        if (value.func_27218_a() != EnumJsonNodeType.STRING) {
            throw new IOException("Manifest " + field + " must be a string");
        }
        String result = value.func_27216_b();
        if (!result.equals(result.trim()) || !allowEmpty && result.isEmpty()) {
            throw new IOException("Manifest " + field + " must be "
                    + (allowEmpty ? "trimmed" : "a non-empty trimmed string"));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<String> dependencies(J_JsonNode value) throws IOException {
        if (value.func_27218_a() != EnumJsonNodeType.ARRAY) {
            throw new IOException("Manifest dependencies must be an array of strings");
        }
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (J_JsonNode dependency : (List<J_JsonNode>) value.func_27215_d()) {
            String name = string(dependency, "dependency", false);
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

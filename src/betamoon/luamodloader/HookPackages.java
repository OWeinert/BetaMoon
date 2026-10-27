package betamoon.luamodloader;

import betamoon.hooks.HookFailure;
import betamoon.interop.catalog.CatalogJson;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pure access to the normal package identity/containment rules, also available
 * in premain.
 */
public final class HookPackages {
    public static final String FILE_NAME = "betamoon.hooks.json";

    private HookPackages() {
    }

    public static List<Snapshot> discover(File scripts, List<String> failures) {
        List<LuaScriptFiles.PreflightFailure> invalid = new ArrayList<>();
        List<LuaModSource> sources = new LuaScriptFiles().discover(scripts, invalid);
        List<Snapshot> result = new ArrayList<>();
        for (LuaScriptFiles.PreflightFailure failure : invalid) {
            failures.add(failure.getFileName() + ": " + failure.getCause());
        }
        for (LuaModSource source : sources) {
            try {
                Snapshot snapshot = snapshot(source);
                if (snapshot != null) {
                    result.add(snapshot);
                }
            } catch (IOException | RuntimeException error) {
                failures.add(source.ownerId() + ": " + error.getMessage());
            }
        }
        return result;
    }

    static Snapshot snapshot(LuaModSource source) throws IOException {
        if (source.layout() == LuaModSource.Layout.SINGLE_FILE || !source.hasResource(FILE_NAME)) {
            return null;
        }
        Object parsed;
        if (source.layout() == LuaModSource.Layout.DIRECTORY) {
            exactCase(source.moduleRoot(), FILE_NAME);
        }
        try {
            parsed = CatalogJson.decode(utf8(source.readResource(FILE_NAME, 65536)));
        } catch (IllegalArgumentException error) {
            throw new HookFailure("hook_file_invalid", source.ownerId() + ": " + error.getMessage(), error);
        }
        if (!(parsed instanceof List) || ((List<?>) parsed).size() > 32) {
            throw new HookFailure("hook_file_invalid", "hook list must be an array of at most 32 Lua paths");
        }
        Map<String, String> texts = new LinkedHashMap<>();
        Set<String> names = new HashSet<>();
        int total = 0;
        for (Object value : (List<?>) parsed) {
            if (!(value instanceof String)) {
                throw new HookFailure("hook_file_invalid", "hook paths must be strings");
            }
            String path = (String) value;
            validatePath(path);
            if (!names.add(path.toLowerCase(Locale.ROOT))) {
                throw new HookFailure("hook_file_invalid", "duplicate hook path " + path);
            }
            if (source.layout() == LuaModSource.Layout.DIRECTORY) {
                exactCase(source.moduleRoot(), path);
            }
            byte[] bytes = source.readResource(path, 262144);
            total += bytes.length;
            if (total > 1048576) {
                throw new HookFailure("hook_file_invalid", "hook sources exceed 1 MiB/package");
            }
            texts.put(path, utf8(bytes));
        }
        return new Snapshot(source.ownerId(), source.entrypointRelative(), source.scriptsRoot(), texts);
    }

    private static String utf8(byte[] bytes) throws IOException {
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    private static void validatePath(String path) {
        if (path.length() > 512 || !path.endsWith(".lua") || path.indexOf('\\') >= 0) {
            throw new HookFailure("hook_file_invalid", "require a relative .lua path using /: " + path);
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..") || segment.endsWith(".")) {
                throw new HookFailure("hook_file_invalid", "invalid hook path " + path);
            }
            for (int index = 0; index < segment.length(); index++) {
                char character = segment.charAt(index);
                if (Character.isISOControl(character) || ":*?\"<>|".indexOf(character) >= 0) {
                    throw new HookFailure("hook_file_invalid", "invalid hook path " + path);
                }
            }
        }
    }

    private static void exactCase(File root, String path) throws IOException {
        File current = root;
        for (String segment : path.split("/")) {
            String[] children = current.list();
            if (children == null || !Arrays.asList(children).contains(segment)) {
                throw new IOException("Hook path missing or case mismatch: " + path);
            }
            current = new File(current, segment);
        }
    }

    public static File scriptsDirectory(File instance) {
        File current = new File(instance, "betamoon/scripts");
        File legacy = new File(instance, "lua_scripts");
        return current.isDirectory() ? current : legacy.isDirectory() ? legacy : current;
    }

    public static final class Snapshot {
        public final String owner;
        public final String resourceOwner;
        public final File scripts;
        public final Map<String, String> files;

        Snapshot(String owner, String resourceOwner, File scripts, Map<String, String> files) {
            this.owner = owner;
            this.resourceOwner = resourceOwner;
            this.scripts = scripts;
            this.files = Collections.unmodifiableMap(new LinkedHashMap<>(files));
        }
    }
}

package betamoon.luamodloader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.lib.ResourceFinder;

/** Resolves Lua resources inside exactly one directory or ZIP package. */
final class LuaModResourceFinder implements ResourceFinder {
    private final LuaModSource source;
    private final String virtualPrefix;

    LuaModResourceFinder(LuaModSource source) {
        this.source = source;
        virtualPrefix = source.modulePathPrefix();
    }

    @Override
    public InputStream findResource(String resourceName) {
        if (resourceName == null || !resourceName.startsWith(virtualPrefix)) {
            return null;
        }
        String relative = resourceName.substring(virtualPrefix.length()).replace('\\', '/');
        if (!isSafeRelativeLuaPath(relative)) {
            return null;
        }

        try {
            if (!source.hasResource(relative)) {
                return null;
            }
            byte[] bytes = source.readLuaSource(relative).getBytes(StandardCharsets.UTF_8);
            return new ByteArrayInputStream(bytes);
        } catch (IOException error) {
            throw new LuaError("Could not read private module '" + relative + "': " + error.getMessage());
        }
    }

    private boolean isSafeRelativeLuaPath(String path) {
        if (path.isEmpty() || path.startsWith("/") || !path.endsWith(".lua")) {
            return false;
        }
        String[] segments = path.split("/", -1);
        for (int i = 0; i < segments.length; i++) {
            if (segments[i].isEmpty() || ".".equals(segments[i]) || "..".equals(segments[i])) {
                return false;
            }
        }
        return true;
    }
}

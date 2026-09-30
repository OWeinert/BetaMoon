package betamoon.worldgen.structure;

import betamoon.assets.AssetKey;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.worldgen.WorldGenKey;
import java.io.IOException;

/** Resolves package gameplay data without consulting texture-pack overrides. */
public final class WorldGenDataResolver {
    public static final int MAX_STRUCTURE_BYTES = 4 * 1024 * 1024;
    public static final int MAX_LOOT_TABLE_BYTES = 1024 * 1024;

    private WorldGenDataResolver() {
    }

    public static ResolvedData structure(String owner, WorldGenKey key, String declaredPath) throws IOException {
        String path = declaredPath == null || declaredPath.trim().isEmpty() ? defaultStructurePath(key)
                : normalize(declaredPath);
        if (!path.startsWith("assets/")) {
            throw new IOException("Worldgen gameplay data must be below the package assets/ root: " + path);
        }
        byte[] bytes = LuaScriptRegistry.readPackageResource(owner, path, MAX_STRUCTURE_BYTES);
        return new ResolvedData(path, LuaScriptRegistry.packageResourceDisplayPath(owner, path), bytes);
    }

    public static ResolvedData lootTable(String owner, AssetKey key, String declaredPath) throws IOException {
        String path = declaredPath == null || declaredPath.trim().isEmpty() ? defaultLootTablePath(key)
                : normalize(declaredPath);
        if (!path.startsWith("assets/")) {
            throw new IOException("Loot-table gameplay data must be below the package assets/ root: " + path);
        }
        byte[] bytes = LuaScriptRegistry.readPackageResource(owner, path, MAX_LOOT_TABLE_BYTES);
        return new ResolvedData(path, LuaScriptRegistry.packageResourceDisplayPath(owner, path), bytes);
    }

    private static String defaultStructurePath(WorldGenKey key) {
        String prefix = "structure/";
        String path = key.getPath().startsWith(prefix) ? key.getPath().substring(prefix.length()) : key.getPath();
        return "assets/" + key.getNamespace() + "/worldgen/structures/" + path + ".json";
    }

    private static String defaultLootTablePath(AssetKey key) {
        return "assets/" + key.getNamespace() + "/loot_tables/" + key.getPath() + ".json";
    }

    private static String normalize(String value) throws IOException {
        String path = value.trim().replace('\\', '/');
        if (path.startsWith("/") || path.contains(":") || path.contains("//")) {
            throw new IOException("Invalid package gameplay-data path: " + value);
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IOException("Invalid package gameplay-data path: " + value);
            }
        }
        return path;
    }

    public static final class ResolvedData {
        public final String path;
        public final String displayPath;
        public final byte[] bytes;

        private ResolvedData(String path, String displayPath, byte[] bytes) {
            this.path = path;
            this.displayPath = displayPath;
            this.bytes = bytes;
        }
    }
}

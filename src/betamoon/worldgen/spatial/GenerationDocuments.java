package betamoon.worldgen.spatial;

import betamoon.assets.model.ModelJson;
import betamoon.luamodloader.LuaScriptRegistry;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static betamoon.worldgen.spatial.GenerationValues.error;
import static betamoon.worldgen.spatial.GenerationValues.fields;
import static betamoon.worldgen.spatial.GenerationValues.object;
import static betamoon.worldgen.spatial.GenerationValues.string;

/** Lua path declarations and JSON documents enter the same domain parsers. */
public final class GenerationDocuments {
    private GenerationDocuments() {
    }

    public static Map<String, Object> resolve(String owner, Map<String, Object> declaration) {
        if (!declaration.containsKey("path")) {
            return declaration;
        }
        fields(declaration, "worldgen", "key", "path");
        String path = string(declaration.get("path"), "worldgen.path");
        if (!path.startsWith("assets/") || path.contains("\\") || path.contains(":") || path.contains("//")) {
            throw error("worldgen.path", "generation documents must be relative files below assets/");
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw error("worldgen.path", "invalid path segment");
            }
        }
        try {
            Map<String, Object> parsed = object(
                    ModelJson.read(LuaScriptRegistry.readPackageResource(owner, path, 4 * 1024 * 1024)), path);
            Map<String, Object> result = new LinkedHashMap<String, Object>(parsed);
            if (declaration.containsKey("key")) {
                if (result.containsKey("key") && !result.get("key").equals(declaration.get("key"))) {
                    throw error(path + ".key", "JSON and Lua declaration keys disagree");
                }
                result.put("key", declaration.get("key"));
            }
            return result;
        } catch (IOException exception) {
            throw error(path, exception.getMessage());
        }
    }
}

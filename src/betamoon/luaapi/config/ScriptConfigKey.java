package betamoon.luaapi.config;

import betamoon.content.ContentKey;
import betamoon.content.ContentType;
import betamoon.luaapi.utils.LuaContentNamespaces;
import java.nio.file.Path;
import org.luaj.vm2.LuaError;

/** A config name with a short, flat form and an optional named-file form. */
final class ScriptConfigKey {
    private static final ContentType CONFIG = ContentType.of("config");

    private final String namespace;
    private final String name;

    private ScriptConfigKey(String namespace, String name) {
        this.namespace = namespace;
        this.name = name;
    }

    static ScriptConfigKey parse(String value) {
        try {
            boolean named = value != null && value.indexOf(':') >= 0;
            ContentKey key = named ? ContentKey.parseForType(value, CONFIG)
                    : ContentKey.of(value, CONFIG, "default");
            LuaContentNamespaces.requireDeclarationNamespace(key);
            return new ScriptConfigKey(key.namespace(), named ? key.name() : null);
        } catch (IllegalArgumentException error) {
            throw new LuaError("Config key: " + error.getMessage());
        }
    }

    Path fileUnder(Path root) {
        Path path = name == null ? root.resolve(namespace + ".json")
                : root.resolve(namespace).resolve(name + ".json");
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedFile = path.toAbsolutePath().normalize();
        if (!normalizedFile.startsWith(normalizedRoot)) {
            throw new LuaError("Config key resolves outside the BetaMoon config folder");
        }
        return normalizedFile;
    }

    @Override
    public String toString() {
        return name == null ? namespace : namespace + ":" + name;
    }
}

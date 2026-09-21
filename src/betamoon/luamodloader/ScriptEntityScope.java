package betamoon.luamodloader;

import betamoon.assets.AssetKey;
import betamoon.entity.EntityTypeDefinition;
import betamoon.entity.EntityTypeRegistry;
import java.util.LinkedHashMap;
import java.util.Map;

/** Holds entity declarations until one script finishes initialization successfully. */
public final class ScriptEntityScope implements AutoCloseable {
    private static final ThreadLocal<ScriptEntityScope> CURRENT = new ThreadLocal<>();

    private final String owner;
    private final Map<AssetKey, EntityTypeDefinition> staged = new LinkedHashMap<>();
    private boolean published;

    private ScriptEntityScope(String owner) {
        this.owner = owner;
    }

    static ScriptEntityScope open(String owner) {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("An entity declaration scope is already active");
        }
        ScriptEntityScope scope = new ScriptEntityScope(owner);
        CURRENT.set(scope);
        return scope;
    }

    public static EntityTypeDefinition findVisible(AssetKey key) {
        ScriptEntityScope scope = CURRENT.get();
        if (scope != null && scope.owner.equals(LuaScriptRegistry.getCurrentScriptFile())) {
            EntityTypeDefinition pending = scope.staged.get(key);
            if (pending != null) {
                return pending;
            }
        }
        return EntityTypeRegistry.find(key);
    }

    public static void stage(EntityTypeDefinition definition) {
        ScriptEntityScope scope = CURRENT.get();
        if (scope == null || scope.published || !scope.owner.equals(LuaScriptRegistry.getCurrentScriptFile())) {
            throw new IllegalStateException("Entity declarations require modInit or modReload");
        }
        if (definition.key.toString().startsWith("betamoon:")
                || definition.key.toString().startsWith("minecraft:")) {
            throw new IllegalArgumentException("The betamoon and minecraft entity namespaces are reserved");
        }
        if (scope.staged.containsKey(definition.key)) {
            throw new IllegalArgumentException("Duplicate entity declaration: " + definition.key);
        }
        String currentOwner = EntityTypeRegistry.owner(definition.key);
        if (currentOwner != null && !scope.owner.equals(currentOwner)) {
            throw new IllegalArgumentException("Entity type " + definition.key + " belongs to " + currentOwner);
        }
        EntityTypeRegistry.validate(scope.owner, definition);
        scope.staged.put(definition.key, definition);
    }

    void publish() {
        if (published || !owner.equals(LuaScriptRegistry.getCurrentScriptFile())) {
            throw new IllegalStateException("Entity declaration scope is already completed");
        }
        EntityTypeRegistry.publish(owner, staged);
        published = true;
    }

    @Override
    public void close() {
        CURRENT.remove();
    }
}

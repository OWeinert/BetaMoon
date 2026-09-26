package betamoon.luamodloader;

import betamoon.assets.AssetDefinition;
import betamoon.assets.AssetId;
import betamoon.assets.AssetRegistration;
import betamoon.assets.AssetRegistry;
import betamoon.assets.BuiltinAssets;
import java.util.List;

/**
 * Bridges script initialization to the headless asset registry; it does not
 * parse Lua.
 */
public final class ScriptAssetScope implements AutoCloseable {
    private static final AssetRegistry REGISTRY = new AssetRegistry();
    private static final ThreadLocal<ScriptAssetScope> CURRENT = new ThreadLocal<>();

    private final AssetRegistry.Batch batch;
    private final String owner;
    private boolean published;
    private boolean closed;

    private ScriptAssetScope(String owner) {
        this.owner = owner;
        this.batch = REGISTRY.begin(owner);
    }

    static ScriptAssetScope open(String owner) {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("An asset declaration scope is already active");
        }
        ScriptAssetScope scope = new ScriptAssetScope(owner);
        CURRENT.set(scope);
        return scope;
    }

    /**
     * Internal entry for future Lua asset parsers, available only during
     * modInit/modReload.
     */
    public static void discardPending(AssetDefinition definition) {
        ScriptAssetScope scope = CURRENT.get();
        if (scope != null && !scope.closed && !scope.published) {
            scope.requireOwner();
            scope.batch.discard(definition);
        }
    }

    public static void requireInitialization() {
        ScriptAssetScope scope = CURRENT.get();
        if (scope == null || scope.closed || scope.published) {
            throw new IllegalStateException("Asset declarations require modInit or modReload");
        }
        scope.requireOwner();
    }

    /** Returns the source identity used to resolve this mod's default assets. */
    public static String currentOwner() {
        ScriptAssetScope scope = CURRENT.get();
        if (scope == null || scope.closed || scope.published) {
            throw new IllegalStateException("Asset declarations require modInit or modReload");
        }
        scope.requireOwner();
        return scope.owner;
    }

    public static void stage(AssetDefinition definition) {
        ScriptAssetScope scope = CURRENT.get();
        if (scope == null || scope.closed || scope.published) {
            throw new IllegalStateException("Assets must be declared during script initialization");
        }
        scope.requireOwner();
        AssetDefinition owned = definition.getDefaultSource() == null ? definition.fromSource(scope.owner) : definition;
        if (!scope.owner.equals(owned.getDefaultSource())) {
            throw new IllegalArgumentException("Asset default source does not match the declaring script");
        }
        if (BuiltinAssets.find(owned.getId()) != null) {
            throw new IllegalArgumentException("Built-in asset cannot be redeclared: " + owned.getId());
        }
        AssetRegistration existing = REGISTRY.find(owned.getId());
        if (existing != null && !scope.owner.equals(existing.getOwner())) {
            throw new IllegalArgumentException(
                    "Asset " + owned.getId() + " belongs to script " + existing.getOwner());
        }
        scope.batch.add(owned);
    }

    /**
     * Reads published metadata without exposing registry mutation to declaration
     * parsers.
     */
    public static AssetRegistration find(AssetId id) {
        return REGISTRY.find(id);
    }

    /** Returns published script assets as an immutable point-in-time snapshot. */
    public static List<AssetRegistration> snapshot() {
        return REGISTRY.snapshot();
    }

    /** Resolves private declarations only for the currently initializing owner. */
    public static AssetDefinition findVisible(AssetId id) {
        AssetDefinition builtin = BuiltinAssets.find(id);
        if (builtin != null) {
            return builtin;
        }
        ScriptAssetScope scope = CURRENT.get();
        if (scope != null && !scope.closed && !scope.published) {
            scope.requireOwner();
            AssetDefinition pending = scope.batch.find(id);
            if (pending != null) {
                return pending;
            }
        }
        AssetRegistration registration = REGISTRY.find(id);
        return registration == null ? null : registration.getDefinition();
    }

    void publish() {
        if (closed || published) {
            throw new IllegalStateException("Asset declaration scope is already completed");
        }
        requireOwner();
        AssetRegistry.Publication publication = batch.commit();
        ScriptResourceTracker.track(publication::close);
        published = true;
    }

    private void requireOwner() {
        if (!owner.equals(LuaScriptRegistry.getCurrentScriptFile())) {
            throw new IllegalStateException("Asset declaration owner does not match the executing script");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        batch.close();
        CURRENT.remove();
        closed = true;
    }
}

package betamoon.luaapi.utils;

import betamoon.luamodloader.LuaScriptRegistry;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import org.luaj.vm2.LuaValue;

/**
 * Immutable callback functions and attribution captured from one declaration.
 */
public final class LuaCallbackDeclarations<K extends Enum<K> & LuaCallbackKey> {
    private final Map<K, LuaValue> actions;
    private final Class<K> keyType;
    final String owner;
    final String resource;

    private LuaCallbackDeclarations(Builder<K> builder) {
        actions = Collections.unmodifiableMap(new EnumMap<K, LuaValue>(builder.actions));
        keyType = builder.keyType;
        owner = builder.owner;
        resource = builder.resource;
    }

    public boolean has(K key) {
        return actions.containsKey(key);
    }

    LuaValue action(K key) {
        return actions.get(key);
    }

    Class<K> keyType() {
        return keyType;
    }

    public static <K extends Enum<K> & LuaCallbackKey> Builder<K> builder(Class<K> keyType, String resource) {
        return new Builder<K>(keyType, resource);
    }

    /** Mutable parser used only while constructing an immutable declaration. */
    public static final class Builder<K extends Enum<K> & LuaCallbackKey> {
        private final Map<K, LuaValue> actions;
        private final Class<K> keyType;
        private final String owner;
        private final String resource;

        private Builder(Class<K> keyType, String resource) {
            this.keyType = keyType;
            this.resource = resource;
            actions = new EnumMap<K, LuaValue>(keyType);
            owner = LuaScriptRegistry.getCurrentScriptFile();
        }

        public Builder<K> parse(LuaValue definition, K key, String... extra) {
            LuaValue callback = LuaDeclarationValues.action(definition.get(key.luaName()), key.luaName(), extra);
            if (!callback.isnil()) {
                actions.put(key, callback);
            }
            return this;
        }

        public LuaCallbackDeclarations<K> build() {
            return new LuaCallbackDeclarations<K>(this);
        }
    }
}

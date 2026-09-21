package betamoon.client.render;

import betamoon.assets.AssetKey;
import betamoon.entity.EntityTypeDefinition;
import betamoon.entity.EntityTypeRegistry;
import betamoon.luaapi.LuaApiUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Client-only, type-keyed model bindings. Gameplay definitions never own GL resources. */
public final class EntityVisuals {
    private static final Map<AssetKey, Binding> BINDINGS = new HashMap<>();

    private EntityVisuals() {
    }

    public static void validate(EntityTypeDefinition definition) throws IOException {
        if (definition.appearance == null) {
            return;
        }
        try (ModelAppearance appearance = new ModelAppearance(definition.appearance)) {
            for (String part : definition.parts.keySet()) {
                if (!appearance.geometry().hasPart(part)) {
                    throw new IOException("Entity part '" + part + "' is missing from the model");
                }
            }
        }
    }

    public static ModelAppearance get(EntityTypeDefinition definition) {
        Binding current = BINDINGS.get(definition.key);
        if (current != null && current.definition == definition) {
            return current.appearance;
        }
        if (current != null && current.failedDefinition == definition) {
            return current.appearance;
        }
        try {
            ModelAppearance replacement = new ModelAppearance(definition.appearance);
            BINDINGS.put(definition.key, new Binding(definition, replacement));
            if (current != null) {
                current.appearance.close();
            }
            return replacement;
        } catch (IOException | RuntimeException error) {
            LuaApiUtils.warn("Entities", definition.key + ": " + error.getMessage()
                    + "; retaining the previous usable appearance");
            if (current != null) {
                current.failedDefinition = definition;
                return current.appearance;
            }
            return null;
        }
    }

    public static void prune() {
        List<EntityTypeDefinition> refresh = new ArrayList<>();
        Iterator<Map.Entry<AssetKey, Binding>> entries = BINDINGS.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<AssetKey, Binding> entry = entries.next();
            EntityTypeDefinition definition = EntityTypeRegistry.find(entry.getKey());
            if (definition == null) {
                entry.getValue().appearance.close();
                entries.remove();
            } else {
                refresh.add(definition);
            }
        }
        for (EntityTypeDefinition definition : refresh) {
            get(definition);
        }
    }

    /** Retry definitions that could not bind when an asset provider changes. */
    public static void retryFailed() {
        for (Map.Entry<AssetKey, Binding> entry : new ArrayList<>(BINDINGS.entrySet())) {
            Binding binding = entry.getValue();
            if (binding.failedDefinition == null) {
                continue;
            }
            EntityTypeDefinition definition = EntityTypeRegistry.find(entry.getKey());
            binding.failedDefinition = null;
            if (definition != null) {
                get(definition);
            }
        }
    }

    private static final class Binding {
        private final EntityTypeDefinition definition;
        private final ModelAppearance appearance;
        private EntityTypeDefinition failedDefinition;

        private Binding(EntityTypeDefinition definition, ModelAppearance appearance) {
            this.definition = definition;
            this.appearance = appearance;
        }
    }
}

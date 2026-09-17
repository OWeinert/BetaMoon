package betamoon.luaapi.item;

import betamoon.client.render.ModelAppearance;
import betamoon.client.render.ModelAppearanceSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Item-specific presentation bindings; native item registration remains
 * unchanged.
 */
public final class ItemModelRegistry {
    private static final Map<Integer, ModelAppearanceSet> MODELS = new LinkedHashMap<>();

    private ItemModelRegistry() {
    }

    public static ModelAppearance get(int id, int metadata) {
        ModelAppearanceSet appearances = MODELS.get(id);
        return appearances == null ? null : appearances.select(metadata);
    }

    static void install(int id, ModelAppearanceSet appearance) {
        ModelAppearanceSet previous = appearance == null ? MODELS.remove(id) : MODELS.put(id, appearance);
        if (previous != null) {
            previous.close();
        }
    }
}

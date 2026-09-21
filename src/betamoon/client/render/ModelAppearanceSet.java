package betamoon.client.render;

import betamoon.luaapi.asset.ModelAppearanceDeclaration;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns preloaded default/variant appearances; selection never loads resources.
 */
public final class ModelAppearanceSet implements AutoCloseable {
    private final Map<Integer, ModelAppearance> variants = new LinkedHashMap<>();
    private final List<ModelAppearance> owned = new ArrayList<>();
    private final ModelAppearance fallback;
    private final boolean dynamic;

    public ModelAppearanceSet(ModelAppearanceDeclaration fallback, Map<Integer, ModelAppearanceDeclaration> variants)
            throws IOException {
        try {
            this.fallback = load(fallback);
            for (Map.Entry<Integer, ModelAppearanceDeclaration> entry : variants.entrySet()) {
                this.variants.put(entry.getKey(), load(entry.getValue()));
            }
        } catch (IOException | RuntimeException error) {
            close();
            throw error;
        }
        boolean requiresDynamic = false;
        for (ModelAppearance appearance : owned) {
            requiresDynamic |= appearance.definition.isDynamic();
        }
        dynamic = requiresDynamic;
    }

    private ModelAppearance load(ModelAppearanceDeclaration definition) throws IOException {
        if (definition == null) {
            return null;
        }
        ModelAppearance appearance = new ModelAppearance(definition);
        owned.add(appearance);
        return appearance;
    }

    public ModelAppearance select(int metadata) {
        return variants.containsKey(metadata) ? variants.get(metadata) : fallback;
    }

    public boolean isDynamic() {
        return dynamic;
    }

    public void onChange(Runnable callback) {
        for (ModelAppearance appearance : owned) {
            appearance.onChange(callback);
        }
    }

    @Override
    public void close() {
        for (ModelAppearance appearance : owned) {
            appearance.close();
        }
    }
}

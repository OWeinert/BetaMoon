package betamoon.worldgen.terrain;

import betamoon.worldgen.GenerationMetrics;
import betamoon.worldgen.spatial.FieldContext;
import betamoon.worldgen.spatial.ScalarField;
import betamoon.worldgen.spatial.ScalarFieldCompiler;
import betamoon.worldgen.spatial.SpatialRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static betamoon.worldgen.spatial.GenerationValues.canonical;
import static betamoon.worldgen.spatial.GenerationValues.error;
import static betamoon.worldgen.spatial.GenerationValues.fields;
import static betamoon.worldgen.spatial.GenerationValues.list;
import static betamoon.worldgen.spatial.GenerationValues.number;
import static betamoon.worldgen.spatial.GenerationValues.object;
import static betamoon.worldgen.spatial.GenerationValues.required;
import static betamoon.worldgen.spatial.GenerationValues.string;

/** Bounded raw-buffer set operations, with no recursive chunk generation. */
public final class ComposedTerrainSource implements TerrainSource {
    private final List<Layer> layers = new ArrayList<Layer>();
    private final String signature;
    private int expandedSourceCount = 1;

    public ComposedTerrainSource(String key, Map<String, Object> value, SpatialRegistry.Snapshot spatial,
            Resolver resolver, Map<String, String> dependencies) {
        fields(value, key, "key", "type", "children");
        Map<String, String> semantics = new TreeMap<String, String>(dependencies);
        semantics.put("compose", canonical(value));
        for (Object declaration : list(required(value, "children", key), key + ".children", 1, 8)) {
            Map<String, Object> layer = object(declaration, key + ".children");
            fields(layer, key, "source", "operation", "mask", "min", "max");
            Object sourceValue = required(layer, "source", key);
            TerrainSource source = sourceValue instanceof String
                    ? resolver.source((String) sourceValue)
                    : resolver.inlineSource(key + "/child_" + layers.size(), object(sourceValue, key + ".source"));
            if (source.isNative()) {
                throw error(key, "composition requires owned raw sources; use beta_compatible for a Beta basis");
            }
            expandedSourceCount += source.expandedSourceCount();
            if (expandedSourceCount > 32) {
                throw error(key, "expanded composition exceeds the 32-source work budget");
            }
            String operation = string(layer, "operation", layers.isEmpty() ? "replace" : "union", key);
            if (!operation.equals("replace") && !operation.equals("union") && !operation.equals("intersection")
                    && !operation.equals("subtract")) {
                throw error(key + ".operation", "expected replace, union, intersection or subtract");
            }
            ScalarField mask = layer.containsKey("mask")
                    ? new ScalarFieldCompiler(key, spatial).compile(layer.get("mask"))
                    : null;
            double min = number(layer, "min", 0, key);
            double max = number(layer, "max", mask == null ? 1 : mask.max, key);
            if (min > max || mask != null && (long) mask.sampleWork * 32768 > 8000000) {
                throw error(key, "invalid mask range or composition work budget exceeded");
            }
            layers.add(new Layer(source, operation, mask, min, max));
            semantics.put("child_" + layers.size(), source.signature());
        }
        signature = GenerationSignature.of(semantics);
    }

    @Override
    public void fill(GenerationContext context, ChunkBlockBuffer buffer) {
        GenerationMetrics.record(GenerationMetrics.Counter.COMPOSED_FILLS, 1);
        for (Layer layer : layers) {
            if (layer.mask == null && layer.operation.equals("replace")) {
                buffer.clear();
                layer.source.fill(context, buffer);
                continue;
            }
            ChunkBlockBuffer input = new ChunkBlockBuffer();
            layer.source.fill(context, input);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    boolean columnMask = layer.mask != null && (layer.mask.dependencies & 2) == 0
                            && context.fields.trace == null;
                    double selected = columnMask
                            ? layer.mask.sample(context.fields, context.worldX(x), 0, context.worldZ(z))
                            : 0;
                    for (int y = 0; y < 128; y++) {
                        if (layer.mask != null) {
                            double sample = columnMask
                                    ? selected
                                    : layer.mask.sample(context.fields, context.worldX(x), y, context.worldZ(z));
                            if (sample < layer.min || sample > layer.max) {
                                continue;
                            }
                        }
                        int id = input.blockId(x, y, z);
                        if (layer.operation.equals("replace") || layer.operation.equals("union") && id != 0) {
                            buffer.setGenerated((x << 11) | (z << 7) | y, id, input.metadata(x, y, z));
                        } else if (layer.operation.equals("subtract") && id != 0
                                || layer.operation.equals("intersection") && id == 0) {
                            buffer.setGenerated((x << 11) | (z << 7) | y, 0, 0);
                        }
                    }
                }
            }
        }
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int y = 127;
                while (y >= 0 && buffer.blockId(x, y, z) == 0) {
                    y--;
                }
                context.preliminarySurface[z * 16 + x] = y;
            }
        }
    }

    @Override
    public double height(FieldContext context, int x, int z) {
        return TerrainHeightPreview.heights(this, context, x >> 4, z >> 4).worldSurface(x & 15, z & 15);
    }

    @Override
    public boolean isNative() {
        return false;
    }

    @Override
    public boolean usesHeightProfile(FieldContext context, int x, int z, String key) {
        for (Layer layer : layers) {
            if (layer.source.usesHeightProfile(context, x, z, key)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int expandedSourceCount() {
        return expandedSourceCount;
    }

    @Override
    public String signature() {
        return signature;
    }

    public interface Resolver {
        TerrainSource source(String key);

        default TerrainSource inlineSource(String path, Map<String, Object> value) {
            throw error(path, "inline source resolution is unavailable");
        }
    }

    private static final class Layer {
        private final TerrainSource source;
        private final String operation;
        private final ScalarField mask;
        private final double min;
        private final double max;

        private Layer(TerrainSource source, String operation, ScalarField mask, double min, double max) {
            this.source = source;
            this.operation = operation;
            this.mask = mask;
            this.min = min;
            this.max = max;
        }
    }
}

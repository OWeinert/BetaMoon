package betamoon.worldgen.terrain;

import betamoon.worldgen.SeedMixer;
import betamoon.worldgen.spatial.NoiseDefinition;
import betamoon.worldgen.spatial.ScalarField;
import betamoon.worldgen.spatial.ScalarFieldCompiler;
import betamoon.worldgen.spatial.SpatialRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.src.Block;

import static betamoon.worldgen.spatial.GenerationValues.error;
import static betamoon.worldgen.spatial.GenerationValues.fields;
import static betamoon.worldgen.spatial.GenerationValues.integer;
import static betamoon.worldgen.spatial.GenerationValues.list;
import static betamoon.worldgen.spatial.GenerationValues.number;
import static betamoon.worldgen.spatial.GenerationValues.object;
import static betamoon.worldgen.spatial.GenerationValues.required;
import static betamoon.worldgen.spatial.GenerationValues.string;

/**
 * Compiled deterministic material decisions, separate from terrain occupancy.
 */
public final class MaterialRule {
    private final Evaluator evaluator;
    public final Set<String> identities;
    public final boolean requiresEnvelope;
    public final int nodeCount;
    public final long sampleWork;

    private MaterialRule(Evaluator evaluator, Set<String> identities) {
        this(evaluator, identities, false, 1);
    }

    private MaterialRule(Evaluator evaluator, Set<String> identities, boolean requiresEnvelope, int nodeCount) {
        this(evaluator, identities, requiresEnvelope, nodeCount, nodeCount);
    }

    private MaterialRule(Evaluator evaluator, Set<String> identities, boolean requiresEnvelope, int nodeCount,
            long sampleWork) {
        this.sampleWork = sampleWork;
        if (sampleWork > 2048) {
            throw error("material", "material rule exceeds its 2048-operation per-cell work budget");
        }
        if (nodeCount > 256) {
            throw error("material", "expanded material rules exceed 256 nodes");
        }
        this.requiresEnvelope = requiresEnvelope;
        this.nodeCount = nodeCount;
        this.evaluator = evaluator;
        this.identities = Collections.unmodifiableSet(new TreeSet<String>(identities));
    }

    public TerrainBlockState sample(GenerationContext context, int x, int y, int z) {
        return evaluator.sample(context, x, y, z);
    }

    public static MaterialRule constant(TerrainBlockState state) {
        return new MaterialRule((context, x, y, z) -> state, Collections.singleton(state.identity));
    }

    public static MaterialRule compile(Object value, String path, Resolver resolver, SpatialRegistry.Snapshot spatial,
            int depth) {
        if (depth > 16) {
            throw error(path, "material rule depth exceeds 16");
        }
        if (value instanceof String && ((String) value).contains(":material/")) {
            return resolver.material((String) value);
        }
        if (!(value instanceof Map) || !object(value, path).containsKey("type")) {
            return constant(TerrainBlockState.read(value, path));
        }
        Map<String, Object> object = object(value, path);
        String type = string(required(object, "type", path), path + ".type");
        if (type.equals("block")) {
            fields(object, path, "type", "block", "metadata", "state");
            Map<String, Object> state = new LinkedHashMap<String, Object>(object);
            state.remove("type");
            return constant(TerrainBlockState.read(state, path));
        }
        if (type.equals("reference")) {
            fields(object, path, "type", "material");
            return resolver.material(string(required(object, "material", path), path + ".material"));
        }
        if (type.equals("weighted")) {
            fields(object, path, "type", "choices", "salt");
            List<Object> choices = list(required(object, "choices", path), path + ".choices", 1, 64);
            List<MaterialRule> rules = new ArrayList<MaterialRule>();
            int[] weights = new int[choices.size()];
            Set<String> identities = new TreeSet<String>();
            int sum = 0;
            for (int i = 0; i < choices.size(); i++) {
                Map<String, Object> choice = object(choices.get(i), path + ".choices[" + (i + 1) + "]");
                fields(choice, path, "block", "metadata", "state", "weight");
                Map<String, Object> state = new LinkedHashMap<String, Object>(choice);
                state.remove("weight");
                MaterialRule rule = constant(TerrainBlockState.read(state, path));
                rules.add(rule);
                identities.addAll(rule.identities);
                weights[i] = integer(choice.getOrDefault("weight", 1.0), path + ".weight", 1, 1024);
                sum += weights[i];
            }
            final int total = sum;
            long salt = SeedMixer.hash(path + ":" + object.getOrDefault("salt", "0"));
            return new MaterialRule((context, x, y, z) -> {
                int selected = (int) (NoiseDefinition.unit(SeedMixer.derive(context.fields.seed, salt),
                        context.worldX(x), y, context.worldZ(z)) * total);
                for (int i = 0; i < weights.length; i++) {
                    selected -= weights[i];
                    if (selected < 0) {
                        return rules.get(i).sample(context, x, y, z);
                    }
                }
                throw new AssertionError("Weighted material choice exhausted");
            }, identities, false, 1, choices.size() + 1);
        }
        if (type.equals("sequence")) {
            fields(object, path, "type", "rules");
            List<Object> declarations = list(required(object, "rules", path), path + ".rules", 1, 64);
            List<MaterialRule> rules = new ArrayList<MaterialRule>();
            Set<String> identities = new TreeSet<String>();
            for (int i = 0; i < declarations.size(); i++) {
                MaterialRule rule = compile(declarations.get(i), path + ".rules[" + (i + 1) + "]", resolver, spatial,
                        depth + 1);
                rules.add(rule);
                identities.addAll(rule.identities);
            }
            boolean needsEnvelope = false;
            int nodes = 1;
            long work = 1;
            for (MaterialRule rule : rules) {
                needsEnvelope |= rule.requiresEnvelope;
                nodes += rule.nodeCount;
                work += rule.sampleWork;
            }
            return new MaterialRule((context, x, y, z) -> {
                for (MaterialRule rule : rules) {
                    TerrainBlockState state = rule.sample(context, x, y, z);
                    if (state != null) {
                        return state;
                    }
                }
                return null;
            }, identities, needsEnvelope, nodes, work);
        }
        if (type.equals("when")) {
            fields(object, path, "type", "condition", "thenRule", "elseRule");
            Condition condition = condition(object(required(object, "condition", path), path + ".condition"), path,
                    spatial);
            MaterialRule yes = compile(required(object, "thenRule", path), path + ".thenRule", resolver, spatial,
                    depth + 1);
            MaterialRule no = object.containsKey("elseRule")
                    ? compile(object.get("elseRule"), path + ".elseRule", resolver, spatial, depth + 1)
                    : null;
            Set<String> identities = new TreeSet<String>(yes.identities);
            if (no != null) {
                identities.addAll(no.identities);
            }
            return new MaterialRule(
                    (context, x, y, z) -> condition.matches(context, x, y, z)
                            ? yes.sample(context, x, y, z)
                            : no == null ? null : no.sample(context, x, y, z),
                    identities,
                    condition.requiresEnvelope() || yes.requiresEnvelope || no != null && no.requiresEnvelope,
                    1 + condition.nodeCount() + yes.nodeCount + (no == null ? 0 : no.nodeCount),
                    1 + condition.sampleWork() + Math.max(yes.sampleWork, no == null ? 0 : no.sampleWork));
        }
        throw error(path, "unknown material rule: " + type);
    }

    private static Condition condition(Map<String, Object> value, String path, SpatialRegistry.Snapshot spatial) {
        return condition(value, path, spatial, 0);
    }

    private static Condition condition(Map<String, Object> value, String path, SpatialRegistry.Snapshot spatial,
            int depth) {
        if (depth > 8) {
            throw error(path, "material condition depth exceeds 8");
        }
        String type = string(required(value, "type", path), path + ".type");
        switch (type) {
            case "all":
            case "any": {
                fields(value, path, "type", "conditions");
                List<Condition> conditions = new ArrayList<Condition>();
                for (Object child : list(required(value, "conditions", path), path + ".conditions", 1, 8)) {
                    conditions.add(condition(object(child, path), path, spatial, depth + 1));
                }
                boolean needsEnvelope = false;
                int nodes = 1;
                long work = 1;
                for (Condition child : conditions) {
                    needsEnvelope |= child.requiresEnvelope();
                    nodes += child.nodeCount();
                    work += child.sampleWork();
                }
                return checked((context, x, y, z) -> {
                    for (Condition child : conditions) {
                        boolean matches = child.matches(context, x, y, z);
                        if (type.equals("all") && !matches) {
                            return false;
                        }
                        if (type.equals("any") && matches) {
                            return true;
                        }
                    }
                    return type.equals("all");
                }, needsEnvelope, nodes, work);
            }
            case "not": {
                fields(value, path, "type", "condition");
                Condition child = condition(object(required(value, "condition", path), path), path, spatial, depth + 1);
                return checked((context, x, y, z) -> !child.matches(context, x, y, z), child.requiresEnvelope(),
                        child.nodeCount() + 1, child.sampleWork() + 1);
            }
            case "stone_depth":
            case "liquid_depth":
            case "derived_surface_depth":
            case "slope": {
                fields(value, path, "type", "min", "max");
                double min = number(value, "min", 0, path);
                double max = number(value, "max", 128, path);
                if (min > max) {
                    throw error(path, "material condition range must be ordered");
                }
                return checked((context, x, y, z) -> {
                    double sample;
                    if (type.equals("derived_surface_depth")) {
                        sample = context.inputSurface(x, z) - y;
                    } else if (type.equals("slope")) {
                        sample = context.slope(x, z);
                    } else {
                        sample = context.depthAbove(x, y, z, type.equals("liquid_depth"));
                    }
                    return sample >= min && sample <= max;
                }, type.equals("slope"), 1, type.equals("slope") ? 1024 : 128);
            }
            case "exposure": {
                fields(value, path, "type", "to");
                String target = string(value, "to", "air", path);
                if (!target.equals("air") && !target.equals("fluid")) {
                    throw error(path, "exposure target must be air or fluid");
                }
                return (context, x, y, z) -> {
                    int id = context.inputBlock(x, y + 1, z);
                    return target.equals("air") ? id == 0 : id != 0 && Block.blocksList[id].blockMaterial.getIsLiquid();
                };
            }
            case "below_y": {
                fields(value, path, "type", "y");
                double limit = number(required(value, "y", path), path + ".y");
                return (context, x, y, z) -> y < limit;
            }
            case "y_range":
            case "surface_depth": {
                fields(value, path, "type", "min", "max");
                double min = number(required(value, "min", path), path + ".min");
                double max = number(required(value, "max", path), path + ".max");
                if (min > max) {
                    throw error(path, "material condition range must be ordered");
                }
                return checked((context, x, y, z) -> {
                    double sample = type.equals("y_range") ? y : context.preliminarySurface[z * 16 + x] - y;
                    return sample >= min && sample <= max;
                }, type.equals("surface_depth"), 1);
            }
            case "biome": {
                fields(value, path, "type", "selector");
                String selector = string(required(value, "selector", path), path + ".selector");
                return (context, x, y, z) -> context.biomeMatches(x, z, selector);
            }
            case "field": {
                fields(value, path, "type", "field", "min", "max");
                ScalarField field = new ScalarFieldCompiler(path, spatial).compile(required(value, "field", path));
                double min = number(value, "min", field.min, path);
                double max = number(value, "max", field.max, path);
                if (min > max) {
                    throw error(path, "material field range must be ordered");
                }
                return checked((context, x, y, z) -> {
                    double sample = field.sample(context.fields, context.worldX(x), y, context.worldZ(z));
                    return sample >= min && sample <= max;
                }, false, 1, field.sampleWork);
            }
            default:
                throw error(path, "unknown material condition: " + type);
        }
    }

    public interface Resolver {
        MaterialRule material(String key);
    }

    private interface Evaluator {
        TerrainBlockState sample(GenerationContext context, int x, int y, int z);
    }

    private static Condition checked(Condition delegate, boolean requiresEnvelope, int nodes) {
        return checked(delegate, requiresEnvelope, nodes, nodes);
    }

    private static Condition checked(Condition delegate, boolean requiresEnvelope, int nodes, long work) {
        if (work > 2048) {
            throw error("material.condition", "condition exceeds its 2048-operation per-cell work budget");
        }
        if (nodes > 256) {
            throw error("material.condition", "expanded condition graph exceeds 256 nodes");
        }
        return new Condition() {
            public boolean matches(GenerationContext context, int x, int y, int z) {
                return delegate.matches(context, x, y, z);
            }

            public boolean requiresEnvelope() {
                return requiresEnvelope;
            }

            public int nodeCount() {
                return nodes;
            }

            public long sampleWork() {
                return work;
            }
        };
    }

    private interface Condition {
        boolean matches(GenerationContext context, int x, int y, int z);

        default boolean requiresEnvelope() {
            return false;
        }

        default long sampleWork() {
            return 1;
        }

        default int nodeCount() {
            return 1;
        }
    }
}

package betamoon.atmosphere;

import betamoon.luaapi.audio.SoundEvents;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.src.World;

import static betamoon.worldgen.spatial.GenerationValues.error;
import static betamoon.worldgen.spatial.GenerationValues.fields;
import static betamoon.worldgen.spatial.GenerationValues.integer;
import static betamoon.worldgen.spatial.GenerationValues.required;
import static betamoon.worldgen.spatial.GenerationValues.string;

/** Owner-local publication and bounded temporary presentation layers. */
public final class AtmosphereRegistry {
    private static final ThreadLocal<Batch> CURRENT = new ThreadLocal<Batch>();
    private static Map<String, OwnedProfile> profiles = Collections.emptyMap();
    private static List<Binding> bindings = Collections.emptyList();
    private static final Map<Long, Layer> LAYERS = new LinkedHashMap<Long, Layer>();
    private static long nextId;
    private static long tick;

    private AtmosphereRegistry() {
    }

    public static Batch begin(String owner) {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("Nested atmosphere publication is unsupported");
        }
        Batch batch = new Batch(owner);
        CURRENT.set(batch);
        return batch;
    }

    public static String add(Map<String, Object> value) {
        Batch batch = batch();
        AtmosphereProfile profile = new AtmosphereProfile(value);
        if (batch.profiles.put(profile.key, new OwnedProfile(batch.owner, profile)) != null
                || batch.profiles.size() > 64) {
            throw error(profile.key, "duplicate atmosphere key or owner profile budget exceeded");
        }
        return profile.key;
    }

    public static void bind(Map<String, Object> value) {
        Batch batch = batch();
        fields(value, "atmosphere.bind", "profile", "scope", "priority", "blendBlocks");
        batch.bindings.add(new Binding(batch.owner, string(required(value, "profile", "atmosphere.bind"), "profile"),
                new AtmosphereScope(required(value, "scope", "atmosphere.bind"), false, "atmosphere.scope"),
                priority(value), blend(value)));
        if (batch.bindings.size() > 64) {
            throw error("atmosphere.bind", "owner binding budget exceeded");
        }
    }

    public static synchronized AtmosphereProfile get(String key) {
        Batch batch = CURRENT.get();
        OwnedProfile value = batch == null ? null : batch.profiles.get(key);
        if (value == null) {
            value = profiles.get(key);
        }
        return value == null ? null : value.profile;
    }

    public static synchronized long activate(String owner, String key, World world, Map<String, Object> value) {
        fields(value, "atmosphere.activate", "scope", "priority", "blendBlocks", "durationTicks", "fadeTicks");
        if (get(key) == null || world == null) {
            throw error("atmosphere.activate", "unknown profile or unavailable world");
        }
        prune();
        if (LAYERS.size() >= 64) {
            throw error("atmosphere.activate", "temporary atmosphere layer budget exceeded");
        }
        int duration = value.containsKey("durationTicks")
                ? integer(value.get("durationTicks"), "atmosphere.durationTicks", 1, 24000)
                : Integer.MAX_VALUE;
        int fade = integer(value.getOrDefault("fadeTicks", 20.0), "atmosphere.fadeTicks", 0, 1200);
        long id = ++nextId;
        Binding binding = new Binding(owner, key,
                new AtmosphereScope(required(value, "scope", "atmosphere.activate"), true, "atmosphere.scope"),
                priority(value), blend(value));
        LAYERS.put(id, new Layer(binding, world, tick, duration, fade));
        return id;
    }

    public static synchronized void close(long id) {
        Layer layer = LAYERS.get(id);
        if (layer != null && layer.closedAt < 0) {
            layer.closedAt = tick;
        }
        prune();
    }

    public static synchronized boolean isActive(long id) {
        prune();
        Layer layer = LAYERS.get(id);
        return layer != null && layer.closedAt < 0 && tick < layer.started + layer.duration;
    }

    public static synchronized void tick(World currentWorld) {
        tick++;
        LAYERS.values().removeIf(layer -> layer.world.get() != currentWorld);
        prune();
    }

    public static synchronized List<Contribution> query(World world, double x, double y, double z) {
        return compose(candidates(world, x, y, z), -1);
    }

    public static synchronized Presentation queryPresentation(World world, double x, double y, double z) {
        return new Presentation(candidates(world, x, y, z));
    }

    private static List<Contribution> candidates(World world, double x, double y, double z) {
        prune();
        List<Contribution> candidates = new ArrayList<Contribution>();
        for (Binding binding : bindings) {
            addContribution(candidates, binding, binding.scope.weight(world, x, y, z, binding.blend));
        }
        for (Layer layer : LAYERS.values()) {
            if (layer.world.get() == world) {
                addContribution(candidates, layer.binding,
                        layer.scopeWeight(tick) * layer.binding.scope.weight(world, x, y, z, layer.binding.blend));
            }
        }
        Collections.sort(candidates,
                (a, b) -> a.priority != b.priority
                        ? Integer.compare(b.priority, a.priority)
                        : a.profile.key.compareTo(b.profile.key));
        return candidates;
    }

    private static List<Contribution> compose(List<Contribution> candidates, int channel) {
        List<Contribution> result = new ArrayList<Contribution>();
        double remaining = 1;
        int cursor = 0;
        while (cursor < candidates.size() && remaining > 0.000001) {
            int end = cursor;
            int priority = candidates.get(cursor).priority;
            double total = 0;
            while (end < candidates.size() && candidates.get(end).priority == priority) {
                Contribution value = candidates.get(end++);
                if (hasChannel(value.profile, channel)) {
                    total += value.weight;
                }
            }
            double denominator = Math.max(1, total);
            for (int i = cursor; i < end; i++) {
                Contribution value = candidates.get(i);
                if (hasChannel(value.profile, channel)) {
                    result.add(new Contribution(value.profile, value.priority, remaining * value.weight / denominator));
                }
            }
            remaining *= 1 - Math.min(1, total);
            cursor = end;
        }
        return Collections.unmodifiableList(result);
    }

    private static boolean hasChannel(AtmosphereProfile profile, int channel) {
        switch (channel) {
            case 0:
                return profile.hasFog;
            case 1:
                return profile.sky != null;
            case 2:
                return profile.lightTone != null;
            case 3:
                return profile.ambient != null;
            case 4:
                return profile.weather != null;
            default:
                return true;
        }
    }

    public static final class Presentation {
        private final List<List<Contribution>> channels = new ArrayList<List<Contribution>>(5);
        private final List<Contribution> profiles;

        private Presentation(List<Contribution> candidates) {
            profiles = compose(candidates, -1);
            for (int channel = 0; channel < 5; channel++) {
                channels.add(compose(candidates, channel));
            }
        }

        public List<Contribution> profiles() {
            return profiles;
        }

        public List<Contribution> channel(int index) {
            return channels.get(index);
        }
    }

    public static synchronized void retainOwners(Set<String> owners) {
        Map<String, OwnedProfile> retained = new TreeMap<String, OwnedProfile>();
        for (OwnedProfile profile : profiles.values()) {
            if (owners.contains(profile.owner)) {
                retained.put(profile.profile.key, profile);
            }
        }
        List<Binding> retainedBindings = new ArrayList<Binding>();
        for (Binding binding : bindings) {
            if (owners.contains(binding.owner) && retained.containsKey(binding.profile)) {
                retainedBindings.add(binding);
            }
        }
        profiles = Collections.unmodifiableMap(retained);
        bindings = Collections.unmodifiableList(retainedBindings);
        LAYERS.values().removeIf(
                layer -> !owners.contains(layer.binding.owner) || !retained.containsKey(layer.binding.profile));
    }

    private static void addContribution(List<Contribution> output, Binding binding, double weight) {
        OwnedProfile profile = profiles.get(binding.profile);
        if (weight > 0 && profile != null) {
            output.add(new Contribution(profile.profile, binding.priority, weight));
        }
    }

    private static void prune() {
        LAYERS.values().removeIf(layer -> layer.world.get() == null
                || layer.scopeWeight(tick) <= 0 && (tick > layer.started || layer.closedAt >= 0));
    }

    private static Batch batch() {
        Batch batch = CURRENT.get();
        if (batch == null) {
            throw error("atmosphere", "profiles and bindings belong in initialization callbacks");
        }
        return batch;
    }

    private static int priority(Map<String, Object> value) {
        return integer(value.getOrDefault("priority", 0.0), "atmosphere.priority", -1000000, 1000000);
    }

    private static int blend(Map<String, Object> value) {
        return integer(value.getOrDefault("blendBlocks", 0.0), "atmosphere.blendBlocks", 0, 256);
    }

    public static final class Batch implements AutoCloseable {
        private final String owner;
        private final Map<String, OwnedProfile> profiles = new TreeMap<String, OwnedProfile>();
        private final List<Binding> bindings = new ArrayList<Binding>();

        private Batch(String owner) {
            this.owner = owner;
        }

        public void validate() {
            synchronized (AtmosphereRegistry.class) {
                Map<String, OwnedProfile> combined = combined();
                for (OwnedProfile value : profiles.values()) {
                    AtmosphereProfile profile = value.profile;
                    if (profile.ambient != null && !SoundEvents.exists(profile.ambient.key)
                            || profile.weather != null && !SoundEvents.exists(profile.weather.key)) {
                        throw error(profile.key, "unknown atmosphere sound event");
                    }
                }
                int retainedBindings = 0;
                for (Binding binding : AtmosphereRegistry.bindings) {
                    if (!binding.owner.equals(owner)) {
                        retainedBindings++;
                        if (!combined.containsKey(binding.profile)) {
                            throw error(binding.profile, "profile is still referenced by another script's binding");
                        }
                    }
                }
                for (Binding binding : bindings) {
                    if (!combined.containsKey(binding.profile)) {
                        throw error(binding.profile, "unknown atmosphere binding profile");
                    }
                }
                if (combined.size() > 1024 || retainedBindings + bindings.size() > 64) {
                    throw error("atmosphere", "global declaration budget exceeded");
                }
            }
        }

        private Map<String, OwnedProfile> combined() {
            Map<String, OwnedProfile> result = new TreeMap<String, OwnedProfile>();
            for (OwnedProfile profile : AtmosphereRegistry.profiles.values()) {
                if (!profile.owner.equals(owner)) {
                    result.put(profile.profile.key, profile);
                }
            }
            for (OwnedProfile profile : profiles.values()) {
                if (result.put(profile.profile.key, profile) != null) {
                    throw error(profile.profile.key, "atmosphere key belongs to another owner");
                }
            }
            return result;
        }

        public void publish() {
            synchronized (AtmosphereRegistry.class) {
                validate();
                List<Binding> result = new ArrayList<Binding>();
                for (Binding binding : AtmosphereRegistry.bindings) {
                    if (!binding.owner.equals(owner)) {
                        result.add(binding);
                    }
                }
                result.addAll(bindings);
                AtmosphereRegistry.profiles = Collections.unmodifiableMap(combined());
                AtmosphereRegistry.bindings = Collections.unmodifiableList(result);
                LAYERS.values().removeIf(layer -> layer.binding.owner.equals(owner));
            }
        }

        @Override
        public void close() {
            CURRENT.remove();
        }
    }

    public static final class Contribution {
        public final AtmosphereProfile profile;
        public final int priority;
        public final double weight;

        private Contribution(AtmosphereProfile profile, int priority, double weight) {
            this.profile = profile;
            this.priority = priority;
            this.weight = weight;
        }
    }

    private static final class OwnedProfile {
        private final String owner;
        private final AtmosphereProfile profile;

        private OwnedProfile(String owner, AtmosphereProfile profile) {
            this.owner = owner;
            this.profile = profile;
        }
    }

    private static final class Binding {
        private final String owner;
        private final String profile;
        private final AtmosphereScope scope;
        private final int priority;
        private final int blend;

        private Binding(String owner, String profile, AtmosphereScope scope, int priority, int blend) {
            this.owner = owner;
            this.profile = profile;
            this.scope = scope;
            this.priority = priority;
            this.blend = blend;
        }
    }

    private static final class Layer {
        private final Binding binding;
        private final WeakReference<World> world;
        private final long started;
        private final int duration;
        private final int fade;
        private long closedAt = -1;

        private Layer(Binding binding, World world, long started, int duration, int fade) {
            this.binding = binding;
            this.world = new WeakReference<World>(world);
            this.started = started;
            this.duration = duration;
            this.fade = fade;
        }

        private double scopeWeight(long tick) {
            long end = closedAt < 0 ? started + duration : Math.min(closedAt, started + duration);
            if (fade == 0) {
                return tick < end ? 1 : 0;
            }
            double entering = Math.min(1, (tick - started) / (double) fade);
            double leaving = tick <= end ? 1 : Math.max(0, 1 - (tick - end) / (double) fade);
            return entering * leaving;
        }
    }
}

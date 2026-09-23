package betamoon.entity;

import betamoon.assets.AssetKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Script-owned types are replaced atomically after successful initialization. */
public final class EntityTypeRegistry {
    private static Map<AssetKey, Entry> active = Collections.emptyMap();

    private EntityTypeRegistry() {
    }

    /** Immutable, callback-free description captured from one registry generation. */
    public static final class Description {
        public final String owner;
        public final AssetKey key;
        public final String kind;
        public final String lifecycle;
        public final String displayName;
        public final float width;
        public final float height;
        public final int tickInterval;
        public final List<String> capabilities;
        public final String modelPath;
        public final String modelOverridePath;
        public final boolean dynamicAppearance;
        public final String aiMode;
        public final String aggression;
        public final SpawnDescription spawning;
        public final List<DataDescription> data;
        public final List<PartDescription> parts;
        public final List<String> callbacks;

        private Description(Entry entry) {
            EntityTypeDefinition definition = entry.definition;
            owner = entry.owner;
            key = definition.key;
            kind = definition.kind.name().toLowerCase(java.util.Locale.ROOT);
            lifecycle = definition.lifecycle.name().toLowerCase(java.util.Locale.ROOT);
            displayName = definition.displayName;
            width = definition.width;
            height = definition.height;
            tickInterval = definition.tickInterval;
            capabilities = capabilities(definition);
            modelPath = definition.appearance == null ? null : definition.appearance.model.getFallback().toString();
            modelOverridePath = definition.appearance == null
                    ? null : definition.appearance.model.getOverride().toString();
            dynamicAppearance = definition.appearance != null && definition.appearance.isDynamic();
            aiMode = definition.living == null
                    ? null : definition.living.ai.name().toLowerCase(java.util.Locale.ROOT);
            aggression = definition.living == null
                    ? null : definition.living.aggression.name().toLowerCase(java.util.Locale.ROOT);
            spawning = definition.spawning == null ? null : new SpawnDescription(definition.spawning);
            List<DataDescription> dataValues = new ArrayList<DataDescription>();
            for (EntityDataField field : definition.data.values()) {
                dataValues.add(new DataDescription(field));
            }
            data = Collections.unmodifiableList(dataValues);
            List<PartDescription> partValues = new ArrayList<PartDescription>();
            for (EntityPartDefinition part : definition.parts.values()) {
                partValues.add(new PartDescription(part));
            }
            parts = Collections.unmodifiableList(partValues);
            callbacks = callbacks(definition);
        }

        private static List<String> capabilities(EntityTypeDefinition definition) {
            List<String> values = new ArrayList<String>();
            add(values, "body", definition.body);
            add(values, "render", definition.render);
            add(values, "sounds", definition.sounds);
            add(values, "spawning", definition.spawning);
            add(values, "inventory", definition.inventory);
            add(values, "equipment", definition.equipment);
            add(values, "relations", definition.relations);
            add(values, "mount", definition.mount);
            add(values, "behavior", definition.behavior);
            add(values, "projectile", definition.projectile);
            add(values, "living", definition.living);
            add(values, "pickup", definition.pickup);
            add(values, "physics", definition.physics);
            add(values, "health", definition.health);
            add(values, "drops", definition.drops);
            if (!definition.data.isEmpty()) {
                values.add("data");
            }
            if (!definition.parts.isEmpty()) {
                values.add("parts");
            }
            if (!definition.sensors.isEmpty()) {
                values.add("sensors");
            }
            return Collections.unmodifiableList(values);
        }

        private static List<String> callbacks(EntityTypeDefinition definition) {
            List<String> values = new ArrayList<String>();
            addCallback(values, "onInteract", definition.onInteract);
            addCallback(values, "onImpact", definition.onImpact);
            addCallback(values, "onTick", definition.onTick);
            addCallback(values, "onSpawn", definition.onSpawn);
            addCallback(values, "onLoad", definition.onLoad);
            addCallback(values, "onDeath", definition.onDeath);
            addCallback(values, "onRemove", definition.onRemove);
            addCallback(values, "onPickup", definition.onPickup);
            addCallback(values, "onActivate", definition.onActivate);
            addCallback(values, "onDeactivate", definition.onDeactivate);
            addCallback(values, "onBeforeDamage", definition.onBeforeDamage);
            addCallback(values, "onAfterDamage", definition.onAfterDamage);
            return Collections.unmodifiableList(values);
        }

        private static void add(List<String> values, String name, Object value) {
            if (value != null) {
                values.add(name);
            }
        }

        private static void addCallback(List<String> values, String name, org.luaj.vm2.LuaValue value) {
            if (value != null && !value.isnil()) {
                values.add(name);
            }
        }
    }

    public static final class DataDescription {
        public final String name;
        public final String type;
        public final String defaultValue;

        private DataDescription(EntityDataField field) {
            name = field.name;
            type = field.describeType();
            defaultValue = String.valueOf(field.defaultValue);
        }
    }

    public static final class PartDescription {
        public final String name;
        public final boolean hitbox;
        public final boolean interactionBox;

        private PartDescription(EntityPartDefinition part) {
            name = part.name;
            hitbox = part.hitbox != null;
            interactionBox = part.interactionBox != null;
        }
    }

    public static final class SpawnDescription {
        public final String category;
        public final int weight;
        public final int groupMin;
        public final int groupMax;
        public final int cap;
        public final int minLight;
        public final int maxLight;
        public final int minY;
        public final int maxY;
        public final List<Integer> dimensions;
        public final List<String> biomes;
        public final List<Integer> substrates;
        public final boolean nativeDespawn;

        private SpawnDescription(EntitySpawnDefinition spawn) {
            category = spawn.category.name().toLowerCase(java.util.Locale.ROOT);
            weight = spawn.weight;
            groupMin = spawn.groupMin;
            groupMax = spawn.groupMax;
            cap = spawn.cap;
            minLight = spawn.minLight;
            maxLight = spawn.maxLight;
            minY = spawn.minY;
            maxY = spawn.maxY;
            dimensions = immutableSorted(spawn.dimensions);
            biomes = immutableSorted(spawn.biomes);
            substrates = immutableSorted(spawn.substrates);
            nativeDespawn = spawn.nativeDespawn;
        }

        private static <T extends Comparable<? super T>> List<T> immutableSorted(Set<T> source) {
            List<T> result = new ArrayList<T>(source);
            Collections.sort(result);
            return Collections.unmodifiableList(result);
        }
    }

    public static synchronized EntityTypeDefinition find(AssetKey key) {
        Entry entry = active.get(key);
        return entry == null ? null : entry.definition;
    }

    public static synchronized String owner(AssetKey key) {
        Entry entry = active.get(key);
        return entry == null ? null : entry.owner;
    }

    public static synchronized List<EntityTypeDefinition> definitions() {
        List<EntityTypeDefinition> result = new ArrayList<>();
        for (Entry entry : active.values()) {
            result.add(entry.definition);
        }
        return Collections.unmodifiableList(result);
    }

    public static synchronized List<Description> snapshot() {
        List<Description> result = new ArrayList<Description>();
        for (Entry entry : active.values()) {
            result.add(new Description(entry));
        }
        return Collections.unmodifiableList(result);
    }

    public static synchronized void publish(String owner, Map<AssetKey, EntityTypeDefinition> definitions) {
        for (EntityTypeDefinition definition : definitions.values()) {
            validate(owner, definition);
        }
        Map<AssetKey, Entry> next = new LinkedHashMap<>(active);
        next.entrySet().removeIf(entry -> entry.getValue().owner.equals(owner));
        for (EntityTypeDefinition definition : definitions.values()) {
            next.put(definition.key, new Entry(owner, definition));
        }
        active = Collections.unmodifiableMap(next);
    }

    public static synchronized void validate(String owner, EntityTypeDefinition definition) {
        Entry old = active.get(definition.key);
        if (old != null && !old.owner.equals(owner)) {
            throw new IllegalArgumentException("Entity type " + definition.key + " belongs to " + old.owner);
        }
        if (old == null) {
            return;
        }
        if (old.definition.kind != definition.kind) {
            throw new IllegalArgumentException("Changing entity kind for " + definition.key
                    + " requires an explicit instance conversion");
        }
        int previousInventory = old.definition.inventory == null ? 0 : old.definition.inventory.size;
        int nextInventory = definition.inventory == null ? 0 : definition.inventory.size;
        if (previousInventory != nextInventory) {
            throw new IllegalArgumentException("Changing inventory size for " + definition.key
                    + " requires an explicit inventory conversion");
        }
        Set<EntityEquipmentDefinition.Slot> previousEquipment = old.definition.equipment == null
                ? Collections.emptySet() : old.definition.equipment.slots;
        Set<EntityEquipmentDefinition.Slot> nextEquipment = definition.equipment == null
                ? Collections.emptySet() : definition.equipment.slots;
        if (!previousEquipment.equals(nextEquipment)) {
            throw new IllegalArgumentException("Changing equipment slots for " + definition.key
                    + " requires an explicit equipment conversion");
        }
        for (EntityDataField field : definition.data.values()) {
            EntityDataField previous = old.definition.data.get(field.name);
            if (previous != null && !field.isCompatibleWith(previous)) {
                throw new IllegalArgumentException("Changing data." + field.name + " from "
                        + previous.describeType() + " to " + field.describeType() + " on " + definition.key
                        + " requires conversion");
            }
        }
    }

    public static synchronized void retainOwners(Set<String> scriptFiles) {
        Map<AssetKey, Entry> next = new LinkedHashMap<>(active);
        next.entrySet().removeIf(entry -> !scriptFiles.contains(entry.getValue().owner));
        active = Collections.unmodifiableMap(next);
    }

    public static synchronized void validateDrops(List<String> errors) {
        for (Entry entry : active.values()) {
            entry.definition.drops.validateRegistered(entry.definition.key, errors);
        }
    }

    public static synchronized void clear() {
        active = Collections.emptyMap();
    }

    private static final class Entry {
        private final String owner;
        private final EntityTypeDefinition definition;

        private Entry(String owner, EntityTypeDefinition definition) {
            this.owner = owner;
            this.definition = definition;
        }
    }
}

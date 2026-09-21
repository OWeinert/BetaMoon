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

package betamoon.fuel;

import betamoon.assets.AssetKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.src.Item;
import net.minecraft.src.ItemStack;

/**
 * Thread-safe registry for composable fuel sets. Mutations publish an immutable
 * snapshot so furnace and machine lookups do not lock the game thread.
 */
public final class FuelRegistry {
    public static final AssetKey FURNACE = AssetKey.parse("minecraft:fuel/furnace");
    public static final int MAX_BURN_TIME = Integer.MAX_VALUE / 2;

    private static final Map<AssetKey, FuelSetDefinition> SETS = new LinkedHashMap<AssetKey, FuelSetDefinition>();
    private static final Map<Long, FuelRegistration> REGISTRATIONS = new LinkedHashMap<Long, FuelRegistration>();
    private static volatile Snapshot snapshot;
    private static long nextRegistrationId = 1L;

    static {
        SETS.put(FURNACE, new FuelSetDefinition(FURNACE, "minecraft", Collections.<AssetKey>emptyList(), true));
        publish();
    }

    private FuelRegistry() {
    }

    public static synchronized FuelSetDefinition addSet(String owner, AssetKey key, List<AssetKey> includes) {
        requireOwner(owner);
        requireFuelKey(key);
        if (includes == null) {
            throw new IllegalArgumentException("Fuel-set includes are required.");
        }
        if (FURNACE.equals(key)) {
            throw new IllegalArgumentException("The built-in furnace fuel set cannot be replaced.");
        }
        Set<AssetKey> uniqueIncludes = new HashSet<AssetKey>();
        for (AssetKey included : includes) {
            if (!uniqueIncludes.add(included)) {
                throw new IllegalArgumentException("Fuel set includes '" + included + "' more than once.");
            }
            if (!SETS.containsKey(included)) {
                throw new IllegalArgumentException("Unknown included fuel set: " + included);
            }
        }
        FuelSetDefinition existing = SETS.get(key);
        if (existing != null) {
            throw new IllegalArgumentException("Fuel set '" + key + "' is already owned by " + existing.owner + ".");
        }

        FuelSetDefinition definition = new FuelSetDefinition(key, owner, includes, false);
        Map<AssetKey, FuelSetDefinition> candidate = new LinkedHashMap<AssetKey, FuelSetDefinition>(SETS);
        candidate.put(key, definition);
        ensureAcyclic(candidate, key, new HashSet<AssetKey>(), new HashSet<AssetKey>());
        SETS.put(key, definition);
        publish();
        return definition;
    }

    public static synchronized boolean removeSet(FuelSetDefinition definition) {
        if (definition == null || definition.builtIn || SETS.get(definition.key) != definition) {
            return false;
        }
        SETS.remove(definition.key);
        List<Long> removed = new ArrayList<Long>();
        for (FuelRegistration registration : REGISTRATIONS.values()) {
            if (definition.key.equals(registration.setKey)) {
                removed.add(Long.valueOf(registration.id));
            }
        }
        for (Long id : removed) {
            REGISTRATIONS.remove(id);
        }
        publish();
        return true;
    }

    public static synchronized FuelRegistration add(String owner, AssetKey setKey, int itemId, Integer damage,
            int burnTime) {
        requireOwner(owner);
        if (!SETS.containsKey(setKey)) {
            throw new IllegalArgumentException("Unknown fuel set: " + setKey);
        }
        if (itemId < 0 || itemId >= Item.itemsList.length || Item.itemsList[itemId] == null) {
            throw new IllegalArgumentException("Fuel item ID is not registered: " + itemId);
        }
        if (damage != null && damage.intValue() < 0) {
            throw new IllegalArgumentException("Fuel damage cannot be negative.");
        }
        if (burnTime <= 0 || burnTime > MAX_BURN_TIME) {
            throw new IllegalArgumentException("Fuel burn time must be from 1 to " + MAX_BURN_TIME + ".");
        }

        for (FuelRegistration registration : REGISTRATIONS.values()) {
            if (registration.setKey.equals(setKey) && registration.itemId == itemId
                    && equals(registration.damage, damage)) {
                throw new IllegalArgumentException("Fuel rule is already owned by " + registration.owner + ".");
            }
        }
        FuelRegistration registration = new FuelRegistration(nextRegistrationId++, owner, setKey, itemId, damage,
                burnTime);
        REGISTRATIONS.put(Long.valueOf(registration.id), registration);
        publish();
        return registration;
    }

    public static synchronized boolean remove(FuelRegistration registration) {
        if (registration == null || REGISTRATIONS.get(Long.valueOf(registration.id)) != registration) {
            return false;
        }
        REGISTRATIONS.remove(Long.valueOf(registration.id));
        publish();
        return true;
    }

    public static FuelSetDefinition findSet(AssetKey key) {
        return snapshot.sets.get(key);
    }

    public static boolean contains(FuelSetDefinition definition) {
        return definition != null && snapshot.sets.get(definition.key) == definition;
    }

    public static boolean contains(FuelRegistration registration) {
        return registration != null && snapshot.registrationsById.get(Long.valueOf(registration.id)) == registration;
    }

    public static FuelResolution resolve(ItemStack stack, AssetKey setKey) {
        if (stack == null || stack.getItem() == null) {
            return FuelResolution.none();
        }
        return snapshot.resolve(stack, setKey, true, new HashSet<AssetKey>());
    }

    /** Resolves only declared BetaMoon entries, used by the furnace return hook. */
    public static FuelResolution resolveRegistered(ItemStack stack, AssetKey setKey) {
        if (stack == null || stack.getItem() == null) {
            return FuelResolution.none();
        }
        return snapshot.resolve(stack, setKey, false, new HashSet<AssetKey>());
    }

    /** ID-only ModLoader fallback. Exact-damage declarations intentionally do not participate. */
    public static int getLegacyFurnaceBurnTime(int itemId) {
        SetSnapshot furnace = snapshot.compiledSets.get(FURNACE);
        if (furnace == null) {
            return 0;
        }
        FuelRegistration registration = furnace.wildcard.get(Integer.valueOf(itemId));
        return registration == null ? 0 : registration.burnTime;
    }

    public static List<FuelSetDefinition> sets() {
        return snapshot.orderedSets;
    }

    public static List<FuelRegistration> registrations() {
        return snapshot.orderedRegistrations;
    }

    private static void requireOwner(String owner) {
        if (owner == null || owner.trim().length() == 0) {
            throw new IllegalArgumentException("A fuel registration owner is required.");
        }
    }

    private static void requireFuelKey(AssetKey key) {
        if (key == null || !key.getPath().startsWith("fuel/") || key.getPath().length() <= "fuel/".length()) {
            throw new IllegalArgumentException("Fuel-set keys must use namespace:fuel/name.");
        }
    }

    private static boolean equals(Integer left, Integer right) {
        return left == null ? right == null : left.equals(right);
    }

    private static void ensureAcyclic(Map<AssetKey, FuelSetDefinition> sets, AssetKey key, Set<AssetKey> visiting,
            Set<AssetKey> visited) {
        if (visited.contains(key)) {
            return;
        }
        if (!visiting.add(key)) {
            throw new IllegalArgumentException("Fuel-set inclusion cycle contains " + key + ".");
        }
        FuelSetDefinition definition = sets.get(key);
        if (definition != null) {
            for (AssetKey included : definition.includes) {
                ensureAcyclic(sets, included, visiting, visited);
            }
        }
        visiting.remove(key);
        visited.add(key);
    }

    private static void publish() {
        snapshot = new Snapshot(SETS, REGISTRATIONS);
    }

    private static final class Snapshot {
        private final Map<AssetKey, FuelSetDefinition> sets;
        private final Map<AssetKey, SetSnapshot> compiledSets;
        private final Map<Long, FuelRegistration> registrationsById;
        private final List<FuelSetDefinition> orderedSets;
        private final List<FuelRegistration> orderedRegistrations;

        private Snapshot(Map<AssetKey, FuelSetDefinition> sourceSets,
                Map<Long, FuelRegistration> sourceRegistrations) {
            sets = Collections.unmodifiableMap(new LinkedHashMap<AssetKey, FuelSetDefinition>(sourceSets));
            registrationsById = Collections
                    .unmodifiableMap(new LinkedHashMap<Long, FuelRegistration>(sourceRegistrations));
            orderedSets = Collections.unmodifiableList(new ArrayList<FuelSetDefinition>(sourceSets.values()));
            orderedRegistrations = Collections
                    .unmodifiableList(new ArrayList<FuelRegistration>(sourceRegistrations.values()));

            Map<AssetKey, SetSnapshot> compiled = new LinkedHashMap<AssetKey, SetSnapshot>();
            for (FuelSetDefinition set : sourceSets.values()) {
                compiled.put(set.key, new SetSnapshot(set));
            }
            for (FuelRegistration registration : sourceRegistrations.values()) {
                SetSnapshot set = compiled.get(registration.setKey);
                if (set != null) {
                    set.add(registration);
                }
            }
            compiledSets = Collections.unmodifiableMap(compiled);
        }

        private FuelResolution resolve(ItemStack stack, AssetKey key, boolean nativeFallback, Set<AssetKey> visiting) {
            SetSnapshot set = compiledSets.get(key);
            if (set == null || !visiting.add(key)) {
                return FuelResolution.none();
            }
            try {
                FuelRegistration exact = set.exact.get(Long.valueOf(exactKey(stack.itemID, stack.getItemDamage())));
                if (exact != null) {
                    return FuelResolution.registered(exact);
                }
                FuelRegistration wildcard = set.wildcard.get(Integer.valueOf(stack.itemID));
                if (wildcard != null) {
                    return FuelResolution.registered(wildcard);
                }
                for (AssetKey included : set.definition.includes) {
                    FuelResolution resolution = resolve(stack, included, nativeFallback, visiting);
                    if (resolution.isFuel()) {
                        return resolution;
                    }
                }
                return nativeFallback && FURNACE.equals(key)
                        ? FuelResolution.nativeFuel(NativeFuelRules.getBurnTime(stack))
                        : FuelResolution.none();
            } finally {
                visiting.remove(key);
            }
        }
    }

    private static final class SetSnapshot {
        private final FuelSetDefinition definition;
        private final Map<Long, FuelRegistration> exact = new HashMap<Long, FuelRegistration>();
        private final Map<Integer, FuelRegistration> wildcard = new HashMap<Integer, FuelRegistration>();

        private SetSnapshot(FuelSetDefinition definition) {
            this.definition = definition;
        }

        private void add(FuelRegistration registration) {
            if (registration.damage == null) {
                wildcard.put(Integer.valueOf(registration.itemId), registration);
            } else {
                exact.put(Long.valueOf(exactKey(registration.itemId, registration.damage.intValue())), registration);
            }
        }
    }

    private static long exactKey(int itemId, int damage) {
        return ((long) itemId << 32) ^ (damage & 0xffffffffL);
    }
}

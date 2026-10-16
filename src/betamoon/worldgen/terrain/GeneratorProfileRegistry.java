package betamoon.worldgen.terrain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Publishes owner replacements atomically; active worlds retain their resolved
 * profile object.
 */
public final class GeneratorProfileRegistry {
    private static volatile Snapshot active = new Snapshot(Collections.<WorldGeneratorProfile>emptyList());

    private GeneratorProfileRegistry() {
    }

    public static synchronized void replaceOwner(String owner, Collection<WorldGeneratorProfile> declarations) {
        active = replacement(owner, declarations);
    }

    public static synchronized void validateOwner(String owner, Collection<WorldGeneratorProfile> declarations) {
        replacement(owner, declarations);
    }

    public static synchronized void replaceOwners(Set<String> owners, Collection<WorldGeneratorProfile> declarations) {
        active = replacement(owners, declarations);
    }

    public static synchronized void validateOwners(Set<String> owners, Collection<WorldGeneratorProfile> declarations) {
        replacement(owners, declarations);
    }

    private static Snapshot replacement(Set<String> owners, Collection<WorldGeneratorProfile> declarations) {
        ArrayList<WorldGeneratorProfile> replacement = new ArrayList<WorldGeneratorProfile>();
        for (WorldGeneratorProfile profile : active.profiles.values()) {
            if (!owners.contains(profile.owner)) {
                replacement.add(profile);
            }
        }
        for (WorldGeneratorProfile profile : declarations) {
            if (!owners.contains(profile.owner)) {
                throw new IllegalArgumentException("Generator profile belongs to another owner: " + profile.key);
            }
            replacement.add(profile);
        }
        return new Snapshot(replacement);
    }

    private static Snapshot replacement(String owner, Collection<WorldGeneratorProfile> declarations) {
        ArrayList<WorldGeneratorProfile> replacement = new ArrayList<WorldGeneratorProfile>();
        for (WorldGeneratorProfile profile : active.profiles.values()) {
            if (!profile.owner.equals(owner)) {
                replacement.add(profile);
            }
        }
        for (WorldGeneratorProfile profile : declarations) {
            if (!profile.owner.equals(owner)) {
                throw new IllegalArgumentException("Generator profile belongs to another owner: " + profile.key);
            }
            replacement.add(profile);
        }
        return new Snapshot(replacement);
    }

    public static Snapshot snapshot() {
        return active;
    }

    public static final class Snapshot {
        private final Map<String, WorldGeneratorProfile> profiles;
        private final WorldGeneratorProfile defaultProfile;

        private Snapshot(Collection<WorldGeneratorProfile> declarations) {
            TreeMap<String, WorldGeneratorProfile> resolved = new TreeMap<String, WorldGeneratorProfile>();
            WorldGeneratorProfile selected = null;
            for (WorldGeneratorProfile profile : declarations) {
                if (resolved.put(profile.key, profile) != null) {
                    throw new IllegalArgumentException("Duplicate generator profile: " + profile.key);
                }
                if (profile.defaultForNewWorlds && (selected == null || profile.priority > selected.priority)) {
                    selected = profile;
                }
            }
            if (selected != null) {
                for (WorldGeneratorProfile profile : resolved.values()) {
                    if (profile != selected && profile.defaultForNewWorlds && profile.priority == selected.priority) {
                        throw new IllegalArgumentException(
                                "Ambiguous default generator profiles: " + selected.key + " and " + profile.key);
                    }
                }
            }
            profiles = Collections.unmodifiableMap(resolved);
            defaultProfile = selected;
        }

        public WorldGeneratorProfile get(String key) {
            return profiles.get(key);
        }

        public WorldGeneratorProfile defaultForNewWorld() {
            return defaultProfile;
        }
    }
}

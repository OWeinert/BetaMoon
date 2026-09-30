package betamoon.worldgen.structure;

import betamoon.worldgen.BlockSet;
import betamoon.worldgen.FeatureContext;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Bounded world-cell replacement and fluid permissions shared by terrain
 * mutations.
 */
public final class TerrainReplacementPolicy {
    public enum Replace {
        ORDINARY_TERRAIN, TERRAIN_AND_VEGETATION, REPLACEABLE;

        public static Replace parse(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("unknown terrain replacement policy: " + value);
            }
        }
    }

    public enum Fluid {
        REJECT, REPLACE, DRAIN, PRESERVE;

        public static Fluid parse(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("unknown terrain fluid policy: " + value);
            }
        }
    }

    /**
     * Immutable replacement selector compiled while a structure definition is
     * loaded. Tags are BetaMoon terrain classifier tags, not runtime callbacks.
     */
    public static final class Selector {
        public final Replace preset;
        public final BlockSet blocks;
        public final BlockSet excludeBlocks;
        public final Set<String> tags;
        public final Set<String> excludeTags;
        public final boolean matchAll;

        public Selector(Replace preset, BlockSet blocks, BlockSet excludeBlocks, Set<String> tags,
                Set<String> excludeTags, boolean matchAll) {
            this.preset = preset;
            this.blocks = blocks == null ? new BlockSet() : blocks;
            this.excludeBlocks = excludeBlocks == null ? new BlockSet() : excludeBlocks;
            this.tags = immutable(tags);
            this.excludeTags = immutable(excludeTags);
            this.matchAll = matchAll;
            if (preset == null && this.blocks.values().length == 0 && this.tags.isEmpty()) {
                throw new IllegalArgumentException("replacement selector requires blocks or tags");
            }
        }

        public static Selector preset(Replace preset) {
            return new Selector(preset, null, null, null, null, false);
        }

        boolean permits(FeatureContext context, int blockId) {
            if (excludeBlocks.contains(blockId) || matchesAnyTag(context, blockId, excludeTags)) {
                return false;
            }
            if (preset != null && matchesPreset(context, blockId, preset)) {
                return true;
            }
            boolean blockMatches = blocks.contains(blockId);
            if (tags.isEmpty()) {
                return blockMatches;
            }
            boolean tagMatches = matchAll
                    ? matchesAllTags(context, blockId, tags)
                    : matchesAnyTag(context, blockId, tags);
            return matchAll && blocks.values().length > 0 ? blockMatches && tagMatches : blockMatches || tagMatches;
        }

        String signature() {
            return String.valueOf(preset) + ':' + Arrays.toString(blocks.values()) + ':'
                    + Arrays.toString(excludeBlocks.values()) + ':' + new TreeSet<String>(tags) + ':'
                    + new TreeSet<String>(excludeTags) + ':' + matchAll;
        }

        private static Set<String> immutable(Set<String> values) {
            return values == null
                    ? Collections.<String>emptySet()
                    : Collections.unmodifiableSet(new LinkedHashSet<String>(values));
        }
    }

    public static boolean validTag(String tag) {
        String normalized = normalizeTag(tag);
        return normalized.equals("ordinary_terrain") || normalized.equals("replaceable")
                || normalized.equals("terrain_and_vegetation") || normalized.equals("solid_terrain_material");
    }

    private static boolean matchesAllTags(FeatureContext context, int blockId, Set<String> tags) {
        for (String tag : tags) {
            if (!matchesTag(context, blockId, tag)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesAnyTag(FeatureContext context, int blockId, Set<String> tags) {
        for (String tag : tags) {
            if (matchesTag(context, blockId, tag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesTag(FeatureContext context, int blockId, String tag) {
        String normalized = normalizeTag(tag);
        if (normalized.equals("ordinary_terrain")) {
            return context.isOrdinaryTerrain(blockId);
        }
        if (normalized.equals("replaceable")) {
            return context.isReplaceable(blockId);
        }
        if (normalized.equals("terrain_and_vegetation")) {
            return context.isOrdinaryTerrain(blockId) || context.isReplaceable(blockId);
        }
        return normalized.equals("solid_terrain_material") && context.isTerrainMaterial(blockId);
    }

    private static String normalizeTag(String value) {
        String result = value.trim().toLowerCase(Locale.ROOT);
        return result.startsWith("#") ? result.substring(1) : result;
    }

    private static boolean matchesPreset(FeatureContext context, int blockId, Replace replace) {
        if (replace == Replace.REPLACEABLE) {
            return context.isReplaceable(blockId);
        }
        if (replace == Replace.TERRAIN_AND_VEGETATION) {
            return context.isOrdinaryTerrain(blockId) || context.isReplaceable(blockId);
        }
        return context.isOrdinaryTerrain(blockId);
    }

    private TerrainReplacementPolicy() {
    }
}

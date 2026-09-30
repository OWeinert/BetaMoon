package betamoon.worldgen;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Result of planning or committing one bounded feature. */
public final class FeatureResult {
    public static final String BLOCKED = "blocked";
    public static final String OUT_OF_BOUNDS = "out_of_bounds";
    public static final String UNLOADED_CHUNK = "unloaded_chunk";
    public static final String INVALID_GROUND = "invalid_ground";
    public static final String PROTECTED = "protected";
    public static final String BUDGET_EXCEEDED = "budget_exceeded";
    public static final String NO_CHANGES = "no_changes";
    public static final String RUNTIME_ERROR = "runtime_error";
    public static final String TERRAIN_UNREADABLE = "terrain_unreadable";
    public static final String TERRAIN_SLOPE = "terrain_slope";
    public static final String TERRAIN_STEP = "terrain_step";
    public static final String TERRAIN_SUPPORT = "terrain_support";
    public static final String FOUNDATION_TOO_DEEP = "foundation_too_deep";
    public static final String TERRACE_CUT_LIMIT = "terrace_cut_limit";
    public static final String TERRACE_FILL_LIMIT = "terrace_fill_limit";
    public static final String TERRAIN_PROTECTED_BLOCK = "terrain_protected_block";
    public static final String TERRAIN_TILE_COLLISION = "terrain_tile_collision";
    public static final String SITE_WRONG_SURFACE_RELATION = "site_wrong_surface_relation";
    public static final String SITE_WRONG_MEDIUM = "site_wrong_medium";
    public static final String SITE_FLUID_COVERAGE = "site_fluid_coverage";
    public static final String SITE_FLUID_DEPTH = "site_fluid_depth";
    public static final String SITE_DEPTH = "site_depth";
    public static final String SITE_INSUFFICIENT_COVER = "site_insufficient_cover";
    public static final String SITE_INSUFFICIENT_CAVITY = "site_insufficient_cavity";
    public static final String SITE_SKY_MISMATCH = "site_sky_mismatch";
    public static final String CONFORM_PATH_FAILED = "conform_path_failed";
    public static final String LOOT_TARGET_MISSING = "loot_target_missing";
    public static final String LOOT_INVENTORY_SIZE = "loot_inventory_size";
    public static final String LOOT_EXISTING_CONTENTS = "loot_existing_contents";
    public static final String LOOT_OVERFLOW = "loot_overflow";
    public static final String LOOT_MUTATION_FAILED = "loot_mutation_failed";

    public final boolean placed;
    public final String reason;
    public final int blocksChanged;
    public final BlockPosition min;
    public final BlockPosition max;
    public final Map<String, Object> details;

    private FeatureResult(boolean placed, String reason, int blocksChanged, BlockPosition min, BlockPosition max,
            Map<String, Object> details) {
        this.placed = placed;
        this.reason = reason;
        this.blocksChanged = blocksChanged;
        this.min = min;
        this.max = max;
        this.details = details == null ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, Object>(details));
    }

    public static FeatureResult rejected(String reason) {
        return rejected(reason, Collections.<String, Object>emptyMap());
    }

    public static FeatureResult rejected(String reason, Map<String, Object> details) {
        return new FeatureResult(false, reason, 0, null, null, details);
    }

    public static FeatureResult placed(int blocksChanged, BlockPosition min, BlockPosition max) {
        return placed(blocksChanged, min, max, Collections.<String, Object>emptyMap());
    }

    public static FeatureResult placed(int blocksChanged, BlockPosition min, BlockPosition max,
            Map<String, Object> details) {
        return new FeatureResult(true, null, blocksChanged, min, max, details);
    }
}

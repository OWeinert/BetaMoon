package betamoon.worldgen.creation;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** One creation-time choice. Parent choices always take precedence. */
public final class GenerationOption {
    public final String key;
    public final String label;
    public final String category;
    public final boolean baseline;
    public final String lockedReason;
    public final List<String> parents;

    public GenerationOption(String key, String label, String category, boolean baseline, String lockedReason,
            String... parents) {
        this.key = key;
        this.label = label;
        this.category = category;
        this.baseline = baseline;
        this.lockedReason = lockedReason;
        this.parents = Collections.unmodifiableList(Arrays.asList(parents.clone()));
    }
}

package betamoon.multiblock;

import java.util.Set;

/**
 * Immutable predicates run entirely in Java. A readable cell is required even
 * for negation.
 */
public interface MultiblockRule {
    boolean matches(int blockId, int metadata, int facing, boolean mirror);

    String describe();

    default String signature(int facing, boolean mirror) {
        return describe();
    }

    /**
     * A finite positive seed superset, or null when discovery cannot index the
     * rule.
     */
    default Set<Integer> candidateBlocks() {
        return null;
    }
}

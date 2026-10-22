package betamoon.entity.modifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resolved contribution and immutable provenance for one rule, independent of
 * native baseline values.
 */
public final class EntityResolvedRule {
    private final Object value;
    private final boolean capped;
    private final EntityRuleSource winner;
    private final List<EntityRuleSource> sources;

    EntityResolvedRule(Object value, boolean capped, EntityRuleSource winner, List<EntityRuleSource> sources) {
        this.value = value;
        this.capped = capped;
        this.winner = winner;
        this.sources = Collections.unmodifiableList(new ArrayList<>(sources));
    }

    public Object value() {
        return value;
    }

    public boolean isCapped() {
        return capped;
    }

    /**
     * Absolute overrides have a winner; products and veto combinations retain all
     * contributors.
     */
    public EntityRuleSource winner() {
        return winner;
    }

    public List<EntityRuleSource> sources() {
        return sources;
    }
}

package betamoon.entity.modifier;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * A reusable immutable resolution; native consumers supply their current
 * baseline on each use.
 */
public final class EntityResolvedRules {
    static final EntityResolvedRules EMPTY = new EntityResolvedRules(
            new EnumMap<EntityMovementRule, EntityResolvedRule>(EntityMovementRule.class));

    private final Map<EntityMovementRule, EntityResolvedRule> rules;

    EntityResolvedRules(Map<EntityMovementRule, EntityResolvedRule> rules) {
        EnumMap<EntityMovementRule, EntityResolvedRule> copy = new EnumMap<>(EntityMovementRule.class);
        copy.putAll(rules);
        this.rules = Collections.unmodifiableMap(copy);
    }

    public EntityResolvedRule get(EntityMovementRule rule) {
        requireRule(rule);
        return rules.get(rule);
    }

    public double numberValue(EntityMovementRule rule, double nativeValue) {
        requireRule(rule);
        if (!rule.isNumeric()) {
            throw new IllegalArgumentException(rule.path() + ": expected a numeric rule");
        }
        EntityResolvedRule result = rules.get(rule);
        if (result == null) {
            return nativeValue;
        }
        double contribution = ((Double) result.value()).doubleValue();
        if (rule.combination() == EntityMovementRule.Combination.MULTIPLIER) {
            return contribution == 1 ? nativeValue : nativeValue * contribution;
        }
        return contribution;
    }

    public boolean booleanValue(EntityMovementRule rule, boolean nativeValue) {
        requireRule(rule);
        if (rule.isNumeric()) {
            throw new IllegalArgumentException(rule.path() + ": expected a boolean rule");
        }
        EntityResolvedRule result = rules.get(rule);
        if (result == null) {
            return nativeValue;
        }
        boolean contribution = ((Boolean) result.value()).booleanValue();
        return rule.combination() == EntityMovementRule.Combination.BOOLEAN_VETO
                ? nativeValue && contribution
                : contribution;
    }

    public Map<EntityMovementRule, EntityResolvedRule> asMap() {
        return rules;
    }

    private static void requireRule(EntityMovementRule rule) {
        if (rule == null) {
            throw new IllegalArgumentException("Entity movement rule is required");
        }
    }
}

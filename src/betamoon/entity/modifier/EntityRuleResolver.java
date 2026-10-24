package betamoon.entity.modifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;

/** Deterministic bounded composition of already-eligible source snapshots. */
public final class EntityRuleResolver {
    public static final int MAX_SOURCES = 64;

    private static final Comparator<EntityRuleSource> SOURCE_ORDER = new Comparator<EntityRuleSource>() {
        public int compare(EntityRuleSource left, EntityRuleSource right) {
            int ownerOrder = left.owner().compareTo(right.owner());
            return ownerOrder != 0 ? ownerOrder : left.key().toString().compareTo(right.key().toString());
        }
    };

    private EntityRuleResolver() {
    }

    public static EntityResolvedRules resolve(Collection<EntityRuleSource> sources) {
        List<EntityRuleSource> ordered = orderedSources(sources);
        if (ordered.isEmpty()) {
            return EntityResolvedRules.EMPTY;
        }
        EnumMap<EntityMovementRule, EntityResolvedRule> resolved = new EnumMap<>(EntityMovementRule.class);
        for (EntityMovementRule rule : EntityMovementRule.values()) {
            List<EntityRuleSource> contributors = new ArrayList<>();
            for (EntityRuleSource source : ordered) {
                if (source.rules().get(rule) != null) {
                    contributors.add(source);
                }
            }
            if (!contributors.isEmpty()) {
                resolved.put(rule, resolveRule(rule, contributors));
            }
        }
        return new EntityResolvedRules(resolved);
    }

    private static List<EntityRuleSource> orderedSources(Collection<EntityRuleSource> sources) {
        if (sources == null) {
            throw new IllegalArgumentException("Entity rule sources are required");
        }
        List<EntityRuleSource> ordered = new ArrayList<>();
        for (EntityRuleSource source : sources) {
            if (source == null) {
                throw new IllegalArgumentException("Entity rule source cannot be null");
            }
            if (ordered.size() == MAX_SOURCES) {
                throw new IllegalArgumentException("Entity rule source limit reached: " + MAX_SOURCES);
            }
            ordered.add(source);
        }
        Collections.sort(ordered, SOURCE_ORDER);
        for (int i = 1; i < ordered.size(); i++) {
            if (SOURCE_ORDER.compare(ordered.get(i - 1), ordered.get(i)) == 0) {
                throw new IllegalArgumentException(
                        "Duplicate entity rule source: " + ordered.get(i).owner() + " / " + ordered.get(i).key());
            }
        }
        return ordered;
    }

    private static EntityResolvedRule resolveRule(EntityMovementRule rule, List<EntityRuleSource> sources) {
        if (rule.combination() == EntityMovementRule.Combination.MULTIPLIER) {
            double product = product(rule, sources);
            boolean capped = product > rule.maximum();
            return new EntityResolvedRule(Double.valueOf(capped ? rule.maximum() : product), capped, null, sources);
        }
        if (rule.combination() == EntityMovementRule.Combination.BOOLEAN_VETO) {
            boolean enabled = true;
            for (EntityRuleSource source : sources) {
                enabled &= ((Boolean) source.rules().get(rule)).booleanValue();
            }
            return new EntityResolvedRule(Boolean.valueOf(enabled), false, null, sources);
        }

        EntityRuleSource winner = sources.get(0);
        for (EntityRuleSource source : sources) {
            if (source.priority() > winner.priority()) {
                winner = source;
            }
        }
        return new EntityResolvedRule(winner.rules().get(rule), false, winner, sources);
    }

    /**
     * Keep the significand normalized so only the final product can overflow or
     * underflow.
     */
    private static double product(EntityMovementRule rule, List<EntityRuleSource> sources) {
        double significand = 1;
        int exponent = 0;
        for (EntityRuleSource source : sources) {
            double factor = ((Double) source.rules().get(rule)).doubleValue();
            if (factor == 0) {
                return 0;
            }
            int factorExponent = Math.getExponent(factor);
            double fraction = Math.scalb(factor, -factorExponent);
            if (fraction < 1) {
                int adjustment = Math.getExponent(fraction);
                fraction = Math.scalb(fraction, -adjustment);
                factorExponent += adjustment;
            }
            significand *= fraction;
            exponent += factorExponent;
            if (significand >= 2) {
                significand *= 0.5;
                exponent++;
            }
        }
        return Math.scalb(significand, exponent);
    }
}

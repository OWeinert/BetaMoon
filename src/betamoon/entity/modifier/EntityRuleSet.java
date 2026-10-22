package betamoon.entity.modifier;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Validated immutable contributions; an absent leaf contributes no override or
 * factor.
 */
public final class EntityRuleSet {
    public static final EntityRuleSet EMPTY = new EntityRuleSet(
            new EnumMap<EntityMovementRule, Object>(EntityMovementRule.class));

    private final Map<EntityMovementRule, Object> values;

    private EntityRuleSet(EnumMap<EntityMovementRule, Object> values) {
        this.values = Collections.unmodifiableMap(values);
    }

    public static EntityRuleSet copyOf(Map<EntityMovementRule, ?> values) {
        if (values == null) {
            throw new IllegalArgumentException("Entity rule values are required");
        }
        EnumMap<EntityMovementRule, Object> checked = new EnumMap<>(EntityMovementRule.class);
        for (Map.Entry<EntityMovementRule, ?> entry : values.entrySet()) {
            EntityMovementRule rule = requireRule(entry.getKey());
            checked.put(rule, rule.validate(entry.getValue()));
        }
        return checked.isEmpty() ? EMPTY : new EntityRuleSet(checked);
    }

    public EntityRuleSet with(EntityMovementRule rule, Object value) {
        requireRule(rule);
        Object checked = rule.validate(value);
        EnumMap<EntityMovementRule, Object> changed = copyValues();
        changed.put(rule, checked);
        return new EntityRuleSet(changed);
    }

    public EntityRuleSet without(EntityMovementRule rule) {
        requireRule(rule);
        if (!values.containsKey(rule)) {
            return this;
        }
        EnumMap<EntityMovementRule, Object> changed = copyValues();
        changed.remove(rule);
        return changed.isEmpty() ? EMPTY : new EntityRuleSet(changed);
    }

    public EntityRuleSet merge(EntityRuleSet patch) {
        if (patch == null) {
            throw new IllegalArgumentException("Entity rule patch is required");
        }
        if (patch.isEmpty()) {
            return this;
        }
        EnumMap<EntityMovementRule, Object> changed = copyValues();
        changed.putAll(patch.values);
        return new EntityRuleSet(changed);
    }

    public Object get(EntityMovementRule rule) {
        return values.get(requireRule(rule));
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public Map<EntityMovementRule, Object> asMap() {
        return values;
    }

    private EnumMap<EntityMovementRule, Object> copyValues() {
        EnumMap<EntityMovementRule, Object> changed = new EnumMap<>(EntityMovementRule.class);
        changed.putAll(values);
        return changed;
    }

    private static EntityMovementRule requireRule(EntityMovementRule rule) {
        if (rule == null) {
            throw new IllegalArgumentException("Entity movement rule is required");
        }
        return rule;
    }
}

package betamoon.entity.modifier;

import betamoon.content.ContentKey;

/**
 * Immutable declaration revision; a prepared native step retains exactly one
 * revision.
 */
public final class EntityModifierDefinition {
    public final ContentKey key;
    public final int priority;
    public final boolean enabled;
    public final EntityRuleSet rules;
    public final EntityModifierCallbacks callbacks;

    public EntityModifierDefinition(ContentKey key, int priority, boolean enabled, EntityRuleSet rules,
            EntityModifierCallbacks callbacks) {
        new EntityRuleSource("definition", key, priority, rules);
        if (callbacks == null) {
            throw new IllegalArgumentException("Modifier callbacks are required");
        }
        this.key = key;
        this.priority = priority;
        this.enabled = enabled;
        this.rules = rules;
        this.callbacks = callbacks;
    }

    public EntityModifierDefinition withEnabled(boolean enabled) {
        return new EntityModifierDefinition(key, priority, enabled, rules, callbacks);
    }
}

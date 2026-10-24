package betamoon.entity.modifier;

import betamoon.content.ContentKey;
import betamoon.content.ContentType;

/**
 * Immutable rule snapshot from one eligible source; binding lifetime is owned
 * by the runtime.
 */
public final class EntityRuleSource {
    public static final ContentType KEY_TYPE = ContentType.of("modifier");
    public static final int MIN_PRIORITY = -10000;
    public static final int MAX_PRIORITY = 10000;

    private final String owner;
    private final ContentKey key;
    private final int priority;
    private final EntityRuleSet rules;

    public EntityRuleSource(String owner, ContentKey key, int priority, EntityRuleSet rules) {
        if (owner == null || owner.trim().isEmpty()) {
            throw new IllegalArgumentException("Entity rule source owner is required");
        }
        if (key == null || !KEY_TYPE.equals(key.type())) {
            throw new IllegalArgumentException("Entity rule source requires a modifier content key");
        }
        if (priority < MIN_PRIORITY || priority > MAX_PRIORITY) {
            throw new IllegalArgumentException("Entity rule source priority must be within -10000..10000");
        }
        if (rules == null) {
            throw new IllegalArgumentException("Entity rule source rules are required");
        }
        this.owner = owner;
        this.key = key;
        this.priority = priority;
        this.rules = rules;
    }

    public String owner() {
        return owner;
    }

    public ContentKey key() {
        return key;
    }

    public int priority() {
        return priority;
    }

    public EntityRuleSet rules() {
        return rules;
    }
}

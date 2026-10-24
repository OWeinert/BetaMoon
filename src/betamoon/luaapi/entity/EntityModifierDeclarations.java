package betamoon.luaapi.entity;

import betamoon.content.ContentKey;
import betamoon.entity.modifier.EntityModifierCallbacks;
import betamoon.entity.modifier.EntityModifierDefinition;
import betamoon.entity.modifier.EntityMovementRule;
import betamoon.entity.modifier.EntityRuleSet;
import betamoon.entity.modifier.EntityRuleSource;
import betamoon.luaapi.utils.LuaDeclarationValues;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Full declaration/patch validation without changing a live source. */
public final class EntityModifierDeclarations {
    private EntityModifierDeclarations() {
    }

    public static EntityModifierDefinition parse(LuaValue table, String owner) {
        fields(table);
        ContentKey key = key(table.get("key"));
        EntityRuleSet rules = EntityRuleDeclarations.parse(ruleGroups(table), "modifier");
        return new EntityModifierDefinition(key, priority(table, 0), enabled(table, true), rules,
                LuaModifierCallbacks.read(table, owner, EntityModifierCallbacks.EMPTY));
    }

    public static EntityModifierDefinition patch(EntityModifierDefinition current, LuaValue patch, String owner) {
        fields(patch);
        if (!patch.get("key").isnil() && !key(patch.get("key")).equals(current.key)) {
            throw LuaDeclarationValues.error("modifier.key", "keys cannot be changed");
        }
        return new EntityModifierDefinition(current.key, priority(patch, current.priority),
                enabled(patch, current.enabled),
                current.rules.merge(EntityRuleDeclarations.parse(ruleGroups(patch), "modifier")),
                LuaModifierCallbacks.read(patch, owner, current.callbacks));
    }

    public static EntityModifierDefinition clear(EntityModifierDefinition current, String path) {
        EntityMovementRule rule = EntityMovementRule.find(path);
        EntityRuleSet rules = current.rules;
        EntityModifierCallbacks callbacks = current.callbacks;
        if (rule != null) {
            rules = rules.without(rule);
        } else {
            callbacks = LuaModifierCallbacks.clear(callbacks, path);
        }
        return new EntityModifierDefinition(current.key, current.priority, current.enabled, rules, callbacks);
    }

    private static void fields(LuaValue table) {
        LuaDeclarationValues.fields(table, "modifier", "key", "priority", "enabled", "physics", "movement", "damage",
                "onAttach", "onDetach", "onMotion", "onForces", "onAfterMotion");
    }

    private static LuaTable ruleGroups(LuaValue table) {
        LuaTable groups = new LuaTable();
        groups.set("physics", table.get("physics"));
        groups.set("movement", table.get("movement"));
        groups.set("damage", table.get("damage"));
        return groups;
    }

    private static ContentKey key(LuaValue value) {
        try {
            return ContentKey.parseForType(LuaDeclarationValues.string(value, "modifier.key"),
                    EntityRuleSource.KEY_TYPE);
        } catch (IllegalArgumentException error) {
            throw LuaDeclarationValues.error("modifier.key", error.getMessage());
        }
    }

    private static int priority(LuaValue table, int fallback) {
        LuaValue value = table.get("priority");
        return value.isnil() ? fallback : LuaDeclarationValues.integer(value, "modifier.priority", -10000, 10000);
    }

    private static boolean enabled(LuaValue table, boolean fallback) {
        return LuaDeclarationValues.bool(table.get("enabled"), "modifier.enabled", fallback);
    }
}

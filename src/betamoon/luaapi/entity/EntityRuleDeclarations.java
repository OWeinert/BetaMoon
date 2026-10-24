package betamoon.luaapi.entity;

import betamoon.entity.modifier.EntityMovementRule;
import betamoon.entity.modifier.EntityRuleSet;
import betamoon.luaapi.utils.LuaDeclarationValues;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Set;
import org.luaj.vm2.LuaValue;

/**
 * Strict rule-group parsing, separate from modifier lifetime and native
 * movement.
 */
public final class EntityRuleDeclarations {
    private EntityRuleDeclarations() {
    }

    public static EntityRuleSet parse(LuaValue value, String path) {
        if (value == null) {
            throw new IllegalArgumentException("Entity rule declaration is required");
        }
        if (value.isnil()) {
            return EntityRuleSet.EMPTY;
        }
        EnumMap<EntityMovementRule, Object> values = new EnumMap<>(EntityMovementRule.class);
        readGroup(value, path, "", values);
        return EntityRuleSet.copyOf(values);
    }

    public static EntityRuleSet patch(EntityRuleSet current, LuaValue value, String path) {
        if (current == null) {
            throw new IllegalArgumentException("Current entity rules are required");
        }
        return current.merge(parse(value, path));
    }

    private static void readGroup(LuaValue table, String path, String prefix,
            EnumMap<EntityMovementRule, Object> values) {
        Set<String> fields = new LinkedHashSet<>();
        for (EntityMovementRule rule : EntityMovementRule.values()) {
            if (rule.path().startsWith(prefix)) {
                String remaining = rule.path().substring(prefix.length());
                int separator = remaining.indexOf('.');
                fields.add(separator < 0 ? remaining : remaining.substring(0, separator));
            }
        }
        LuaDeclarationValues.fields(table, path, fields.toArray(new String[fields.size()]));
        for (String field : fields) {
            LuaValue value = table.get(field);
            if (value.isnil()) {
                continue;
            }
            String fieldPath = path + "." + field;
            String rulePath = prefix + field;
            EntityMovementRule rule = EntityMovementRule.find(rulePath);
            if (rule == null) {
                readGroup(value, fieldPath, rulePath + ".", values);
            } else {
                Object contribution = rule.isNumeric()
                        ? Double.valueOf(LuaDeclarationValues.number(value, fieldPath))
                        : Boolean.valueOf(LuaDeclarationValues.bool(value, fieldPath, false));
                try {
                    values.put(rule, rule.validate(contribution));
                } catch (IllegalArgumentException error) {
                    throw LuaDeclarationValues.error(fieldPath, "expected a finite number in 0.." + rule.maximum());
                }
            }
        }
    }
}

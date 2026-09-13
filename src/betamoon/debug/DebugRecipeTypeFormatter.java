package betamoon.debug;

import betamoon.recipes.custom.RecipeTypes;
import java.util.Map;
import org.luaj.vm2.LuaValue;

/**
 * Formats recipe type schemas separately from recipe instances.
 */
final class DebugRecipeTypeFormatter {
    private DebugRecipeTypeFormatter() {
    }

    static String format(RecipeTypes.Type type) {
        LuaValue definition = type.getDefinition();
        StringBuilder out = new StringBuilder();
        out.append(type.name).append(" : ")
                .append(DebugValueFormatter.quote(definition.get("displayName").tojstring()));
        out.append("\n    owner = ").append(DebugValueFormatter.quote(type.owner));
        if (type.matcherName != null) {
            out.append("\n    matcher = ").append(DebugValueFormatter.quote(type.matcherName));
        }
        appendInputRoles(out, type);
        appendOutputRoles(out, type);
        appendFields(out, "data", type.data, true);
        appendFields(out, "context", type.context, false);
        return out.toString();
    }

    private static void appendInputRoles(StringBuilder out, RecipeTypes.Type type) {
        out.append(type.builtin ? "\n    inputs:" : "\n    ingredients:");
        if ("minecraft:shaped".equals(type.name)) {
            appendRole(out, type, type.ingredients.get("pattern"), false);
            appendRole(out, type, type.ingredients.get("ingredients"), false);
            return;
        }
        for (RecipeTypes.Role role : type.ingredients.values()) {
            appendRole(out, type, role, !type.builtin);
        }
    }

    private static void appendRole(StringBuilder out, RecipeTypes.Type type, RecipeTypes.Role role,
            boolean includeConsumption) {
        out.append("\n        ").append(DebugValueFormatter.quote(role.name)).append(": ")
                .append(formatRoleValue(role.type)).append(" [").append(role.optional ? "optional" : "required");
        if (includeConsumption) {
            out.append(", ").append(role.consume ? "consumed" : "retained");
        }
        appendRoleOptions(out, role);
        appendNativeInputConstraint(out, type, role);
        out.append(']');
    }

    private static String formatRoleValue(RecipeTypes.RoleValueType type) {
        switch (type) {
            case ITEM_LIST:
                return "{ item, ... }";
            case STRING_LIST:
                return "{ \"row\", ... }";
            case CHARACTER_ITEM_MAP:
                return "{ [\"X\"] = item, ... }";
            case ITEM_POOL:
            case ITEM_OUTPUT_POOL:
                return "{ item, ... }";
            case ITEM_GRID:
                return "{ pattern = { \"row\", ... }, key = { [\"X\"] = item, ... } }";
            case ITEM:
            default:
                return "item";
        }
    }

    private static void appendRoleOptions(StringBuilder out, RecipeTypes.Role role) {
        if (role.pool != null) {
            out.append(", allow extra = ").append(role.pool.allowExtra);
        }
        if (role.grid != null) {
            out.append(", grid = ").append(role.grid.width).append('x').append(role.grid.height);
            out.append(", placement = ").append(role.grid.placement);
            out.append(", empty cells = ").append(role.grid.emptyCells);
            if (role.grid.allowSmaller) {
                out.append(", smaller patterns allowed");
            }
            if (role.grid.transformations.length() > 0) {
                out.append(", transformations = ").append(DebugValueFormatter.format(role.grid.transformations));
            }
        }
    }

    private static void appendNativeInputConstraint(StringBuilder out, RecipeTypes.Type type, RecipeTypes.Role role) {
        if ("minecraft:shaped".equals(type.name)) {
            out.append("pattern".equals(role.name) ? ", 2x2 or 3x3 grid" : ", one entry per pattern character");
        } else if ("minecraft:shapeless".equals(type.name) && "ingredients".equals(role.name)) {
            out.append(", 1 to 9 entries");
        }
    }

    private static void appendOutputRoles(StringBuilder out, RecipeTypes.Type type) {
        out.append("\n    outputs:");
        for (RecipeTypes.Role role : type.outputs.values()) {
            out.append("\n        ").append(DebugValueFormatter.quote(role.name)).append(": ")
                    .append(formatRoleValue(role.type)).append(" [").append(role.optional ? "optional" : "required");
            if (role.name.equals(type.primary)) {
                out.append(", primary");
            }
            appendRoleOptions(out, role);
            out.append(']');
        }
    }

    private static void appendFields(StringBuilder out, String heading, Map<String, RecipeTypes.Field> fields,
            boolean includeRequirement) {
        if (fields.isEmpty()) {
            return;
        }
        out.append("\n    ").append(heading).append(":");
        for (Map.Entry<String, RecipeTypes.Field> entry : fields.entrySet()) {
            LuaValue schema = entry.getValue().getSchema();
            out.append("\n        ").append(entry.getKey()).append(": ").append(entry.getValue().type);
            appendFieldDetails(out, schema, includeRequirement);
        }
    }

    private static void appendFieldDetails(StringBuilder out, LuaValue schema, boolean includeRequirement) {
        StringBuilder details = new StringBuilder();
        if (includeRequirement) {
            LuaValue defaultValue = schema.get("default");
            appendDetail(details,
                    defaultValue.isnil() ? "required" : "default = " + DebugValueFormatter.format(defaultValue));
        }
        for (String property : new String[]{"min", "max", "values"}) {
            LuaValue value = schema.get(property);
            if (!value.isnil()) {
                appendDetail(details, property + " = " + DebugValueFormatter.format(value));
            }
        }
        if (details.length() > 0) {
            out.append(" [").append(details).append(']');
        }
    }

    private static void appendDetail(StringBuilder details, String detail) {
        if (details.length() > 0) {
            details.append(", ");
        }
        details.append(detail);
    }
}

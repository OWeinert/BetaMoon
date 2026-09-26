package betamoon.tileentity;

/**
 * Evaluates a prevalidated GUI visibility condition against current tile data.
 */
public final class GuiConditionEvaluator {
    private GuiConditionEvaluator() {
    }

    public static boolean evaluate(ContainerGuiDefinition.Condition condition, ValueProvider values) {
        return evaluate(condition, values, field -> null);
    }

    public static boolean evaluate(ContainerGuiDefinition.Condition condition, ValueProvider data,
            ValueProvider session) {
        if (condition == null) {
            return true;
        }
        if (condition.operator == GuiConditionOperator.ALL) {
            for (int i = 0; i < condition.children.size(); i++) {
                if (!evaluate(condition.children.get(i), data, session)) {
                    return false;
                }
            }
            return true;
        }
        if (condition.operator == GuiConditionOperator.ANY) {
            for (int i = 0; i < condition.children.size(); i++) {
                if (evaluate(condition.children.get(i), data, session)) {
                    return true;
                }
            }
            return false;
        }

        Object actual = (condition.source == ContainerGuiDefinition.Condition.Source.SESSION ? session : data)
                .get(condition.field);
        if (condition.operator == GuiConditionOperator.EQUALS) {
            return equal(actual, condition.expected);
        }
        if (condition.operator == GuiConditionOperator.NOT_EQUALS) {
            return !equal(actual, condition.expected);
        }

        int comparison = compare(actual, condition.expected);
        if (condition.operator == GuiConditionOperator.GREATER_THAN) {
            return comparison > 0;
        }
        if (condition.operator == GuiConditionOperator.GREATER_OR_EQUAL) {
            return comparison >= 0;
        }
        if (condition.operator == GuiConditionOperator.LESS_THAN) {
            return comparison < 0;
        }
        return comparison <= 0;
    }

    private static boolean equal(Object left, Object right) {
        if (left instanceof Number && right instanceof Number) {
            return Double.compare(((Number) left).doubleValue(), ((Number) right).doubleValue()) == 0;
        }
        return left == null ? right == null : left.equals(right);
    }

    private static int compare(Object left, Object right) {
        if (left instanceof Number && right instanceof Number) {
            return Double.compare(((Number) left).doubleValue(), ((Number) right).doubleValue());
        }
        return String.valueOf(left).compareTo(String.valueOf(right));
    }

    @FunctionalInterface
    public interface ValueProvider {
        Object get(String field);
    }
}

package betamoon.tileentity;

/** Operators supported by a container element's visibleWhen declaration. */
public enum GuiConditionOperator {
    ALL("all", false), ANY("any", false), EQUALS("equals", false), NOT_EQUALS("notEquals", false), GREATER_THAN(
            "greaterThan", true), GREATER_OR_EQUAL("greaterOrEqual",
                    true), LESS_THAN("lessThan", true), LESS_OR_EQUAL("lessOrEqual", true);

    private final String luaName;
    private final boolean ordered;

    GuiConditionOperator(String luaName, boolean ordered) {
        this.luaName = luaName;
        this.ordered = ordered;
    }

    public boolean isGroup() {
        return this == ALL || this == ANY;
    }

    public boolean isOrdered() {
        return ordered;
    }

    public static GuiConditionOperator fromLua(String value) {
        for (GuiConditionOperator operator : values()) {
            if (operator.luaName.equals(value)) {
                return operator;
            }
        }
        return null;
    }
}

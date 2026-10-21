package betamoon.luaapi.config;

/** One presentation entry in a config object's editor layout. */
public final class ScriptConfigLayoutEntry {
    public enum Kind {
        FIELD, HEADING, DIVIDER
    }

    private final Kind kind;
    private final String text;

    ScriptConfigLayoutEntry(Kind kind, String text) {
        this.kind = kind;
        this.text = text;
    }

    public Kind kind() {
        return kind;
    }

    /** Field key or heading text; empty for a divider. */
    public String text() {
        return text;
    }
}

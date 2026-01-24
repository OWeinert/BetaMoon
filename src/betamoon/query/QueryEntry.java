package betamoon.query;

public final class QueryEntry {
    public final int id;
    public final int damage;
    public final String internalName;
    public final String displayName;

    public QueryEntry(int id, int damage, String internalName, String displayName) {
        this.id = id;
        this.damage = damage;
        this.internalName = internalName;
        this.displayName = displayName;
    }
}

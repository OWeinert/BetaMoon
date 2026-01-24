package betamoon.query;

import java.util.List;

public abstract class ContentQueryEntry extends ContentQuery<QueryEntry> {
    @Override
    protected QueryStepResult<List<QueryEntry>> selectFirst(List<QueryEntry> state) {
        if (state.isEmpty()) {
            return QueryStepResult.failure(emptyQueryMessage());
        }
        return QueryStepResult.success(wrapSingle(state.get(0)));
    }

    @Override
    protected QueryStepResult<List<QueryEntry>> selectLast(List<QueryEntry> state) {
        if (state.isEmpty()) {
            return QueryStepResult.failure(emptyQueryMessage());
        }
        return QueryStepResult.success(wrapSingle(state.get(state.size() - 1)));
    }

    public ContentQueryEntry filterByName(final String name) {
        addFilterStep("filterByName", quote(name),
            (List<QueryEntry> state) -> QueryStepResult.success(QueryEntries.filterEntries(state,
                entry -> name.equals(entry.internalName))));
        return this;
    }

    public ContentQueryEntry filterByDisplayName(final String name) {
        addFilterStep("filterByDisplayName", quote(name),
            (List<QueryEntry> state) -> QueryStepResult.success(QueryEntries.filterEntries(state,
                entry -> name.equals(entry.displayName))));
        return this;
    }

    public ContentQueryEntry filterDamage(final int min, final int max) {
        addFilterStep("filterDamage", min + ", " + max, (List<QueryEntry> state) -> {
            List filtered = QueryEntries.filterEntries(state,
                entry -> entry.damage >= min && entry.damage <= max);
            return QueryStepResult.success(filtered);
        });
        return this;
    }

    public ContentQueryEntry getByDamage(final int damage) {
        addSingleStep("getByDamage", String.valueOf(damage), (List<QueryEntry> state) -> {
            QueryEntry match = null;
            for (int i = 0; i < state.size(); i++) {
                QueryEntry entry = state.get(i);
                if (entry.damage == damage) {
                    if (match != null) {
                        return QueryStepResult.failure("Query: multiple " + pluralType()
                            + " match damage value: " + damage);
                    }
                    match = entry;
                }
            }
            if (match == null) {
                return QueryStepResult.failure("Query: no " + getQueryType()
                    + " found with damage value: " + damage);
            }
            return QueryStepResult.success(wrapSingle(match));
        });
        return this;
    }

    public ContentQueryEntry fromHandle(final int id, final int damage, final boolean valid) {
        addSingleStep("fromHandle", id + ", " + damage, (List<QueryEntry> state) -> {
            if (!valid) {
                return QueryStepResult.failure("Query: " + getQueryType() + " handle not found in registry.");
            }
            QueryEntry entry = QueryEntries.findById(state, id, damage);
            if (entry == null) {
                return QueryStepResult.failure("Query: " + getQueryType() + " handle not found in registry.");
            }
            return QueryStepResult.success(wrapSingle(entry));
        });
        return this;
    }
}

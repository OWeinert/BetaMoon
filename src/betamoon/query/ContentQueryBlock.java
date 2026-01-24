package betamoon.query;

import java.util.List;

public final class ContentQueryBlock extends ContentQueryEntry {
    @Override
    protected List<QueryEntry> createInitialState() {
        return QueryEntries.buildBlockEntries();
    }

    @Override
    protected boolean isEmpty(List<QueryEntry> state) {
        return state == null || state.isEmpty();
    }

    @Override
    protected String getQueryType() {
        return "block";
    }

    @Override
    protected QueryStepResult<List<QueryEntry>> selectByIndex(List<QueryEntry> state, int index) {
        if (index < 0 || index > 255) {
            return QueryStepResult.failure("Query: block index out of bounds (0-255): " + index);
        }
        QueryEntry entry = QueryEntries.findFirst(state, entryObj -> ((QueryEntry) entryObj).id == index);
        if (entry == null) {
            return QueryStepResult.failure("Query: block id not found in query: " + index);
        }
        return QueryStepResult.success(wrapSingle(entry));
    }
    
    public ContentQueryBlock getById(final int id) {
        addSingleStep("getById", String.valueOf(id), (List<QueryEntry> state) -> {
            if (id < 0 || id > 255) {
                return QueryStepResult.failure("Query: block id outside allowed range (0-255): " + id);
            }
            QueryEntry entry = QueryEntries.findById(state, id, 0);
            if (entry == null) {
                return QueryStepResult.failure("Query: block id not registered: " + id);
            }
            return QueryStepResult.success(wrapSingle(entry));
        });
        return this;
    }
}

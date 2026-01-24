package betamoon.query;

import java.util.List;

public final class ContentQueryItem extends ContentQueryEntry {
    @Override
    protected List<QueryEntry> createInitialState() {
        return QueryEntries.buildItemEntries();
    }

    @Override
    protected boolean isEmpty(List<QueryEntry> state) {
        return state == null || state.isEmpty();
    }

    @Override
    protected String getQueryType() {
        return "item";
    }

    @Override
    protected QueryStepResult<List<QueryEntry>> selectByIndex(List<QueryEntry> state, int index) {
        if (index <= 255) {
            return QueryStepResult.failure("Query: item index must be > 255: " + index);
        }
        QueryEntry entry = QueryEntries.findFirstById(state, index);
        if (entry == null) {
            return QueryStepResult.failure("Query: item id not found in query: " + index);
        }
        return QueryStepResult.success(wrapSingle(entry));
    }
    
    public ContentQueryItem getById(final int id) {
        addSingleStep("getById", String.valueOf(id), (List<QueryEntry> state) -> {
            if (id < 256) {
                return QueryStepResult.failure("Query: item id must be >= 256: " + id);
            }
            QueryEntry entry = QueryEntries.findById(state, id, 0);
            if (entry == null) {
                return QueryStepResult.failure("Query: item id not registered: " + id);
            }
            return QueryStepResult.success(wrapSingle(entry));
        });
        return this;
    }
}

package betamoon.query;

public final class QueryExecutionResult<TState> {
    private final TState state;
    private final ContentQuery.ResultMode resultMode;
    private final QueryFailure failure;
    private final QueryFailure warning;

    private QueryExecutionResult(TState state, ContentQuery.ResultMode resultMode, QueryFailure failure,
        QueryFailure warning) {
        this.state = state;
        this.resultMode = resultMode;
        this.failure = failure;
        this.warning = warning;
    }

    public static <TState> QueryExecutionResult<TState> success(TState state, ContentQuery.ResultMode mode,
        QueryFailure warning) {
        return new QueryExecutionResult<TState>(state, mode, null, warning);
    }

    public static <TState> QueryExecutionResult<TState> failure(QueryFailure failure) {
        return new QueryExecutionResult<TState>(null, null, failure, null);
    }

    public boolean isFailure() {
        return failure != null;
    }

    public TState getState() {
        return state;
    }

    public ContentQuery.ResultMode getResultMode() {
        return resultMode;
    }

    public QueryFailure getFailure() {
        return failure;
    }

    public QueryFailure getWarning() {
        return warning;
    }
}

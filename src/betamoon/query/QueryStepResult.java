package betamoon.query;

public final class QueryStepResult<TState> {
    private final TState state;
    private final String failureMessage;

    private QueryStepResult(TState state, String failureMessage) {
        this.state = state;
        this.failureMessage = failureMessage;
    }

    public static <TState> QueryStepResult<TState> success(TState state) {
        return new QueryStepResult<TState>(state, null);
    }

    public static <TState> QueryStepResult<TState> failure(String message) {
        return new QueryStepResult<TState>(null, message);
    }

    public boolean isFailure() {
        return failureMessage != null;
    }

    public TState getState() {
        return state;
    }

    public String getFailureMessage() {
        return failureMessage;
    }
}

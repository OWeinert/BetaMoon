package betamoon.query;

@FunctionalInterface
public interface IQueryStepAction<TState> {
    QueryStepResult<TState> apply(TState state);
}

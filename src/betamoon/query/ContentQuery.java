package betamoon.query;

import java.util.ArrayList;
import java.util.List;

public abstract class ContentQuery<TResult> {
    public enum ResultMode {
        LIST,
        SINGLE
    }

    private final List<QueryStep<List<TResult>>> steps = new ArrayList<QueryStep<List<TResult>>>();
    private ResultMode resultMode = ResultMode.LIST;
    private List<TResult> results;

    protected final void addStep(QueryStep<List<TResult>> step) {
        steps.add(step);
    }

    protected final void addFilterStep(String name, String detail, IQueryStepAction<List<TResult>> action) {
        addStep(new QueryStep<List<TResult>>(name, detail, action));
    }

    protected final void addSingleStep(String name, String detail, IQueryStepAction<List<TResult>> action) {
        addStep(new QueryStep<List<TResult>>(name, detail, action));
        setResultMode(ResultMode.SINGLE);
    }

    protected final void addFailureStep(String name, String detail, String message) {
        addStep(new QueryStep<List<TResult>>(name, detail, state -> QueryStepResult.failure(message)));
    }

    protected final void setResultMode(ResultMode mode) {
        this.resultMode = mode;
    }

    protected final ResultMode getResultMode() {
        return resultMode;
    }

    protected abstract List<TResult> createInitialState();

    protected abstract boolean isEmpty(List<TResult> state);

    protected abstract String getQueryType();

    protected abstract QueryStepResult<List<TResult>> selectFirst(List<TResult> state);

    protected abstract QueryStepResult<List<TResult>> selectLast(List<TResult> state);

    protected abstract QueryStepResult<List<TResult>> selectByIndex(List<TResult> state, int index);

    protected final String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value + "\"";
    }

    protected final String pluralType() {
        return getQueryType() + "s";
    }

    protected final String emptyQueryMessage() {
        return "Query: no " + pluralType() + " found in query.";
    }

    protected final List<TResult> wrapSingle(TResult entry) {
        List<TResult> out = new ArrayList<TResult>();
        out.add(entry);
        return out;
    }

    public ContentQuery<TResult> first() {
        addSingleStep("first", null, state -> selectFirst(state));
        return this;
    }

    public ContentQuery<TResult> last() {
        addSingleStep("last", null, state -> selectLast(state));
        return this;
    }

    public ContentQuery<TResult> get(int index) {
        addSingleStep("get", String.valueOf(index), state -> selectByIndex(state, index));
        return this;
    }

    public final QueryExecutionResult<List<TResult>> execute() {
        results = createInitialState();
        List<QueryStep<List<TResult>>> executed = new ArrayList<QueryStep<List<TResult>>>();
        QueryFailure warning = null;
        QueryStep<List<TResult>> warningStep = null;
        String warningMessage = null;
        for (int i = 0; i < steps.size(); i++) {
            QueryStep<List<TResult>> step = steps.get(i);
            executed.add(step);
            if (warningMessage != null) {
                continue;
            }
            QueryStepResult<List<TResult>> result = step.getAction().apply(results);
            if (result.isFailure()) {
                return QueryExecutionResult.failure(buildFailure(result.getFailureMessage(), executed, step));
            }
            results = result.getState();
            if (warningMessage == null && isEmpty(results)) {
                warningMessage = "Query: no results after " + step.describe();
                warningStep = step;
            }
        }
        if (warningMessage != null) {
            warning = buildFailure(warningMessage, executed, warningStep);
        }
        return QueryExecutionResult.success(results, resultMode, warning);
    }

    private QueryFailure buildFailure(String message, List<QueryStep<List<TResult>>> executed,
        QueryStep<List<TResult>> step) {
        return new QueryFailure(message, formatTree(executed, step), step.getName());
    }

    private String formatTree(List<QueryStep<List<TResult>>> executed, QueryStep<List<TResult>> failedStep) {
        StringBuilder builder = new StringBuilder();
        builder.append(getQueryType()).append(" query:");
        for (int i = 0; i < executed.size(); i++) {
            QueryStep<List<TResult>> step = executed.get(i);
            builder.append("\n  - ").append(step.describe());
            if (step == failedStep) {
                builder.append("  <--- FAILED HERE");
            }
        }
        return builder.toString();
    }
}

package betamoon.query;

public final class QueryStep<TState> {
    private final String name;
    private final String detail;
    private final IQueryStepAction<TState> action;

    public QueryStep(String name, String detail, IQueryStepAction<TState> action) {
        this.name = name;
        this.detail = detail;
        this.action = action;
    }

    public String getName() {
        return name;
    }

    public String getDetail() {
        return detail;
    }

    public IQueryStepAction<TState> getAction() {
        return action;
    }

    public String describe() {
        if (detail == null || detail.length() == 0) {
            return name;
        }
        return name + "(" + detail + ")";
    }
}

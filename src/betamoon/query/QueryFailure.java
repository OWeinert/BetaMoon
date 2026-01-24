package betamoon.query;

public final class QueryFailure {
    private final String message;
    private final String tree;
    private final String stepName;

    public QueryFailure(String message, String tree, String stepName) {
        this.message = message;
        this.tree = tree;
        this.stepName = stepName;
    }

    public String getMessage() {
        return message;
    }

    public String getTree() {
        return tree;
    }

    public String getStepName() {
        return stepName;
    }
}

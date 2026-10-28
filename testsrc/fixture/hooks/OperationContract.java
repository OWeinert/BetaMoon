package fixture.hooks;

public interface OperationContract {
    int value(int input);

    default int defaultValue(int input) {
        return input + 20;
    }
}

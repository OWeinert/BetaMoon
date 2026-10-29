package betamoon.interop.fixture;

/**
 * Resource fixture with observable normal/failing disposal and no Minecraft
 * dependency.
 */
public final class NativeFixtureOwned {
    public static int closed;
    public static int failed;
    public int number;
    public final Node child = new Node();

    public Node getChild() {
        return child;
    }

    public void close() {
        closed++;
    }

    public void failClose() {
        failed++;
        throw new IllegalStateException("fixture disposal failure");
    }

    public static final class Node {
        public int number = 1;
    }
}

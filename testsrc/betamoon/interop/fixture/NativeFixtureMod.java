package betamoon.interop.fixture;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Arbitrary mod-shaped API; the engine has no adapter or allowlist entry for
 * this class.
 */
public class NativeFixtureMod extends NativeFixtureParent {
    static {
        System.setProperty("betamoon.native.binding.initialized", "yes");
    }

    public int number = 3;
    public final int fixed = 9;
    public int collision = 2;
    private int hidden = 17;
    private Node node = new Node();
    public Object opaqueNode = node;
    public Node optionalNode;
    public static int counter;
    public final List<Object> list = new ArrayList<>();
    public final Map<Object, Object> map = new HashMap<>();
    public final Number customNumber = new Number() {
        public int intValue() {
            counter++;
            return 1;
        }

        public long longValue() {
            counter++;
            return 1;
        }

        public float floatValue() {
            counter++;
            return 1;
        }

        public double doubleValue() {
            counter++;
            return 1;
        }
    };
    public final List<Object> custom = new ArrayList<Object>() {
        @Override
        public int size() {
            counter++;
            return super.size();
        }
    };

    public NativeFixtureMod() {
        list.add("first");
        list.add(null);
        list.add(node);
    }

    private NativeFixtureMod(int number) {
        this.number = number;
    }

    public String which(int value) {
        return "int";
    }

    @Override
    public String covariant() {
        return "child";
    }

    public String which(long value) {
        return "long";
    }

    public String which(double value) {
        return "double";
    }

    public String which(float value) {
        return "float";
    }

    public byte byteEcho(byte value) {
        return value;
    }

    public String narrow(byte value) {
        return "byte";
    }

    public String narrow(short value) {
        return "short";
    }

    public String ambiguous(Serializable value) {
        return "serializable";
    }

    public String ambiguous(CharSequence value) {
        return "sequence";
    }

    public String nullable(String value) {
        return value == null ? "string-null" : value;
    }

    public String nullable(Object value) {
        return value == null ? "object-null" : "object";
    }

    public String text(String value) {
        return value;
    }

    public char character(char value) {
        return value;
    }

    public long longEcho(long value) {
        return value;
    }

    public long maximum() {
        return Long.MAX_VALUE;
    }

    public long minimum() {
        return Long.MIN_VALUE;
    }

    public Object nothing() {
        return null;
    }

    public String collision() {
        return "method";
    }

    public int sum(int... values) {
        int total = 0;
        for (int value : values) {
            total += value;
        }
        return total;
    }

    public void update() {
        counter++;
    }

    public static int staticEcho(int value) {
        return value + 1;
    }

    public static void staticNoArguments() {
        counter++;
    }

    private String secret(int value) {
        return "private:" + value;
    }

    String packageEcho(int value) {
        return "package:" + value;
    }

    public String unpaired() {
        return new String(new char[]{'a', '\ud800'});
    }

    public void fail() {
        throw new IllegalStateException("native fixture failure", new IllegalArgumentException("root cause"));
    }

    public void fatal() {
        throw new FixtureFatalError();
    }

    public double negativeZero() {
        return -0.0;
    }

    public long bits(double value) {
        return Double.doubleToRawLongBits(value);
    }

    public String largeText() {
        char[] text = new char[300000];
        Arrays.fill(text, 'a');
        return new String(text);
    }

    public static final class FixtureFatalError extends VirtualMachineError {
        public FixtureFatalError() {
            super("fixture fatal signal");
        }
    }

    private static class Node {
        private int value = 7;
        private Node next;
        public String publicOnPrivateClass() {
            return "node";
        }
    }

    public enum Tag {
        FIRST, SECOND
    }
}

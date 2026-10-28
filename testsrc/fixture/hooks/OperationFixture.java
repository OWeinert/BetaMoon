package fixture.hooks;

import java.util.ArrayList;
import java.util.List;

public class OperationFixture implements OperationContract {
    public static final List<String> EVENTS = new ArrayList<>();
    public static final RuntimeException FAILURE = new IllegalStateException("operation native failure");
    public volatile long counter;
    public static volatile double shared = 1.25;
    public byte small;
    public short medium;
    public char character;
    public boolean enabled;
    public static final int CONSTANT = 7;

    public int scalar(int input) {
        EVENTS.add("callee:" + input);
        return input * 2;
    }

    public int two(OperationFixture other, int input) {
        int first = scalar(side("first", input));
        input = 42;
        return first + other.scalar(side("second", input));
    }

    public static int side(String label, int input) {
        EVENTS.add(label + ":" + input);
        return input;
    }

    public static long wide(long input, double fraction, Object object) {
        return input + (long) fraction;
    }

    public static int wide(int input) {
        return input + 1000;
    }

    public long staticCall(long input, double fraction, Object object) {
        return wide(input, fraction, object) + wide(1);
    }

    private int secret(int input) {
        return input + 3;
    }

    public int special(int input) {
        return secret(input);
    }

    public int contract(OperationContract other, int input) {
        return other.value(side("interface", input));
    }

    @Override
    public int value(int input) {
        if (input < 0) {
            throw FAILURE;
        }
        return input + 10;
    }

    public long read() {
        return counter;
    }

    public void write(long value) {
        counter = value;
    }

    public double readShared() {
        return shared;
    }

    public void writeSmall(byte value) {
        small = value;
    }

    public void writeShort(short value) {
        medium = value;
    }

    public void writeChar(char value) {
        character = value;
    }

    public static void voidTarget(int value) {
        EVENTS.add("void:" + value);
    }

    public void voidCall(int value) {
        voidTarget(value);
    }

    public void writeBoolean(boolean value) {
        enabled = value;
    }

    public static String text() {
        return "native";
    }

    public String constructing() {
        return new StringBuilder(text()).toString();
    }

    public int useDefault(int value) {
        return OperationContract.super.defaultValue(value);
    }

    public int math(int value) {
        return Math.abs(value);
    }
}

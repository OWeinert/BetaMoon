package betamoon.instrumentation.api;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Exact argument-producing tails for optional semantic native call redirects.
 */
public final class CallRedirectShape {
    public enum Kind {
        INTEGER, LOCAL_INTEGER, OPCODE
    }

    public static final class Instruction {
        public final Kind kind;
        public final int value;

        private Instruction(Kind kind, int value) {
            this.kind = kind;
            this.value = value;
        }
    }

    public final int occurrences;
    public final List<Instruction> tail;

    public CallRedirectShape(int occurrences, Instruction... tail) {
        if (occurrences < 1 || occurrences > 64 || tail == null || tail.length < 1 || tail.length > 64
                || Arrays.asList(tail).contains(null)) {
            throw new IllegalArgumentException("Call shape requires bounded occurrences and an exact instruction tail");
        }
        this.occurrences = occurrences;
        this.tail = Collections.unmodifiableList(Arrays.asList(tail.clone()));
    }

    public static Instruction integer(int value) {
        return new Instruction(Kind.INTEGER, value);
    }

    public static Instruction localInteger() {
        return new Instruction(Kind.LOCAL_INTEGER, 0);
    }

    public static Instruction opcode(int opcode) {
        if (opcode < 0 || opcode > 255) {
            throw new IllegalArgumentException("Invalid JVM opcode");
        }
        return new Instruction(Kind.OPCODE, opcode);
    }
}

package betamoon.interop.query;

import betamoon.interop.reference.NativeMemberRef;
import betamoon.interop.reference.NativeTypeRef;
import betamoon.interop.value.NativeExactValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable symbolic selection. It retains neither executable classes nor
 * scoped native values.
 */
public final class NativeDataPlan {
    public static final int MAXIMUM_WORK = 4096;
    public static final int MAXIMUM_DEPTH = 8;
    public static final int MAXIMUM_BYTES = 256 * 1024;
    public static final int MAXIMUM_REQUESTS = 256;
    public final NativeTypeRef type;
    public final List<Selection> selections;
    public final int maximumBytes;
    public final int nodes;
    public final int depth;
    public final int metadataBytes;

    public NativeDataPlan(NativeTypeRef type, List<Selection> selections, int maximumBytes) {
        this.type = type;
        this.selections = Collections.unmodifiableList(new ArrayList<>(selections));
        this.maximumBytes = maximumBytes;
        int count = 1;
        int nesting = 1;
        int footprint = 32 + type.key().length() * 2;
        for (Selection selection : selections) {
            count += 1 + selection.arguments.size();
            footprint += selection.metadataBytes();
            for (NativeDataPlan nested : selection.projection.plans()) {
                count += nested.nodes;
                nesting = Math.max(nesting, nested.depth + 1);
            }
            if (count > MAXIMUM_WORK || footprint > MAXIMUM_BYTES) {
                throw new IllegalArgumentException("data plan exceeds its metadata limits");
            }
        }
        if (count > MAXIMUM_WORK || nesting > MAXIMUM_DEPTH || maximumBytes < 1 || maximumBytes > MAXIMUM_BYTES) {
            throw new IllegalArgumentException("data plan exceeds its definition limits");
        }
        nodes = count;
        depth = nesting;
        metadataBytes = footprint;
    }

    public static final class Selection {
        public final String key;
        public final NativeMemberRef member;
        public final List<Argument> arguments;
        public final Projection projection;
        public final Slice slice;

        public Selection(String key, NativeMemberRef member, List<Argument> arguments, Projection projection,
                Slice slice) {
            this.key = key;
            this.member = member;
            this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
            this.projection = projection;
            this.slice = slice;
        }

        public int metadataBytes() {
            int result = 64 + (key.length() + member.key().length()) * 2;
            for (Argument argument : arguments) {
                if (argument.literal instanceof NativeExactValue
                        && ((NativeExactValue) argument.literal).type != null) {
                    result += ((NativeExactValue) argument.literal).type.key().length() * 2;
                }
                Object literal = argument.literal instanceof NativeExactValue
                        ? ((NativeExactValue) argument.literal).value
                        : argument.literal;
                result += 32 + (argument.input == null ? 0 : argument.input.length() * 2);
                if (literal instanceof String) {
                    result += ((String) literal).length() * 2;
                }
            }
            for (NativeDataPlan nested : projection.plans()) {
                result += nested.metadataBytes;
            }
            return result;
        }
    }

    public static final class Argument {
        public final String input;
        public final Object literal;

        public Argument(String input, Object literal) {
            this.input = input;
            this.literal = literal;
        }
    }

    public static final class Slice {
        public final int start;
        public final int count;

        public Slice(int start, int count) {
            this.start = start;
            this.count = count;
        }
    }

    public static final class Projection {
        public enum Kind {
            SCALAR, OBJECT, EACH, MAP
        }

        public final Kind kind;
        public final NativeDataPlan first;
        public final NativeDataPlan second;

        public Projection(Kind kind, NativeDataPlan first, NativeDataPlan second) {
            this.kind = kind;
            this.first = first;
            this.second = second;
        }

        public List<NativeDataPlan> plans() {
            List<NativeDataPlan> result = new ArrayList<>();
            if (first != null) {
                result.add(first);
            }
            if (second != null) {
                result.add(second);
            }
            return result;
        }
    }
}

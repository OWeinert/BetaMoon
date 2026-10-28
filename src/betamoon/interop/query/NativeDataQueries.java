package betamoon.interop.query;

import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.binding.NativeBindings;
import betamoon.interop.binding.NativeMemberPlan;
import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.catalog.CatalogJson;
import betamoon.interop.diagnostics.NativeDiagnosticEvidence;
import betamoon.interop.query.NativeDataPlan.Argument;
import betamoon.interop.query.NativeDataPlan.Projection;
import betamoon.interop.query.NativeDataPlan.Selection;
import betamoon.interop.query.NativeDataPlan.Slice;
import betamoon.interop.reference.NativeMemberRef;
import betamoon.interop.runtime.NativeContext;
import betamoon.interop.value.NativeContainerPolicy;
import betamoon.interop.value.NativeExactValue;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Bounded Java projection engine. Executable caches belong to the binding
 * session, never to Lua plans.
 */
public final class NativeDataQueries {
    private final NativeBindings bindings;
    private final Map<NativeDataPlan, Prepared> managed = new IdentityHashMap<>();
    private final Map<NativeDataPlan, Prepared> direct = new IdentityHashMap<>();
    private final Map<Prepared, Boolean> accessOrder = new LinkedHashMap<Prepared, Boolean>(64, 0.75F, true);
    private int cachedNodes;
    private long revision = -1;

    public NativeDataQueries(NativeBindings bindings) {
        this.bindings = bindings;
    }

    public void clear() {
        managed.clear();
        direct.clear();
        accessOrder.clear();
        cachedNodes = 0;
    }

    public Object execute(NativeDataPlan plan, List<Request> requests, NativeContext context, boolean directAccess,
            boolean many) {
        return execute(plan, requests, context, directAccess, many, NativeDataOutput.JAVA);
    }

    public Object execute(NativeDataPlan plan, List<Request> requests, NativeContext context, boolean directAccess,
            boolean many, NativeDataOutput output) {
        context.check();
        if (context.bindings != bindings || requests.size() > NativeDataPlan.MAXIMUM_REQUESTS
                || !many && requests.size() != 1) {
            throw new NativeAccessFailure("invalid_data_requests", "wrong session or invalid request count");
        }
        State state = new State(context, directAccess, plan.maximumBytes, output);
        if (many && requests.isEmpty()) {
            state.bytes(32);
            return output.sequence(0);
        }
        Prepared prepared;
        try {
            prepared = prepared(plan, context, directAccess);
        } catch (NativeAccessFailure failure) {
            throw failure.explain(CatalogValues.record("requestIndex", 1, "dataPath", "$"));
        }
        List<Invocation> invocations = new ArrayList<>(requests.size());
        for (int index = 0; index < requests.size(); index++) {
            state.request = index + 1;
            Request request = requests.get(index);
            try {
                state.work();
                prepared.receiver(request.receiver);
                Invocation invocation = new Invocation(request, prepared);
                preflight(prepared, invocation, state);
                for (BoundSelection selection : prepared.selections) {
                    selection.member.preflight(context, directAccess,
                            selection.member.isStatic ? null : request.receiver);
                }
                invocations.add(invocation);
            } catch (NativeAccessFailure failure) {
                throw state.explain(failure, "$");
            }
        }
        Object results = many ? output.sequence(invocations.size()) : null;
        Object first = null;
        if (many) {
            state.bytes(32);
        }
        for (int index = 0; index < invocations.size(); index++) {
            state.request = index + 1;
            Invocation invocation = invocations.get(index);
            Object result = record(prepared, invocation.request.receiver, invocation, state, 1);
            if (many) {
                output.element(results, index, result);
            } else {
                first = result;
            }
        }
        context.check();
        return many ? results : first;
    }

    private Prepared prepared(NativeDataPlan plan, NativeContext context, boolean directAccess) {
        long observed = bindings.bindingRevision();
        Map<NativeDataPlan, Prepared> cache = directAccess ? direct : managed;
        Prepared found = revision == observed ? cache.get(plan) : null;
        if (found != null) {
            accessOrder.get(found);
            return found;
        }
        context.prepare();
        if (revision != observed) {
            clear();
        }
        Prepared result = compile(plan, context, directAccess, new IdentityHashMap<>(), "$");
        List<Preflight> operations = new ArrayList<>();
        int[] argumentSlots = {0};
        Map<String, Integer> inputSlots = new LinkedHashMap<>();
        flatten(result, new IdentityHashMap<>(), "$", operations, argumentSlots, inputSlots);
        result.inputNames = inputSlots.keySet().toArray(new String[0]);
        result.preflight = operations.toArray(new Preflight[0]);
        result.argumentSlots = argumentSlots[0];
        // Type resolution may have discovered a defining loader while preparing this
        // plan.
        if (revision != bindings.bindingRevision()) {
            clear();
        }
        revision = bindings.bindingRevision();
        while (!accessOrder.isEmpty()
                && (accessOrder.size() >= 64 || plan.nodes > NativeDataPlan.MAXIMUM_WORK - cachedNodes)) {
            Prepared eldest = accessOrder.keySet().iterator().next();
            accessOrder.remove(eldest);
            (eldest.directAccess ? direct : managed).remove(eldest.plan, eldest);
            cachedNodes -= eldest.plan.nodes;
        }
        cache.put(plan, result);
        accessOrder.put(result, Boolean.TRUE);
        cachedNodes += plan.nodes;
        return result;
    }

    private Prepared compile(NativeDataPlan plan, NativeContext context, boolean directAccess,
            Map<NativeDataPlan, Prepared> seen, String prefix) {
        Prepared previous = seen.get(plan);
        if (previous != null) {
            return previous;
        }
        Class<?> expected = bindings.type(plan.type, context);
        Prepared result = new Prepared(plan, expected, directAccess);
        seen.put(plan, result);
        for (Selection selection : plan.selections) {
            try {
                NativeMemberPlan member = bindings.exact(selection.member, context, directAccess);
                if (!member.member.getDeclaringClass().isAssignableFrom(expected)) {
                    throw new NativeAccessFailure("wrong_declaring_owner", selection.key);
                }
                Prepared first = selection.projection.first == null
                        ? null
                        : compile(selection.projection.first, context, directAccess, seen, path(prefix, selection.key));
                Prepared second = selection.projection.second == null
                        ? null
                        : compile(selection.projection.second, context, directAccess, seen,
                                path(prefix, selection.key));
                validateProjection(selection, member.valueType, first);
                result.selections.add(new BoundSelection(selection, member, first, second));
                result.instance |= !member.isStatic;
            } catch (NativeAccessFailure failure) {
                throw failure.explain(CatalogValues.record("dataPath", path(prefix, selection.key)));
            }
        }
        return result;
    }

    private void validateProjection(Selection selection, Class<?> valueType, Prepared first) {
        Projection.Kind kind = selection.projection.kind;
        boolean unknown = valueType == Object.class || valueType.isInterface();
        if (kind == Projection.Kind.OBJECT) {
            if (selection.slice != null || valueType.isPrimitive() || !unknown
                    && !first.expected.isAssignableFrom(valueType) && !valueType.isAssignableFrom(first.expected)) {
                throw new NativeAccessFailure("invalid_data_projection",
                        selection.key + ": incompatible object selection");
            }
        } else if (kind == Projection.Kind.EACH) {
            if (!unknown && !valueType.isArray() && !List.class.isAssignableFrom(valueType)) {
                throw new NativeAccessFailure("invalid_data_projection",
                        selection.key + ": each needs an array or List");
            }
            if (first != null && valueType.isArray() && valueType.getComponentType().isPrimitive()) {
                throw new NativeAccessFailure("invalid_data_projection",
                        selection.key + ": primitive elements cannot use an object plan");
            }
        } else if (kind == Projection.Kind.MAP) {
            if (selection.slice != null || !unknown && !Map.class.isAssignableFrom(valueType)) {
                throw new NativeAccessFailure("invalid_data_projection",
                        selection.key + ": map selection requires a Map without slices");
            }
        } else {
            boolean primitiveArray = valueType.isArray() && valueType.getComponentType().isPrimitive();
            if (selection.slice != null && !primitiveArray) {
                throw new NativeAccessFailure("invalid_data_projection",
                        selection.key + ": slice requires an array/list selection");
            }
            boolean scalar = valueType.isPrimitive() || valueType.isAssignableFrom(String.class)
                    || valueType.isAssignableFrom(Boolean.class) || valueType.isAssignableFrom(Character.class)
                    || valueType.isAssignableFrom(Byte.class) || valueType.isAssignableFrom(Short.class)
                    || valueType.isAssignableFrom(Integer.class) || valueType.isAssignableFrom(Long.class)
                    || valueType.isAssignableFrom(Float.class) || valueType.isAssignableFrom(Double.class);
            if (!scalar && !primitiveArray) {
                throw new NativeAccessFailure("native_data_projection_required",
                        selection.key + ": select object data explicitly");
            }
        }
    }

    private void flatten(Prepared prepared, Map<Prepared, Boolean> seen, String prefix, List<Preflight> operations,
            int[] argumentSlots, Map<String, Integer> inputSlots) {
        if (seen.put(prepared, Boolean.TRUE) != null) {
            return;
        }
        for (BoundSelection selection : prepared.selections) {
            String selectedPath = prefix + selection.pathComponent;
            if (selection.definition.member.kind == NativeMemberRef.Kind.METHOD) {
                selection.argumentSlot = argumentSlots[0]++;
                for (int index = 0; index < selection.arguments.length; index++) {
                    String name = selection.arguments[index].input;
                    selection.inputSlots[index] = name == null
                            ? -1
                            : inputSlots.computeIfAbsent(name, key -> inputSlots.size());
                }
            }
            operations.add(new Preflight(selection, selectedPath));
            if (selection.first != null) {
                flatten(selection.first, seen, selectedPath, operations, argumentSlots, inputSlots);
            }
            if (selection.second != null) {
                flatten(selection.second, seen, selectedPath, operations, argumentSlots, inputSlots);
            }
        }
    }

    private void preflight(Prepared prepared, Invocation invocation, State state) {
        for (Preflight operation : prepared.preflight) {
            BoundSelection selection = operation.selection;
            try {
                state.work();
                selection.member.preflightPolicy(state.context, state.directAccess);
                if (selection.argumentSlot >= 0) {
                    Argument[] arguments = selection.arguments;
                    Object[] input = new Object[arguments.length];
                    for (int index = 0; index < input.length; index++) {
                        state.work();
                        Argument argument = arguments[index];
                        int slot = selection.inputSlots[index];
                        Object value = slot < 0 ? argument.literal : invocation.inputs[slot];
                        if (value == Invocation.MISSING) {
                            throw new NativeAccessFailure("native_data_input_missing", argument.input);
                        }
                        input[index] = value;
                    }
                    invocation.arguments[selection.argumentSlot] = bindings.arguments(selection.member, input,
                            state.context);
                }
            } catch (NativeAccessFailure failure) {
                throw state.explain(selectedFailure(failure, selection, state), operation.path);
            }
        }
    }

    private Object record(Prepared prepared, Object receiver, Invocation invocation, State state, int depth) {
        state.enter(receiver, depth);
        int previousLimit = state.ceiling;
        state.ceiling = Math.min(previousLimit, state.usedBytes + prepared.plan.maximumBytes);
        try {
            prepared.receiver(receiver);
            state.bytes(32);
            Object result = state.output.record(prepared.selections.size());
            for (BoundSelection selection : prepared.selections) {
                state.push(selection.pathComponent);
                try {
                    state.work();
                    state.bytes(selection.keyBytes);
                    NativeMemberPlan member = selection.member;
                    Object value = selection.definition.member.kind == NativeMemberRef.Kind.FIELD
                            ? member.get(state.context, state.directAccess, member.isStatic ? null : receiver)
                            : member.callConverted(state.context, state.directAccess, member.isStatic ? null : receiver,
                                    invocation.arguments[selection.argumentSlot]);
                    state.output.field(result, selection.definition.key,
                            project(value, selection, invocation, state, depth + 1));
                } catch (NativeAccessFailure failure) {
                    throw state.explain(selectedFailure(failure, selection, state));
                } finally {
                    state.pop();
                }
            }
            return result;
        } finally {
            state.ceiling = previousLimit;
            state.leave(receiver);
        }
    }

    private Object project(Object value, BoundSelection selection, Invocation invocation, State state, int depth) {
        state.work();
        if (value == null) {
            state.bytes(8);
            return state.output.scalar(NativeExactValue.NULL);
        }
        Projection.Kind kind = selection.definition.projection.kind;
        Slice slice = selection.definition.slice;
        if (kind == Projection.Kind.OBJECT) {
            if (slice != null) {
                throw new NativeAccessFailure("invalid_data_projection", "object selections do not accept slices");
            }
            return record(selection.first, value, invocation, state, depth);
        }
        if (kind == Projection.Kind.MAP) {
            if (!(value instanceof Map) || slice != null) {
                throw new NativeAccessFailure("invalid_data_projection",
                        "map selection requires a Map without a slice");
            }
            return map((Map<?, ?>) value, selection, invocation, state, depth);
        }
        if (kind == Projection.Kind.EACH
                || value.getClass().isArray() && value.getClass().getComponentType().isPrimitive()) {
            if (!value.getClass().isArray() && !(value instanceof List)) {
                throw new NativeAccessFailure("invalid_data_projection", "each requires an array or List");
            }
            return sequence(value, selection, invocation, state, depth);
        }
        if (slice != null) {
            throw new NativeAccessFailure("invalid_data_projection", "a scalar cannot have a slice");
        }
        return scalar(value, state);
    }

    private Object sequence(Object value, BoundSelection selection, Invocation invocation, State state, int depth) {
        state.enter(value, depth);
        try {
            state.context.require(NativeContainerPolicy.effect(value, false), state.directAccess);
            int length = value.getClass().isArray()
                    ? Array.getLength(value)
                    : state.external(() -> ((List<?>) value).size());
            Slice slice = selection.definition.slice;
            int start = slice == null ? 0 : slice.start;
            int count = slice == null ? length : slice.count;
            if (start > length || (long) start + count > length) {
                throw new NativeAccessFailure("array_bounds", "selection exceeds the actual sequence length");
            }
            state.work(count);
            state.bytes(32);
            Object result = state.output.sequence(count);
            ListIterator<?> iterator = value instanceof List
                    ? state.external(() -> ((List<?>) value).listIterator(start))
                    : null;
            for (int index = 0; index < count; index++) {
                Object element = iterator == null ? Array.get(value, start + index) : state.external(iterator::next);
                state.pushIndex(index + 1);
                try {
                    state.output.element(result, index,
                            selected(element, selection.first, invocation, state, depth + 1));
                } catch (NativeAccessFailure failure) {
                    throw state.explain(failure);
                } finally {
                    state.pop();
                }
            }
            if (iterator != null) {
                if (count > 0) {
                    // Standard ArrayList iterators check structural modification on previous(),
                    // including
                    // modifications caused by the last selected element's nested native call.
                    state.external(iterator::previous);
                }
                int after = state.external(() -> ((List<?>) value).size());
                if (length != after) {
                    throw new NativeAccessFailure("native_data_collection_changed", "list changed during projection");
                }
            }
            return result;
        } finally {
            state.leave(value);
        }
    }

    private Object map(Map<?, ?> value, BoundSelection selection, Invocation invocation, State state, int depth) {
        state.enter(value, depth);
        try {
            state.context.require(NativeContainerPolicy.effect(value, false), state.directAccess);
            int length = state.external(value::size);
            if (length < 0) {
                throw new NativeAccessFailure("native_data_collection_changed", "negative map size");
            }
            state.work(length);
            state.bytes(32);
            Object result = state.output.sequence(length);
            int size = 0;
            Iterator<? extends Map.Entry<?, ?>> iterator = state.external(() -> value.entrySet().iterator());
            while (state.external(iterator::hasNext)) {
                if (size >= length) {
                    throw new NativeAccessFailure("native_data_collection_changed", "map grew during projection");
                }
                Map.Entry<?, ?> entry = state.external(iterator::next);
                Object key = state.external(entry::getKey);
                Object element = state.external(entry::getValue);
                state.pushIndex(size + 1);
                try {
                    state.bytes(32);
                    Object copy = state.output.record(2);
                    state.push(".key");
                    try {
                        state.output.field(copy, "key", selected(key, selection.first, invocation, state, depth + 1));
                    } finally {
                        state.pop();
                    }
                    state.push(".value");
                    try {
                        state.output.field(copy, "value",
                                selected(element, selection.second, invocation, state, depth + 1));
                    } finally {
                        state.pop();
                    }
                    state.output.element(result, size++, copy);
                } catch (NativeAccessFailure failure) {
                    throw state.explain(failure);
                } finally {
                    state.pop();
                }
            }
            if (size != length || state.external(value::size) != length) {
                throw new NativeAccessFailure("native_data_collection_changed", "map changed during projection");
            }
            return result;
        } finally {
            state.leave(value);
        }
    }

    private Object selected(Object value, Prepared nested, Invocation invocation, State state, int depth) {
        try {
            state.work();
            return nested == null || value == null
                    ? scalar(value, state)
                    : record(nested, value, invocation, state, depth);
        } catch (NativeAccessFailure failure) {
            throw state.explain(failure);
        }
    }

    private Object scalar(Object value, State state) {
        if (value == null) {
            state.bytes(8);
            return state.output.scalar(NativeExactValue.NULL);
        }
        if (value instanceof String) {
            state.text((String) value);
            return state.output.scalar(value);
        }
        if (value instanceof Boolean || value instanceof Character || value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long || value instanceof Float
                || value instanceof Double) {
            state.bytes(16);
            return state.output.scalar(value);
        }
        throw new NativeAccessFailure("native_data_projection_required",
                "native objects require an explicit selection");
    }

    private NativeAccessFailure selectedFailure(NativeAccessFailure failure, BoundSelection selection, State state) {
        if (failure.diagnostic().containsKey("requestedMember")) {
            return failure;
        }
        return bindings.explain(failure, selection.definition.member.lookupType, selection.definition.member,
                selection.member, state.context, state.directAccess);
    }

    private static int textBytes(String value) {
        long bytes = 16;
        for (int index = 0; index < value.length(); index++) {
            char unit = value.charAt(index);
            if (Character.isHighSurrogate(unit) && index + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(index + 1))) {
                bytes += 4;
                index++;
            } else {
                bytes += unit < 0x80 ? 1 : unit < 0x800 ? 2 : 3;
            }
            if (bytes > NativeDataPlan.MAXIMUM_BYTES) {
                throw new NativeAccessFailure("native_data_byte_limit", "text exceeds detached output limit");
            }
        }
        return (int) bytes;
    }

    private static String path(String parent, String key) {
        return parent + "[" + CatalogJson.encode(key) + "]";
    }

    public static final class Request {
        public final Object receiver;
        public final Map<String, Object> inputs;

        public Request(Object receiver, Map<String, Object> inputs) {
            this.receiver = receiver;
            this.inputs = Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
        }
    }

    private static final class Prepared {
        private final NativeDataPlan plan;
        private final Class<?> expected;
        private final boolean directAccess;
        private final List<BoundSelection> selections = new ArrayList<>();
        private boolean instance;
        private Preflight[] preflight;
        private int argumentSlots;
        private String[] inputNames;

        private Prepared(NativeDataPlan plan, Class<?> expected, boolean directAccess) {
            this.plan = plan;
            this.expected = expected;
            this.directAccess = directAccess;
        }

        private void receiver(Object receiver) {
            if (receiver == null && instance || receiver != null && !expected.isInstance(receiver)) {
                throw new NativeAccessFailure("wrong_receiver", "receiver must be assignable to " + expected.getName());
            }
        }
    }

    private static final class BoundSelection {
        private final Selection definition;
        private final NativeMemberPlan member;
        private final Prepared first;
        private final Prepared second;
        private final Argument[] arguments;
        private final int[] inputSlots;
        private final String pathComponent;
        private final int keyBytes;
        private int argumentSlot = -1;

        private BoundSelection(Selection definition, NativeMemberPlan member, Prepared first, Prepared second) {
            this.definition = definition;
            this.arguments = definition.arguments.toArray(new Argument[0]);
            this.inputSlots = new int[arguments.length];
            this.pathComponent = "[" + CatalogJson.encode(definition.key) + "]";
            this.keyBytes = textBytes(definition.key);
            this.member = member;
            this.first = first;
            this.second = second;
        }
    }

    private static final class Preflight {
        private final BoundSelection selection;
        private final String path;
        private Preflight(BoundSelection selection, String path) {
            this.selection = selection;
            this.path = path;
        }
    }

    private static final class Invocation {
        private final Request request;
        private static final Object MISSING = new Object();
        private static final Object[][] NO_ARGUMENTS = new Object[0][];
        private static final Object[] NO_INPUTS = new Object[0];
        private final Object[][] arguments;
        private final Object[] inputs;

        private Invocation(Request request, Prepared prepared) {
            this.request = request;
            arguments = prepared.argumentSlots == 0 ? NO_ARGUMENTS : new Object[prepared.argumentSlots][];
            inputs = prepared.inputNames.length == 0 ? NO_INPUTS : new Object[prepared.inputNames.length];
            for (int index = 0; index < inputs.length; index++) {
                inputs[index] = request.inputs.getOrDefault(prepared.inputNames[index], MISSING);
            }
        }
    }

    private static final class State {
        private final NativeContext context;
        private final boolean directAccess;
        private final Object[] ancestors = new Object[NativeDataPlan.MAXIMUM_DEPTH];
        private int ancestorCount;
        private final String[] path = new String[NativeDataPlan.MAXIMUM_DEPTH * 3];
        private int[] indices;
        private int pathCount;
        private final NativeDataOutput output;
        private int ceiling;
        private int usedBytes;
        private int request;

        private State(NativeContext context, boolean directAccess, int maximumBytes, NativeDataOutput output) {
            this.context = context;
            this.directAccess = directAccess;
            ceiling = maximumBytes;
            this.output = output;
        }

        private void work() {
            work(1);
        }

        private void work(int count) {
            context.dataBudget.work(count);
        }

        private void bytes(int count) {
            if (count < 0 || count > ceiling - usedBytes) {
                throw new NativeAccessFailure("native_data_byte_limit", "detached output exceeds maxBytes");
            }
            context.dataBudget.bytes(count);
            usedBytes += count;
        }

        private void text(String value) {
            bytes(16);
            // Dynamic strings charge incrementally so repeated refused samples cannot
            // rescan a huge value for free.
            for (int index = 0; index < value.length(); index++) {
                char unit = value.charAt(index);
                if (Character.isHighSurrogate(unit) && index + 1 < value.length()
                        && Character.isLowSurrogate(value.charAt(index + 1))) {
                    bytes(4);
                    index++;
                } else {
                    bytes(unit < 0x80 ? 1 : unit < 0x800 ? 2 : 3);
                }
            }
        }

        private void push(String component) {
            if (pathCount == path.length) {
                throw new NativeAccessFailure("native_data_depth_limit", "data path is too deep");
            }
            path[pathCount++] = component;
        }

        private void pushIndex(int index) {
            if (indices == null) {
                indices = new int[path.length];
            }
            push(null);
            indices[pathCount - 1] = index;
        }

        private void pop() {
            path[--pathCount] = null;
        }

        private String path() {
            StringBuilder result = new StringBuilder("$");
            for (int index = 0; index < pathCount && result.length() < 512; index++) {
                if (path[index] == null) {
                    result.append('[').append(indices[index]).append(']');
                } else {
                    result.append(path[index]);
                }
            }
            return NativeDiagnosticEvidence.bounded(result.toString(), 512);
        }

        private NativeAccessFailure explain(NativeAccessFailure failure) {
            return explain(failure, path());
        }

        private void enter(Object value, int depth) {
            work();
            if (depth > NativeDataPlan.MAXIMUM_DEPTH) {
                throw new NativeAccessFailure("native_data_depth_limit", "projection exceeds depth 8");
            }
            for (int index = 0; value != null && index < ancestorCount; index++) {
                if (ancestors[index] == value) {
                    throw new NativeAccessFailure("native_data_cycle", "selection revisits an ancestor object");
                }
            }
            ancestors[ancestorCount++] = value;
        }

        private void leave(Object value) {
            ancestors[--ancestorCount] = null;
        }

        private <T> T external(Supplier<T> operation) {
            context.check();
            work();
            try {
                T result = operation.get();
                context.check();
                return result;
            } catch (NativeAccessFailure failure) {
                throw failure;
            } catch (ConcurrentModificationException changed) {
                throw new NativeAccessFailure("native_data_collection_changed", "collection changed during projection");
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable failure) {
                throw new NativeAccessFailure("native_exception",
                        "collection operation threw " + failure.getClass().getName(), failure);
            }
        }

        private NativeAccessFailure explain(NativeAccessFailure failure, String path) {
            return failure.explain(CatalogValues.record("requestIndex", request, "dataPath",
                    NativeDiagnosticEvidence.bounded(path, 512)));
        }
    }
}

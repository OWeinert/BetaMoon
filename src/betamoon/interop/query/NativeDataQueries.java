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
    private int cachedNodes;
    private long revision = -1;

    public NativeDataQueries(NativeBindings bindings) {
        this.bindings = bindings;
    }

    public void clear() {
        managed.clear();
        direct.clear();
        cachedNodes = 0;
    }

    public Object execute(NativeDataPlan plan, List<Request> requests, NativeContext context, boolean directAccess,
            boolean many) {
        context.check();
        if (context.bindings != bindings || requests.size() > NativeDataPlan.MAXIMUM_REQUESTS
                || !many && requests.size() != 1) {
            throw new NativeAccessFailure("invalid_data_requests", "wrong session or invalid request count");
        }
        State state = new State(context, directAccess, plan.maximumBytes);
        if (many && requests.isEmpty()) {
            state.bytes(32);
            return Collections.emptyList();
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
                Invocation invocation = new Invocation(request);
                preflight(prepared, invocation, state, new IdentityHashMap<>(), "$");
                for (BoundSelection selection : prepared.selections) {
                    selection.member.preflight(context, directAccess,
                            selection.member.isStatic ? null : request.receiver);
                }
                invocations.add(invocation);
            } catch (NativeAccessFailure failure) {
                throw state.explain(failure, "$");
            }
        }
        List<Object> results = new ArrayList<>(invocations.size());
        if (many) {
            state.bytes(32);
        }
        for (int index = 0; index < invocations.size(); index++) {
            state.request = index + 1;
            Invocation invocation = invocations.get(index);
            results.add(record(prepared, invocation.request.receiver, invocation, state, "$", 1));
        }
        context.check();
        return many ? results : results.get(0);
    }

    private Prepared prepared(NativeDataPlan plan, NativeContext context, boolean directAccess) {
        long observed = bindings.bindingRevision();
        Map<NativeDataPlan, Prepared> cache = directAccess ? direct : managed;
        Prepared found = revision == observed ? cache.get(plan) : null;
        if (found != null) {
            return found;
        }
        context.prepare();
        if (revision != observed || managed.size() + direct.size() >= 64
                || plan.nodes > NativeDataPlan.MAXIMUM_WORK - cachedNodes) {
            clear();
        }
        Prepared result = compile(plan, context, directAccess, new IdentityHashMap<>(), "$");
        // Type resolution may have discovered a defining loader while preparing this
        // plan.
        if (revision != bindings.bindingRevision()) {
            clear();
        }
        revision = bindings.bindingRevision();
        cache.put(plan, result);
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
        Prepared result = new Prepared(plan, expected);
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

    private void preflight(Prepared prepared, Invocation invocation, State state, Map<Prepared, Boolean> seen,
            String prefix) {
        if (seen.put(prepared, Boolean.TRUE) != null) {
            return;
        }
        for (BoundSelection selection : prepared.selections) {
            String path = path(prefix, selection.definition.key);
            try {
                state.work();
                selection.member.preflightPolicy(state.context, state.directAccess);
                List<Argument> arguments = selection.definition.arguments;
                Object[] input = new Object[arguments.size()];
                for (int index = 0; index < input.length; index++) {
                    state.work();
                    Argument argument = arguments.get(index);
                    if (argument.input != null && !invocation.request.inputs.containsKey(argument.input)) {
                        throw new NativeAccessFailure("native_data_input_missing", argument.input);
                    }
                    input[index] = argument.input == null
                            ? argument.literal
                            : invocation.request.inputs.get(argument.input);
                }
                if (selection.definition.member.kind == NativeMemberRef.Kind.METHOD) {
                    invocation.arguments.put(selection, bindings.arguments(selection.member, input, state.context));
                }
                if (selection.first != null) {
                    preflight(selection.first, invocation, state, seen, path);
                }
                if (selection.second != null) {
                    preflight(selection.second, invocation, state, seen, path);
                }
            } catch (NativeAccessFailure failure) {
                throw state.explain(selectedFailure(failure, selection, state), path);
            }
        }
    }

    private Map<String, Object> record(Prepared prepared, Object receiver, Invocation invocation, State state,
            String path, int depth) {
        state.enter(receiver, depth);
        int previousLimit = state.ceiling;
        state.ceiling = Math.min(previousLimit, state.usedBytes + prepared.plan.maximumBytes);
        try {
            prepared.receiver(receiver);
            state.bytes(32);
            Map<String, Object> result = new LinkedHashMap<>();
            for (BoundSelection selection : prepared.selections) {
                String selectedPath = path(path, selection.definition.key);
                try {
                    state.work();
                    state.text(selection.definition.key);
                    NativeMemberPlan member = selection.member;
                    Object value = selection.definition.member.kind == NativeMemberRef.Kind.FIELD
                            ? member.get(state.context, state.directAccess, member.isStatic ? null : receiver)
                            : member.callConverted(state.context, state.directAccess, member.isStatic ? null : receiver,
                                    invocation.arguments.get(selection));
                    result.put(selection.definition.key,
                            project(value, selection, invocation, state, selectedPath, depth + 1));
                } catch (NativeAccessFailure failure) {
                    throw state.explain(selectedFailure(failure, selection, state), selectedPath);
                }
            }
            return result;
        } finally {
            state.ceiling = previousLimit;
            state.leave(receiver);
        }
    }

    private Object project(Object value, BoundSelection selection, Invocation invocation, State state, String path,
            int depth) {
        state.work();
        if (value == null) {
            state.bytes(8);
            return NativeExactValue.NULL;
        }
        Projection.Kind kind = selection.definition.projection.kind;
        Slice slice = selection.definition.slice;
        if (kind == Projection.Kind.OBJECT) {
            if (slice != null) {
                throw new NativeAccessFailure("invalid_data_projection", "object selections do not accept slices");
            }
            return record(selection.first, value, invocation, state, path, depth);
        }
        if (kind == Projection.Kind.MAP) {
            if (!(value instanceof Map) || slice != null) {
                throw new NativeAccessFailure("invalid_data_projection",
                        "map selection requires a Map without a slice");
            }
            return map((Map<?, ?>) value, selection, invocation, state, path, depth);
        }
        if (kind == Projection.Kind.EACH
                || value.getClass().isArray() && value.getClass().getComponentType().isPrimitive()) {
            if (!value.getClass().isArray() && !(value instanceof List)) {
                throw new NativeAccessFailure("invalid_data_projection", "each requires an array or List");
            }
            return sequence(value, selection, invocation, state, path, depth);
        }
        if (slice != null) {
            throw new NativeAccessFailure("invalid_data_projection", "a scalar cannot have a slice");
        }
        return scalar(value, state);
    }

    private Object sequence(Object value, BoundSelection selection, Invocation invocation, State state, String path,
            int depth) {
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
            List<Object> result = new ArrayList<>(count);
            ListIterator<?> iterator = value instanceof List
                    ? state.external(() -> ((List<?>) value).listIterator(start))
                    : null;
            for (int index = 0; index < count; index++) {
                Object element = iterator == null ? Array.get(value, start + index) : state.external(iterator::next);
                String elementPath = path + "[" + (index + 1) + "]";
                result.add(selected(element, selection.first, invocation, state, elementPath, depth + 1));
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

    private Object map(Map<?, ?> value, BoundSelection selection, Invocation invocation, State state, String path,
            int depth) {
        state.enter(value, depth);
        try {
            state.context.require(NativeContainerPolicy.effect(value, false), state.directAccess);
            int length = state.external(value::size);
            if (length < 0) {
                throw new NativeAccessFailure("native_data_collection_changed", "negative map size");
            }
            state.work(length);
            state.bytes(32);
            List<Object> result = new ArrayList<>(length);
            Iterator<? extends Map.Entry<?, ?>> iterator = state.external(() -> value.entrySet().iterator());
            while (state.external(iterator::hasNext)) {
                if (result.size() >= length) {
                    throw new NativeAccessFailure("native_data_collection_changed", "map grew during projection");
                }
                Map.Entry<?, ?> entry = state.external(iterator::next);
                Object key = state.external(entry::getKey);
                Object element = state.external(entry::getValue);
                String entryPath = path + "[" + (result.size() + 1) + "]";
                state.bytes(32);
                Map<String, Object> copy = new LinkedHashMap<>();
                copy.put("key", selected(key, selection.first, invocation, state, entryPath + ".key", depth + 1));
                copy.put("value",
                        selected(element, selection.second, invocation, state, entryPath + ".value", depth + 1));
                result.add(copy);
            }
            if (result.size() != length || state.external(value::size) != length) {
                throw new NativeAccessFailure("native_data_collection_changed", "map changed during projection");
            }
            return result;
        } finally {
            state.leave(value);
        }
    }

    private Object selected(Object value, Prepared nested, Invocation invocation, State state, String path, int depth) {
        state.work();
        try {
            return nested == null || value == null
                    ? scalar(value, state)
                    : record(nested, value, invocation, state, path, depth);
        } catch (NativeAccessFailure failure) {
            throw state.explain(failure, path);
        }
    }

    private Object scalar(Object value, State state) {
        if (value == null) {
            state.bytes(8);
            return NativeExactValue.NULL;
        }
        if (value instanceof String) {
            state.text((String) value);
            return value;
        }
        if (value instanceof Boolean || value instanceof Character || value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long || value instanceof Float
                || value instanceof Double) {
            state.bytes(16);
            return value;
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
        private final List<BoundSelection> selections = new ArrayList<>();
        private boolean instance;

        private Prepared(NativeDataPlan plan, Class<?> expected) {
            this.plan = plan;
            this.expected = expected;
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

        private BoundSelection(Selection definition, NativeMemberPlan member, Prepared first, Prepared second) {
            this.definition = definition;
            this.member = member;
            this.first = first;
            this.second = second;
        }
    }

    private static final class Invocation {
        private final Request request;
        private final Map<BoundSelection, Object[]> arguments = new IdentityHashMap<>();

        private Invocation(Request request) {
            this.request = request;
        }
    }

    private static final class State {
        private final NativeContext context;
        private final boolean directAccess;
        private final Map<Object, Boolean> ancestors = new IdentityHashMap<>();
        private int ceiling;
        private int usedBytes;
        private int request;

        private State(NativeContext context, boolean directAccess, int maximumBytes) {
            this.context = context;
            this.directAccess = directAccess;
            ceiling = maximumBytes;
        }

        private void work() {
            work(1);
        }

        private void work(int count) {
            context.check();
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
            // Charge directly without encoding a possibly huge string into a temporary byte
            // array.
            for (int index = 0; index < value.length(); index++) {
                char unit = value.charAt(index);
                if (Character.isHighSurrogate(unit) && index + 1 < value.length()
                        && Character.isLowSurrogate(value.charAt(index + 1))) {
                    bytes(4);
                    index++;
                } else {
                    // Lone UTF-16 units are preserved by the existing exact-string representation.
                    bytes(unit < 0x80 ? 1 : unit < 0x800 ? 2 : 3);
                }
            }
        }

        private void enter(Object value, int depth) {
            work();
            if (depth > NativeDataPlan.MAXIMUM_DEPTH) {
                throw new NativeAccessFailure("native_data_depth_limit", "projection exceeds depth 8");
            }
            if (value != null && ancestors.put(value, Boolean.TRUE) != null) {
                throw new NativeAccessFailure("native_data_cycle", "selection revisits an ancestor object");
            }
        }

        private void leave(Object value) {
            if (value != null) {
                ancestors.remove(value);
            }
        }

        private <T> T external(Supplier<T> operation) {
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

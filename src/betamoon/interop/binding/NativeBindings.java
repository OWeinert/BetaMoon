package betamoon.interop.binding;

import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.cache.NativeLookupCache;
import betamoon.interop.catalog.JvmDescriptors;
import betamoon.interop.client.NativeClientSessions;
import betamoon.interop.discovery.NativeIndexSnapshot;
import betamoon.interop.discovery.NativeLoaderRegistry;
import betamoon.interop.interfaces.NativeInterfaces;
import betamoon.interop.hosts.NativeHostShapes;
import betamoon.interop.query.NativeDataQueries;
import betamoon.interop.reference.NativeMemberRef;
import betamoon.interop.reference.NativeTypeRef;
import betamoon.interop.runtime.NativeContext;
import betamoon.interop.runtime.NativeOwnedValues;
import betamoon.interop.value.NativeConversions;
import betamoon.interop.value.NativeExactValue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.objectweb.asm.Type;

/**
 * General binder for Minecraft and arbitrary mod classes. No invocation trial
 * is used to select overloads.
 */
public final class NativeBindings {
    public final NativeNames names;
    public final NativeClientSessions clients = new NativeClientSessions(this);
    public final NativeDataQueries dataQueries = new NativeDataQueries(this);
    public final NativeOwnedValues owned = new NativeOwnedValues(this);
    public final NativeInterfaces interfaces = new NativeInterfaces(this);
    public final NativeHostShapes hostShapes = new NativeHostShapes(this);
    private final NativeLoaderRegistry loaders;
    private final Thread client = Thread.currentThread();
    private final Supplier<NativeIndexSnapshot> snapshots;
    private volatile boolean closed;
    private volatile int cachedTypeSelectors;
    private final Map<String, Long> failures = new HashMap<>();
    private final NativeFailureJournal failureJournal = new NativeFailureJournal();
    private final Map<String, WeakReference<Class<?>>> types = new NativeLookupCache<>(1024);
    private long typeRevision = -1;
    private final ClassValue<ReferenceCache> references = new ClassValue<ReferenceCache>() {
        @Override
        protected ReferenceCache computeValue(Class<?> type) {
            return new ReferenceCache();
        }
    };

    private static final class ReferenceCache {
        private long revision = -1;
        private NativeTypeRef reference;
    }

    private final ClassValue<RevisionCache<NativeMemberPlan>> plans = new ClassValue<RevisionCache<NativeMemberPlan>>() {
        @Override
        protected RevisionCache<NativeMemberPlan> computeValue(Class<?> type) {
            return new RevisionCache<>();
        }
    };
    private final ClassValue<RevisionCache<List<NativeMemberPlan>>> methodSets = new ClassValue<RevisionCache<List<NativeMemberPlan>>>() {
        @Override
        protected RevisionCache<List<NativeMemberPlan>> computeValue(Class<?> type) {
            return new RevisionCache<>();
        }
    };
    private final ClassValue<RevisionCache<NativeOrdinaryMember>> ordinaryMembers = new ClassValue<RevisionCache<NativeOrdinaryMember>>() {
        @Override
        protected RevisionCache<NativeOrdinaryMember> computeValue(Class<?> type) {
            return new RevisionCache<>();
        }
    };

    private static final class RevisionCache<V> {
        private final Map<String, V> values = new NativeLookupCache<>(512);
        private long revision = -1;
        private Map<String, V> at(long current) {
            if (revision != current) {
                values.clear();
                revision = current;
            }
            return values;
        }
    }

    public NativeBindings(NativeNames names, NativeLoaderRegistry loaders) {
        this(names, loaders, NativeIndexSnapshot::empty);
    }

    public NativeBindings(NativeNames names, NativeLoaderRegistry loaders, Supplier<NativeIndexSnapshot> snapshots) {
        this.names = names;
        this.loaders = loaders;
        this.snapshots = snapshots;
    }

    public long bindingRevision() {
        requireThread();
        return loaders.bindingRevision();
    }

    public NativeIndexSnapshot snapshot() {
        return snapshots.get();
    }

    public void recordFailure(String reason) {
        recordFailure(new NativeAccessFailure(reason, null));
    }

    public void recordFailure(NativeAccessFailure failure) {
        synchronized (failures) {
            String reason = failure.reason;
            if (failures.containsKey(reason) || failures.size() < 128) {
                failures.put(reason, failures.getOrDefault(reason, 0L) + 1L);
                failureJournal.record(failure);
            }
        }
    }

    public NativeAccessFailure explain(NativeAccessFailure failure, NativeTypeRef type, NativeMemberRef member,
            NativeMemberPlan plan, NativeContext context, boolean direct) {
        NativeIndexSnapshot snapshot = snapshot();
        Map<String, Object> details = CatalogValues.record("namespace", snapshot.namespace, "mappingSha256",
                snapshot.mappingSha256, "generation", snapshot.generation, "policy", direct ? "direct" : "managed");
        if (context != null) {
            details.putAll(context.failureContext());
        }
        if (type != null) {
            details.put("requestedType", type.name());
            details.put("requestedNames", type.names);
            details.put("requestedLoaderId", type.loaderId);
            details.put("requestedOriginId", type.originId);
        }
        if (member != null) {
            details.put("requestedMember", member.name);
            details.put("requestedDescriptor", member.descriptor);
            details.put("requestedDeclaringType", member.declaringType == null ? null : member.declaringType.name());
            details.put("requestedDescriptorNames", member.descriptorNames);
        }
        if (plan != null) {
            Class<?> owner = plan.member.getDeclaringClass();
            details.put("runtimeOwner", owner.getName());
            details.put("runtimeMember", plan.member.getName());
            details.put("runtimeDescriptor", plan.descriptor);
            if (onClientThread()) {
                details.put("definingLoaderId", loaders.id(owner.getClassLoader()));
                details.put("originId", loaders.originId(owner));
            }
        }
        if (type != null && member != null) {
            List<Map<String, Object>> alternatives = new ArrayList<>();
            List<Map<String, Object>> rows = snapshot.rows("members");
            String owner = member.declaringType == null ? type.name() : member.declaringType.name();
            int low = 0;
            int high = rows.size();
            while (low < high) {
                int middle = (low + high) >>> 1;
                // Snapshot keys append ':' after the owner; '$' nested names sort before that
                // separator.
                if ((String.valueOf(rows.get(middle).get("declaringType")) + ":").compareTo(owner + ":") < 0) {
                    low = middle + 1;
                } else {
                    high = middle;
                }
            }
            for (int index = low; index < rows.size() && index < low + 256 && alternatives.size() < 8; index++) {
                Map<String, Object> row = rows.get(index);
                if (!owner.equals(row.get("declaringType"))) {
                    break;
                }
                if (member.name.equals(row.get("name"))) {
                    alternatives.add(CatalogValues.record("name", row.get("name"), "descriptor", row.get("descriptor"),
                            "originId", row.get("originId"), "access", row.get("access")));
                }
            }
            details.put("availableOverloads", alternatives);
            details.put("alternativesBounded", true);
        }
        return failure.explain(details);
    }

    public Map<String, Object> diagnostics() {
        synchronized (failures) {
            return CatalogValues.record("session", closed ? "closed" : "active", "cachedTypeSelectors",
                    cachedTypeSelectors, "failures", new HashMap<>(failures), "failureDetails",
                    failureJournal.snapshot(), "ownership", owned.diagnostics(), "interfaces", interfaces.diagnostics(),
                    "client", clients.diagnostics(), "hostShapes", hostShapes.preparedCount());
        }
    }

    public void requireThread() {
        if (!onClientThread()) {
            throw new NativeAccessFailure("wrong_thread", "native binding runs on the client thread");
        }
        if (closed) {
            throw new NativeAccessFailure("native_session_closed", "native session ended");
        }
    }

    public boolean onClientThread() {
        return Thread.currentThread() == client;
    }

    public void close() {
        requireThread();
        clients.requireCloseBoundary();
        try {
            clients.close();
            interfaces.close();
            owned.close();
        } finally {
            closed = true;
            dataQueries.clear();
            hostShapes.close();
            types.clear();
            cachedTypeSelectors = 0;
        }
    }

    public boolean borrowedRootType(Class<?> type) {
        requireThread();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            String name = names.canonicalClass(current);
            if (name.equals("net/minecraft/src/World") || name.equals("net/minecraft/src/Entity")
                    || name.equals("net/minecraft/src/TileEntity") || name.equals("net/minecraft/src/GuiScreen")
                    || name.equals("net/minecraft/client/Minecraft")) {
                return true;
            }
        }
        return false;
    }

    public NativeTypeRef reference(Class<?> type) {
        requireThread();
        ReferenceCache cached = references.get(type);
        long revision = loaders.bindingRevision();
        if (cached.reference != null && cached.revision == revision) {
            return cached.reference;
        }
        NativeTypeRef reference = type.isPrimitive()
                ? NativeTypeRef.named(type.getName(), "runtime", null, null)
                : NativeTypeRef.descriptor(JvmDescriptors.remap(Type.getDescriptor(type), names::canonicalName), "auto")
                        .selected(null, loaders.id(type.getClassLoader()), loaders.originId(type));
        cached.reference = reference;
        cached.revision = loaders.bindingRevision();
        return reference;
    }

    public Class<?> type(NativeTypeRef reference, NativeContext context) {
        try {
            return resolveType(reference, context);
        } catch (NativeAccessFailure failure) {
            throw explain(failure, reference, null, null, context, false);
        }
    }

    private Class<?> resolveType(NativeTypeRef reference, NativeContext context) {
        context.check();
        long observedRevision = loaders.bindingRevision();
        if (typeRevision != observedRevision) {
            types.clear();
            typeRevision = observedRevision;
            cachedTypeSelectors = 0;
        }
        String key = reference.key();
        WeakReference<Class<?>> cached = types.get(key);
        Class<?> result = cached == null ? null : cached.get();
        if (result != null) {
            return result;
        }
        Class<?> primitive = NativeConversions.primitive(reference.name());
        if (primitive != null) {
            return primitive;
        }
        context.prepare();
        String descriptor = names.descriptor(reference.descriptor, reference.names);
        String binary = descriptor.startsWith("[")
                ? descriptor.replace('/', '.')
                : descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
        List<ClassLoader> routes = reference.loaderId == null
                ? loaders.bindingLoaders()
                : Collections.singletonList(loaders.bindingLoader(reference.loaderId));
        if (reference.loaderId == null) {
            routes.add(null);
        }
        Set<Class<?>> candidates = Collections.newSetFromMap(new IdentityHashMap<>());
        LinkageError dependency = null;
        for (ClassLoader route : routes) {
            try {
                Class<?> candidate = Class.forName(binary, false, route);
                if (reference.loaderId != null && !reference.loaderId.equals(loaders.id(candidate.getClassLoader()))) {
                    continue;
                }
                if (reference.originId != null && !reference.originId.equals(loaders.originId(candidate))) {
                    continue;
                }
                candidates.add(candidate);
            } catch (ClassNotFoundException exception) {
                // Try other known routes; the same parent-defined Class is deduplicated by
                // identity.
            } catch (LinkageError exception) {
                dependency = exception;
            } catch (SecurityException exception) {
                throw new NativeAccessFailure("access_denied", reference.name(), exception);
            }
        }
        if (candidates.size() > 1) {
            throw new NativeAccessFailure("ambiguous_loader", reference.name() + "; select loaderId/originId");
        }
        if (candidates.isEmpty()) {
            throw dependency == null
                    ? new NativeAccessFailure("class_not_found", reference.name())
                    : new NativeAccessFailure("missing_dependency", reference.name(), dependency);
        }
        result = candidates.iterator().next();
        if (typeRevision != loaders.bindingRevision()) {
            types.clear();
            typeRevision = loaders.bindingRevision();
        }
        types.put(key, new WeakReference<>(result));
        cachedTypeSelectors = types.size();
        return result;
    }

    public NativeMemberPlan exact(NativeMemberRef reference, NativeContext context, boolean direct) {
        try {
            return resolveExact(reference, context, direct);
        } catch (NativeAccessFailure failure) {
            throw explain(failure, reference.lookupType, reference, null, context, direct);
        }
    }

    private NativeMemberPlan resolveExact(NativeMemberRef reference, NativeContext context, boolean direct) {
        Class<?> lookup = type(reference.lookupType, context);
        Map<String, NativeMemberPlan> cache = plans.get(lookup).at(loaders.bindingRevision());
        String key = reference.bindingKey(direct);
        NativeMemberPlan previous = cache.get(key);
        if (previous != null) {
            return previous;
        }
        context.prepare();
        Class<?> declaring = reference.declaringType == null ? null : type(reference.declaringType, context);
        if (declaring != null && !declaring.isAssignableFrom(lookup)) {
            throw new NativeAccessFailure("wrong_declaring_owner", reference.declaringType.name());
        }
        String descriptor = reference.descriptor == null
                ? null
                : names.descriptor(reference.descriptor, reference.descriptorNames);
        List<NativeMemberPlan> found = new ArrayList<>();
        try {
            if (reference.kind == NativeMemberRef.Kind.CONSTRUCTOR) {
                for (Constructor<?> constructor : lookup.getDeclaredConstructors()) {
                    if (Type.getConstructorDescriptor(constructor).equals(descriptor)) {
                        found.add(new NativeMemberPlan(constructor, names));
                    }
                }
            } else {
                for (Class<?> owner : declaring == null ? hierarchy(lookup) : Collections.singletonList(declaring)) {
                    if (reference.kind == NativeMemberRef.Kind.METHOD) {
                        for (Method method : owner.getDeclaredMethods()) {
                            if (!(method.isSynthetic() && (method.getName().startsWith("betamoon$hook$body$")
                                    || method.getName().startsWith("betamoon$host$")))
                                    && Type.getMethodDescriptor(method).equals(descriptor)
                                    && names.methodMatches(method, reference.name, reference.names)) {
                                if (!found.isEmpty() && declaring == null && !Modifier.isPrivate(method.getModifiers())
                                        && method.getDeclaringClass()
                                                .isAssignableFrom(found.get(0).member.getDeclaringClass())) {
                                    continue;
                                }
                                found.add(new NativeMemberPlan(method, names));
                            }
                        }
                    } else {
                        for (Field field : owner.getDeclaredFields()) {
                            String actual = Type.getDescriptor(field.getType());
                            if ((descriptor == null || descriptor.equals(actual)) && names.fieldMatches(owner,
                                    field.getName(), actual, reference.name, reference.names)) {
                                found.add(new NativeMemberPlan(field, names));
                            }
                        }
                    }
                }
            }
        } catch (LinkageError exception) {
            throw new NativeAccessFailure("missing_dependency", reference.key(), exception);
        } catch (SecurityException exception) {
            throw new NativeAccessFailure("access_denied", reference.key(), exception);
        }
        if (found.size() != 1) {
            throw new NativeAccessFailure(found.isEmpty() ? "member_not_found" : "ambiguous_member", reference.key());
        }
        NativeMemberPlan selected = found.get(0);
        if (selected.nonPublic && !direct) {
            throw new NativeAccessFailure("nonpublic_requires_direct", selected.identity());
        }
        selected.prepareExecution(context, direct);
        cache.put(key, selected);
        return selected;
    }

    public NativeMemberPlan method(NativeTypeRef reference, String name, Object receiver, Object[] arguments,
            NativeContext context) {
        try {
            return overload(methods(reference, name, receiver == null, context), arguments, context, reference.name(),
                    name);
        } catch (NativeAccessFailure failure) {
            failure.explain(CatalogValues.record("requestedMember", name));
            throw explain(failure, reference, null, null, context, false);
        }
    }

    public NativeOrdinaryMember ordinary(NativeTypeRef reference, String name, boolean isStatic,
            NativeContext context) {
        if (name == null || name.isEmpty() || name.length() > 1024) {
            throw new IllegalArgumentException("invalid_member_name");
        }
        Class<?> lookup = type(reference, context);
        Map<String, NativeOrdinaryMember> cache = ordinaryMembers.get(lookup).at(loaders.bindingRevision());
        String key = reference.names + ":" + name + ":" + isStatic;
        NativeOrdinaryMember found = cache.get(key);
        if (found != null) {
            return found.requireUnambiguousFields(name);
        }
        context.prepare();
        NativeMemberRef.requireName(name);
        NativeMemberPlan field = null;
        int fields = 0;
        try {
            for (Class<?> owner : hierarchy(lookup)) {
                for (Field candidate : owner.getDeclaredFields()) {
                    if (names.fieldMatches(owner, candidate.getName(), Type.getDescriptor(candidate.getType()), name,
                            reference.names)) {
                        fields++;
                        field = new NativeMemberPlan(candidate, names);
                    }
                }
            }
        } catch (LinkageError failure) {
            throw new NativeAccessFailure("missing_dependency", reference.name(), failure);
        } catch (SecurityException failure) {
            throw new NativeAccessFailure("access_denied", reference.name(), failure);
        }
        boolean method = fields <= 1 && !methods(reference, name, isStatic, context).isEmpty();
        found = new NativeOrdinaryMember(field != null && !field.nonPublic && field.isStatic == isStatic ? field : null,
                method, fields > 1);
        if (found.field != null) {
            found.field.prepareExecution(context, false);
        }
        cache.put(key, found);
        return found.requireUnambiguousFields(name);
    }

    public boolean hasMethod(NativeTypeRef reference, String name, boolean isStatic, NativeContext context) {
        return !methods(reference, name, isStatic, context).isEmpty();
    }

    private List<NativeMemberPlan> methods(NativeTypeRef reference, String name, boolean isStatic,
            NativeContext context) {
        Class<?> lookup = type(reference, context);
        String cacheKey = name + ":" + reference.names + ":" + isStatic;
        Map<String, List<NativeMemberPlan>> cache = methodSets.get(lookup).at(loaders.bindingRevision());
        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }
        context.prepare();
        List<NativeMemberPlan> candidates = new ArrayList<>();
        Map<String, Class<?>> signatures = new HashMap<>();
        try {
            for (Class<?> owner : hierarchy(lookup)) {
                for (Method method : owner.getDeclaredMethods()) {
                    if (!Modifier.isPublic(method.getModifiers()) || !Modifier.isPublic(owner.getModifiers())
                            || method.isSynthetic() || method.isBridge()
                            || Modifier.isStatic(method.getModifiers()) != isStatic
                            || !names.methodMatches(method, name, reference.names)) {
                        continue;
                    }
                    String descriptor = Type.getMethodDescriptor(method);
                    String key = method.getName() + descriptor.substring(0, descriptor.indexOf(')') + 1);
                    Class<?> previousOwner = signatures.putIfAbsent(key, owner);
                    if (previousOwner == null || previousOwner == owner) {
                        candidates.add(new NativeMemberPlan(method, names));
                    }
                }
            }
        } catch (LinkageError exception) {
            throw new NativeAccessFailure("missing_dependency", reference.name() + "." + name, exception);
        } catch (SecurityException exception) {
            throw new NativeAccessFailure("access_denied", reference.name() + "." + name, exception);
        }
        candidates.sort(Comparator.comparing(NativeMemberPlan::identity));
        List<NativeMemberPlan> result = Collections.unmodifiableList(candidates);
        for (NativeMemberPlan candidate : candidates) {
            candidate.prepareExecution(context, false);
        }
        cache.put(cacheKey, result);
        return result;
    }

    public NativeMemberPlan constructor(Class<?> type, Object[] arguments, NativeContext context) {
        Map<String, List<NativeMemberPlan>> cache = methodSets.get(type).at(loaders.bindingRevision());
        List<NativeMemberPlan> candidates = cache.get("<constructors>");
        if (candidates == null) {
            context.prepare();
            candidates = new ArrayList<>();
            try {
                for (Constructor<?> constructor : type.getConstructors()) {
                    if (Modifier.isPublic(type.getModifiers())) {
                        candidates.add(new NativeMemberPlan(constructor, names));
                    }
                }
            } catch (LinkageError exception) {
                throw new NativeAccessFailure("missing_dependency", type.getName(), exception);
            } catch (SecurityException exception) {
                throw new NativeAccessFailure("access_denied", type.getName(), exception);
            }
            for (NativeMemberPlan candidate : candidates) {
                candidate.prepareExecution(context, false);
            }
            candidates = Collections.unmodifiableList(candidates);
            cache.put("<constructors>", candidates);
        }
        return overload(candidates, arguments, context, type.getName(), "new");
    }

    public Object[] arguments(NativeMemberPlan plan, Object[] arguments, NativeContext context) {
        if (plan.parameters.length != arguments.length) {
            throw new NativeAccessFailure("argument_count", plan.identity());
        }
        return argumentsOwned(plan, arguments.clone(), context);
    }

    /**
     * Internal transfer of a private argument buffer; never use with borrowed
     * callback/native arrays.
     */
    public Object[] argumentsOwned(NativeMemberPlan plan, Object[] arguments, NativeContext context) {
        if (plan.parameters.length != arguments.length) {
            throw new NativeAccessFailure("argument_count", plan.identity());
        }
        for (int i = 0; i < arguments.length; i++) {
            validateNull(arguments[i], plan.parameters[i], context);
            arguments[i] = NativeConversions.convert(arguments[i], plan.parameters[i]);
        }
        return arguments;
    }

    public void validateNull(Object value, Class<?> target, NativeContext context) {
        if (value instanceof NativeExactValue) {
            NativeExactValue exact = (NativeExactValue) value;
            if (exact.value == null && exact.type != null && !target.isAssignableFrom(type(exact.type, context))) {
                throw new NativeAccessFailure("conversion_failed",
                        "typed null is not assignable to " + target.getName());
            }
        }
    }

    private NativeMemberPlan overload(List<NativeMemberPlan> candidates, Object[] arguments, NativeContext context,
            String selectorType, String selectorName) {
        NativeMemberPlan best = null;
        int bestCost = Integer.MAX_VALUE;
        List<String> ties = null;
        for (NativeMemberPlan candidate : candidates) {
            if (candidate.parameters.length != arguments.length) {
                continue;
            }
            int total = 0;
            for (int i = 0; i < arguments.length; i++) {
                int cost;
                try {
                    validateNull(arguments[i], candidate.parameters[i], context);
                    cost = NativeConversions.cost(arguments[i], candidate.parameters[i]);
                    if (arguments[i] instanceof NativeExactValue) {
                        NativeExactValue exact = (NativeExactValue) arguments[i];
                        if (exact.value == null && exact.type != null) {
                            cost = NativeConversions.distance(type(exact.type, context), candidate.parameters[i]);
                        }
                    }
                } catch (NativeAccessFailure exception) {
                    cost = -1;
                }
                if (cost < 0) {
                    total = -1;
                    break;
                }
                total += cost;
            }
            if (total >= 0 && total < bestCost) {
                best = candidate;
                bestCost = total;
                ties = null;
            } else if (total >= 0 && total == bestCost) {
                if (ties == null) {
                    ties = new ArrayList<>();
                    ties.add(best.identity());
                }
                ties.add(candidate.identity());
            }
        }
        if (best == null || ties != null) {
            if (ties == null) {
                ties = Collections.emptyList();
            } else {
                Collections.sort(ties);
            }
            throw new NativeAccessFailure(best == null ? "no_matching_overload" : "ambiguous_overload",
                    selectorType + "." + selectorName + "; candidates " + ties);
        }
        return best;
    }

    private static List<Class<?>> hierarchy(Class<?> start) {
        List<Class<?>> result = new ArrayList<>();
        Deque<Class<?>> pending = new ArrayDeque<>();
        Set<Class<?>> visited = new HashSet<>();
        pending.add(start);
        while (!pending.isEmpty()) {
            Class<?> type = pending.removeFirst();
            if (!visited.add(type)) {
                continue;
            }
            result.add(type);
            if (type.getSuperclass() != null) {
                pending.add(type.getSuperclass());
            }
            List<Class<?>> interfaces = new ArrayList<>();
            Collections.addAll(interfaces, type.getInterfaces());
            interfaces.sort(Comparator.comparing(Class::getName));
            pending.addAll(interfaces);
        }
        return result;
    }
}

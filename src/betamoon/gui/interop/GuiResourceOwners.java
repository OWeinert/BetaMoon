package betamoon.gui.interop;

import betamoon.gui.framework.GuiPreparationPhase;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * One UI resource revision shares prepared owner resources across static
 * content and temporary row leases.
 */
public final class GuiResourceOwners<T extends AutoCloseable> implements AutoCloseable {
    private final Function<String, T> factory;
    private final ToLongFunction<String> generation;
    private final Consumer<String> released;
    private final Consumer<Throwable> failures;
    private final Map<String, Entry> owners = new LinkedHashMap<>();
    private final Set<Lease> leases = new HashSet<>();
    private Map<String, T> published = Collections.emptyMap();
    private boolean acquiring;
    private boolean closed;

    public GuiResourceOwners(Function<String, T> factory, ToLongFunction<String> generation, Consumer<String> released,
            Consumer<Throwable> failures) {
        this.factory = Objects.requireNonNull(factory, "UI resource preparation");
        this.generation = Objects.requireNonNull(generation, "resource owner generation");
        this.released = Objects.requireNonNull(released, "resource cache retirement");
        this.failures = Objects.requireNonNull(failures, "resource cleanup reporter");
    }

    public Lease acquire(Collection<String> requested) {
        requireOpen();
        requireIdle();
        if (requested == null || requested.size() > 32) {
            throw new IllegalArgumentException("UI resource leases support at most 32 owners");
        }
        Set<String> keys = new LinkedHashSet<>(requested);
        for (String owner : keys) {
            if (owner == null || owner.isEmpty()) {
                throw new IllegalArgumentException("UI resource owner must be named");
            }
        }
        Set<String> combined = new HashSet<>(owners.keySet());
        combined.addAll(keys);
        if (combined.size() > 32) {
            throw new IllegalArgumentException("UI resource revision exceeds 32 live or candidate owners");
        }
        Map<String, Entry> selected = new LinkedHashMap<>();
        Lease lease = null;
        acquiring = true;
        try (GuiPreparationPhase preparation = GuiPreparationPhase.open()) {
            for (String owner : keys) {
                Entry entry = owners.get(owner);
                if (entry == null) {
                    long version = generation.applyAsLong(owner);
                    T resource = Objects.requireNonNull(factory.apply(owner), "prepared UI owner resources");
                    entry = new Entry(owner, version, resource);
                    selected.put(owner, entry);
                    entry.references = 1;
                    owners.put(owner, entry);
                } else {
                    selected.put(owner, entry);
                    entry.references++;
                }
                requireCurrent(entry);
            }
            for (Entry entry : selected.values()) {
                requireCurrent(entry);
            }
            lease = new Lease(selected);
            leases.add(lease);
            publish();
            return lease;
        } catch (RuntimeException | Error failure) {
            if (lease != null) {
                leases.remove(lease);
                lease.closed = true;
                lease.members.clear();
            }
            Throwable cleanup = release(selected.values());
            publish();
            if (cleanup != null) {
                Throwable combinedFailure = collect(failure, cleanup);
                if (combinedFailure != failure) {
                    throw (Error) combinedFailure;
                }
            }
            throw failure;
        } finally {
            acquiring = false;
        }
    }

    /**
     * Engine-only resource index; native resources retain their own independent
     * use/lifetime checks.
     */
    public Map<String, T> snapshot() {
        return published;
    }

    public T required(String owner) {
        requireOpen();
        Entry entry = owners.get(owner);
        if (entry == null) {
            throw new IllegalStateException("Rendering resources were not leased for " + owner);
        }
        requireCurrent(entry);
        return entry.resource;
    }

    private void requireCurrent(Entry entry) {
        if (entry.references < 1 || owners.get(entry.owner) != entry
                || generation.applyAsLong(entry.owner) != entry.generation) {
            throw new IllegalStateException("Prepared UI resource owner has expired: " + entry.owner);
        }
    }

    private void publish() {
        Map<String, T> values = new LinkedHashMap<>();
        for (Entry entry : owners.values()) {
            values.put(entry.owner, entry.resource);
        }
        published = Collections.unmodifiableMap(values);
    }

    private Throwable release(Collection<Entry> entries) {
        Throwable cleanup = null;
        for (Entry entry : entries) {
            if (--entry.references == 0) {
                owners.remove(entry.owner);
                try {
                    released.accept(entry.owner);
                } catch (RuntimeException | Error failure) {
                    cleanup = collect(cleanup, failure);
                }
                try {
                    entry.resource.close();
                } catch (Exception | Error failure) {
                    cleanup = collect(cleanup, failure);
                }
            }
        }
        return cleanup;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("UI resource revision is closed");
        }
    }

    private void requireIdle() {
        if (acquiring) {
            throw new IllegalStateException("UI resource preparation cannot reenter its owner leases");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        requireIdle();
        closed = true;
        for (Lease lease : leases) {
            lease.closed = true;
            lease.members.clear();
        }
        leases.clear();
        for (Entry entry : owners.values()) {
            entry.references = 1;
        }
        Throwable cleanup = release(new ArrayList<>(owners.values()));
        published = Collections.emptyMap();
        report(cleanup);
    }

    private static boolean fatal(Throwable failure) {
        return failure instanceof VirtualMachineError || failure instanceof ThreadDeath;
    }

    private static Throwable collect(Throwable previous, Throwable failure) {
        if (previous == null) {
            return failure;
        }
        if (fatal(failure) && !fatal(previous)) {
            return collect(failure, previous);
        }
        if (previous != failure && previous.getSuppressed().length < 16) {
            previous.addSuppressed(failure);
        }
        return previous;
    }

    private void report(Throwable failure) {
        if (fatal(failure)) {
            throw (Error) failure;
        }
        if (failure != null) {
            failures.accept(failure);
        }
    }

    private final class Entry {
        final String owner;
        final long generation;
        final T resource;
        int references;

        Entry(String owner, long generation, T resource) {
            this.owner = owner;
            this.generation = generation;
            this.resource = resource;
        }
    }

    public final class Lease implements AutoCloseable {
        private final Map<String, Entry> members;
        private boolean closed;

        private Lease(Map<String, Entry> members) {
            this.members = new LinkedHashMap<>(members);
        }

        public T resource(String owner) {
            requireOpen();
            Entry entry = members.get(owner);
            if (closed || entry == null) {
                throw new IllegalStateException("UI resources are outside this lease or its lifetime");
            }
            requireCurrent(entry);
            return entry.resource;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            requireIdle();
            closed = true;
            leases.remove(this);
            Collection<Entry> retiring = new ArrayList<>(members.values());
            members.clear();
            Throwable cleanup = release(retiring);
            publish();
            report(cleanup);
        }
    }
}

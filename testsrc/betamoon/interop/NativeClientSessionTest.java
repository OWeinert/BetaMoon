package betamoon.interop;

import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.binding.NativeBindings;
import betamoon.interop.binding.NativeNames;
import betamoon.interop.client.NativeClientAction;
import betamoon.interop.client.NativeClientActionPermit;
import betamoon.interop.client.NativeClientResource;
import betamoon.interop.client.NativeClientSession;
import betamoon.interop.discovery.NativeLoaderRegistry;
import betamoon.interop.runtime.NativeContext;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Real scopes/owners with deterministic Java resources; rendered font/texture
 * consumers have a separate gate.
 */
public final class NativeClientSessionTest {
    private static int checks;
    private final NativeBindings bindings;
    private final Map<String, Object> roots = new LinkedHashMap<>();
    private final AtomicInteger disposed = new AtomicInteger();
    private final AtomicInteger actions = new AtomicInteger();

    private NativeClientSessionTest() throws Exception {
        TinyMappingResolver mapping = TinyMappingResolver.readStrict(
                new ByteArrayInputStream("tiny\t2\t0\tnamed\tclient\tserver\n".getBytes(StandardCharsets.UTF_8)));
        bindings = new NativeBindings(new NativeNames(mapping.snapshot(), "named"), new NativeLoaderRegistry());
        roots.put("client", new Object());
        roots.put("screen", new Object());
        roots.put("world", null);
        roots.put("player", null);
    }

    public static void main(String[] args) throws Exception {
        NativeClientSessionTest test = new NativeClientSessionTest();
        test.basic();
        test.failures();
        test.limits();
        test.bindings.close();
        System.out.println("Native client lifecycles passed: " + checks
                + " checks; resources, deferred disposal, authorized actions and stale owner/environment refusal.");
    }

    private void basic() {
        NativeClientSession session = create("client-owner");
        AtomicReference<NativeClientResource<String>> resource = new AtomicReference<>();
        AtomicReference<NativeClientAction> action = new AtomicReference<>();
        AtomicReference<NativeClientAction> terminal = new AtomicReference<>();
        AtomicReference<NativeClientActionPermit> permit = new AtomicReference<>();
        session.prepare(() -> {
            resource.set(session.resource(() -> "prepared", this::dispose));
            action.set(session.action("counter", () -> {
                NativeContext.current().requireClientAction();
                expect("native_phase_denied",
                        () -> NativeContext.current().require(NativeContext.Effect.UNKNOWN, true));
                actions.incrementAndGet();
            }, false));
            terminal.set(session.action("navigate", () -> {
                roots.put("screen", new Object());
                NativeClientSession next = create("next-owner");
                next.prepare(() -> resource.set(next.resource(() -> "next", this::dispose)));
                next.close();
            }, true));
        });
        session.paint(() -> {
            require(resource.get().use().equals("prepared"), "prepared resource works in paint");
            expect("native_phase_denied", resource.get()::close);
            expect("native_phase_denied", () -> session.prepare(() -> {
                throw new AssertionError("forbidden initializer executed");
            }));
            expect("native_phase_denied", () -> create("paint-owner"));
            expect("native_phase_denied", bindings::close);
        });
        require(session.active(), "forbidden preparation/close did not invalidate a healthy host");
        session.input(authorization -> {
            permit.set(authorization);
            session.submit(action.get(), authorization);
        });
        require(actions.get() == 0, "input only queues the prepared command");
        session.paint(() -> {
            bindings.clients.observe();
            require(actions.get() == 0, "query cannot flush an authorized action with new privileges");
        });
        bindings.clients.observe();
        require(actions.get() == 1, "authorized action executes at the client boundary");
        session.input(
                current -> expect("native_client_action_denied", () -> session.submit(action.get(), permit.get())));
        ScriptExecution.withOwner("other-owner", () -> {
            expect("wrong_owner", () -> session.input(ignored -> {
                throw new AssertionError("foreign owner callback executed");
            }));
            return null;
        });
        session.input(current -> session.submit(terminal.get(), current));
        bindings.clients.observe();
        require(!session.active(), "navigation retires the old host");
        require(disposed.get() == 2, "old/new screen resources dispose after the navigation boundary");
        require(number("resourceSlots") == 0 && number("pendingActions") == 0, "navigation leaves no queued payload");
    }

    private void failures() {
        NativeClientSession initialization = create("initialization-owner");
        RuntimeException original = new IllegalStateException("initialization failure");
        try {
            initialization.prepare(() -> {
                initialization.resource(() -> "partial", this::dispose);
                throw original;
            });
            throw new AssertionError("initialization failure swallowed");
        } catch (RuntimeException failure) {
            require(failure == original, "initialization keeps the original failure");
        }
        require(!initialization.active(), "failed initialization retires all partially published resources");
        bindings.clients.observe();
        require(number("resourceSlots") == 0, "partial initialization cleanup drained");

        NativeClientSession assertion = create("assertion-owner");
        AssertionError assertionFailure = new AssertionError("initialization assertion");
        try {
            assertion.prepare(() -> {
                assertion.resource(() -> "assertion partial", this::dispose);
                throw assertionFailure;
            });
            throw new AssertionError("initialization assertion swallowed");
        } catch (AssertionError failure) {
            require(failure == assertionFailure, "nonfatal initialization error keeps its identity");
        }
        bindings.clients.observe();
        require(!assertion.active() && number("resourceSlots") == 0, "nonfatal error retires partial resources");

        boolean[] providerFails = {false};
        NativeClientSession brokenProvider = bindings.clients.create("provider-owner", () -> {
            if (providerFails[0]) {
                throw new IllegalStateException("environment provider failure");
            }
            return new LinkedHashMap<>(roots);
        });
        brokenProvider.prepare(() -> brokenProvider.resource(() -> "provider", this::dispose));
        providerFails[0] = true;
        bindings.clients.observe();
        require(!brokenProvider.active() && number("resourceSlots") == 0,
                "failed environment provider retires its host");

        NativeClientSession candidate = create("candidate-owner");
        expect("stale_environment", () -> candidate.prepare(() -> candidate.resource(() -> {
            roots.put("world", new Object());
            return "candidate";
        }, this::dispose)));
        bindings.clients.observe();
        require(number("resourceSlots") == 0, "unpublished candidate is disposed on environment change");

        NativeClientSession stale = create("stale-owner");
        AtomicReference<NativeClientAction> command = new AtomicReference<>();
        stale.prepare(() -> {
            stale.resource(() -> "stale", this::dispose);
            command.set(stale.action("must-not-run", () -> {
                throw new AssertionError("stale action executed");
            }, false));
        });
        stale.input(permit -> stale.submit(command.get(), permit));
        ScriptResourceTracker.unload("stale-owner");
        require(!stale.active(), "unload disables owned resources/actions immediately");
        bindings.clients.observe();
        require(number("pendingActions") == 0 && number("cancelledActions") > 0, "unload cancels queued actions");

        NativeClientSession world = create("world-owner");
        world.prepare(() -> world.resource(() -> "world", this::dispose));
        roots.put("world", null);
        bindings.clients.observe();
        require(!world.active(), "world disappearance invalidates the client host");
        NativeClientSession screen = create("screen-owner");
        screen.prepare(() -> screen.resource(() -> "screen", this::dispose));
        roots.put("screen", new Object());
        bindings.clients.observe();
        require(!screen.active(), "screen replacement invalidates the client host");

        NativeClientSession release = create("release-owner");
        AtomicReference<NativeClientResource<String>> handle = new AtomicReference<>();
        release.prepare(() -> handle.set(release.resource(() -> "released", this::dispose)));
        release.input(permit -> handle.get().close());
        require(number("pendingDisposals") == 1, "client action releases logically before native disposal");
        release.paint(() -> {
            bindings.clients.observe();
            require(number("pendingDisposals") == 1, "paint defers native resource disposal");
            expect("native_client_resource_closed", handle.get()::use);
        });
        bindings.clients.observe();
        require(number("pendingDisposals") == 0, "safe client boundary disposes released resources");
        release.close();

        NativeClientSession disposal = create("bad-disposer");
        disposal.prepare(() -> disposal.resource(() -> "bad", value -> {
            throw new IllegalStateException("disposal failure");
        }));
        disposal.close();
        bindings.clients.observe();
        require(number("resourceSlots") == 0 && number("failures") >= 2,
                "failed disposer reports failure and frees its slot");
    }

    private void limits() {
        NativeClientSession first = create("limit-one");
        NativeClientSession second = create("limit-two");
        first.prepare(() -> {
            for (int index = 0; index < 256; index++) {
                first.resource(() -> "slot", this::dispose);
            }
        });
        second.prepare(() -> {
            for (int index = 0; index < 256; index++) {
                second.resource(() -> "slot", this::dispose);
            }
        });
        NativeClientSession third = create("limit-three");
        expect("native_client_limit", () -> third.prepare(() -> third.resource(() -> "overflow", this::dispose)));
        first.close();
        second.close();
        bindings.clients.observe();
        require(number("sessions") == 0 && number("resourceSlots") >= 448,
                "mass close invalidates sessions immediately and limits each ordinary cleanup slice");
        for (int boundary = 0; boundary < 512 && number("resourceSlots") > 0; boundary++) {
            bindings.clients.observe();
        }
        require(number("resourceSlots") == 0 && number("sessions") == 0,
                "bounded slots eventually clean up completely");
    }

    private NativeClientSession create(String owner) {
        return bindings.clients.create(owner, () -> new LinkedHashMap<>(roots));
    }

    private long number(String key) {
        return ((Number) bindings.clients.diagnostics().get(key)).longValue();
    }

    private void dispose(String value) {
        require(value != null, "disposal retains the prepared resource until its safe boundary");
        expect("native_context_unavailable", NativeContext::current);
        try {
            LuaCallbackScope.requireGameplayWrite();
            throw new AssertionError("client disposal gained gameplay writes");
        } catch (RuntimeException expected) {
            checks++;
        }
        disposed.incrementAndGet();
    }

    private static void expect(String reason, Runnable callback) {
        try {
            callback.run();
            throw new AssertionError("Expected " + reason);
        } catch (NativeAccessFailure failure) {
            require(failure.reason.equals(reason), "exact client refusal: " + failure.reason);
        }
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

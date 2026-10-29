package betamoon.interop;

import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.binding.NativeBindings;
import betamoon.interop.binding.NativeMemberPlan;
import betamoon.interop.binding.NativeNames;
import betamoon.interop.discovery.NativeLoaderRegistry;
import betamoon.interop.reference.NativeMemberRef;
import betamoon.interop.reference.NativeTypeRef;
import betamoon.interop.runtime.NativeContext;
import betamoon.interop.runtime.NativeEntry;
import betamoon.interop.runtime.NativePermissions;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Client capabilities remain separate from raw initialization/unknown effects
 * and intersect every parent.
 */
public final class NativeClientCapabilityTest {
    private static final String OWNER = "native-client-capabilities";
    private static int worldWrites;
    private static int constructors;
    private static int checks;

    private NativeClientCapabilityTest() {
    }

    public static void main(String[] args) throws Exception {
        NativeLoaderRegistry registry = new NativeLoaderRegistry();
        registry.capture(Collections.singletonList(ClientLooking.class), Collections.singletonList(ClientLooking.class),
                Collections.emptyList(), false, null, false);
        TinyMappingResolver mapping = TinyMappingResolver.readStrict(
                new ByteArrayInputStream("tiny\t2\t0\tnamed\tclient\tserver\n".getBytes(StandardCharsets.UTF_8)));
        NativeBindings bindings = new NativeBindings(new NativeNames(mapping.snapshot(), "named"), registry);
        ScriptExecution.withOwner(OWNER, () -> {
            verify(bindings);
            return null;
        });
        bindings.close();
        System.out.println("Native client capabilities passed: " + checks
                + " checks; client preparation/actions grant no gameplay, unknown calls or arbitrary initialization.");
    }

    private static void verify(NativeBindings bindings) {
        NativeTypeRef type = NativeTypeRef.named(ClientLooking.class.getName(), "runtime", null, null);
        NativeMemberRef secret = new NativeMemberRef(NativeMemberRef.Kind.FIELD, type, null, "secret", "I", null, null);
        NativeMemberRef smuggle = new NativeMemberRef(NativeMemberRef.Kind.METHOD, type, null, "clientOperation", "()V",
                null, null);
        NativeMemberRef constructor = new NativeMemberRef(NativeMemberRef.Kind.CONSTRUCTOR, type, null, "new", "()V",
                null, null);
        ClientLooking receiver = new ClientLooking();
        int initialConstructions = constructors;
        Map<String, Object> roots = Collections.singletonMap("client", receiver);
        AtomicBoolean valid = new AtomicBoolean(true);
        NativeMemberPlan field;
        try (LuaCallbackScope callback = new LuaCallbackScope(false, true);
                NativeContext preparation = NativeEntry.clientPreparation(bindings, callback, roots, valid::get)
                        .open(OWNER)) {
            preparation.prepare();
            preparation.requireClientResource();
            field = bindings.exact(secret, preparation, true);
            require(field.get(preparation, true, receiver).equals(7), "private read prepared explicitly");
            NativeMemberPlan unknown = bindings.exact(smuggle, preparation, true);
            expect("native_phase_denied", () -> unknown.call(preparation, true, receiver, new Object[0]));
            NativeMemberPlan initialize = bindings.exact(constructor, preparation, true);
            expect("native_phase_denied", () -> initialize.call(preparation, true, null, new Object[0]));
            expect("native_phase_denied", () -> preparation.require(NativeContext.Effect.WRITE, true));
            expect("native_phase_denied", preparation::requireClientAction);
            require(Boolean.FALSE.equals(preparation.describe().get("gameplayWrites")), "no gameplay authority");
            require(Boolean.TRUE.equals(preparation.describe().get("bindingPreparation")), "explicit preparation");
            require(Boolean.TRUE.equals(preparation.describe().get("clientResources")), "curated resources only");
            try (NativeContext child = preparation.child()) {
                child.prepare();
                child.requireClientResource();
                expect("native_phase_denied", () -> child.require(NativeContext.Effect.UNKNOWN, true));
            }
            try (LuaCallbackScope nested = new LuaCallbackScope(false, false);
                    NativeContext query = NativeContext.open(bindings, OWNER, nested, false, roots, valid::get)) {
                expect("native_phase_denied", preparation::prepare);
                expect("native_phase_denied", preparation::requireClientResource);
                try (LuaCallbackScope requested = new LuaCallbackScope(false, true);
                        NativeContext attempted = NativeEntry.clientPreparation(bindings, requested, roots, valid::get)
                                .open(OWNER)) {
                    expect("native_phase_denied", attempted::prepare);
                    expect("native_phase_denied", attempted::requireClientResource);
                }
            }
            try (LuaCallbackScope nested = new LuaCallbackScope(false, true);
                    NativeContext action = NativeEntry.clientAction(bindings, nested, roots, valid::get).open(OWNER)) {
                expect("native_phase_denied", action::requireClientAction);
            }
            valid.set(false);
            expect("stale_environment", preparation::prepare);
        }
        valid.set(true);
        require(worldWrites == 0 && constructors == initialConstructions, "client-looking code was never invoked");
        try (LuaCallbackScope callback = new LuaCallbackScope(false, true);
                NativeContext action = NativeEntry.clientAction(bindings, callback, roots, valid::get).open(OWNER)) {
            action.requireClientAction();
            expect("native_phase_denied", action::prepare);
            expect("native_phase_denied", action::requireClientResource);
            expect("native_phase_denied", () -> action.require(NativeContext.Effect.UNKNOWN, true));
            expect("native_phase_denied", () -> action.require(NativeContext.Effect.INITIALIZE, true));
        }
        try (LuaCallbackScope callback = new LuaCallbackScope(true);
                NativeContext legacyClient = new NativeEntry(bindings, callback, NativeEntry.Phase.CLIENT, false, roots,
                        valid::get).open(OWNER)) {
            require(field.get(legacyClient, true, receiver).equals(7), "legacy prepared reads remain available");
            expect("native_phase_denied", legacyClient::prepare);
            expect("native_phase_denied", legacyClient::requireClientAction);
        }
        try (LuaCallbackScope callback = new LuaCallbackScope(true);
                NativeContext gameplay = NativeContext.open(bindings, OWNER, callback, true, roots, valid::get)) {
            gameplay.prepare();
            NativeMemberPlan unknown = bindings.exact(smuggle, gameplay, true);
            unknown.call(gameplay, true, receiver, new Object[0]);
            require(worldWrites == 1, "existing gameplay direct behavior is preserved");
            expect("direct_required", () -> unknown.call(gameplay, false, receiver, new Object[0]));
        }
        try (LuaCallbackScope callback = LuaCallbackScope.binding();
                NativeContext query = NativeContext.open(bindings, OWNER, callback,
                        NativePermissions.CLIENT_PREPARATION, roots, valid::get,
                        NativeEntry.Phase.CLIENT_PREPARATION)) {
            try {
                query.prepare();
                throw new AssertionError("binding flags were bypassed");
            } catch (RuntimeException expected) {
                checks++;
            }
        }
    }

    private static void expect(String reason, Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected " + reason);
        } catch (NativeAccessFailure failure) {
            require(reason.equals(failure.reason), "exact refusal: " + failure.reason);
        }
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static final class ClientLooking {
        private int secret = 7;

        public ClientLooking() {
            constructors++;
        }

        public void clientOperation() {
            worldWrites++;
        }
    }
}

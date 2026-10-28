package betamoon.interop.hosts;

import betamoon.interop.binding.NativeAccessFailure;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Function;
import java.util.function.BooleanSupplier;

/**
 * Per-instance activation record. Generated foreign aliases never look up a new
 * owner's callbacks.
 */
public final class NativeHostDispatch implements Function<Object[], Object> {
    private static final ThreadLocal<Deque<Invocation>> ENTERED = ThreadLocal.withInitial(ArrayDeque::new);
    private final NativeHostShape shape;
    private final String[] closedPolicies;
    private final InvocationHandler handler;
    private volatile int state;
    private final BooleanSupplier ownerReady;

    public NativeHostDispatch(NativeHostShape shape, String[] closedPolicies, InvocationHandler handler,
            BooleanSupplier ownerReady) {
        if (closedPolicies.length != shape.methods.size()) {
            throw new NativeAccessFailure("invalid_native_host_policy", "supply one closed policy per override");
        }
        this.shape = shape;
        this.closedPolicies = closedPolicies.clone();
        this.handler = handler;
        this.ownerReady = ownerReady;
        for (int index = 0; index < closedPolicies.length; index++) {
            String policy = closedPolicies[index];
            if (!Arrays.asList("super", "error", "noop").contains(policy)
                    || policy.equals("super") && !shape.hasSuper(index)
                    || policy.equals("noop") && shape.methods.get(index).getReturnType() != Void.TYPE) {
                throw new NativeAccessFailure("invalid_native_host_policy",
                        "closed policy must be super, error or void-only noop");
            }
        }
    }

    public synchronized void activate() {
        if (state != 0) {
            throw new NativeAccessFailure("native_implementation_closed", "construction or setup already failed");
        }
        state = 1;
    }

    public synchronized void close() {
        state = 2;
    }

    @Override
    public Object apply(Object[] request) {
        if (request == null || request.length != 3 || !(request[0] instanceof Integer)
                || !(request[2] instanceof Object[])) {
            throw new NativeAccessFailure("invalid_native_host_dispatch", "invalid JDK-only callback transport");
        }
        int index = (Integer) request[0];
        if (index < 0 || index >= shape.methods.size()) {
            throw new NativeAccessFailure("invalid_native_host_dispatch", "override index is outside this shape");
        }
        shape.requireInstance(request[1]);
        if (state == 0) {
            if (!shape.hasSuper(index)) {
                throw new NativeAccessFailure("unsupported_native_host_construction",
                        "abstract callback before activation");
            }
            return new Object[]{true, null};
        }
        if (state == 2) {
            return closed(index);
        }
        if (!ownerReady.getAsBoolean()) {
            if (shape.hasSuper(index)) {
                return new Object[]{true, null};
            }
            throw new NativeAccessFailure("owner_not_ready", "class callback owner has not completed initialization");
        }
        Method method = shape.methods.get(index);
        Deque<Invocation> entered = ENTERED.get();
        if (entered.size() >= 16 || entered.stream().anyMatch(call -> call.host == this && call.index == index)) {
            throw new NativeAccessFailure("native_host_reentry",
                    "host callbacks cannot reenter their active override; maximum nesting is 16");
        }
        entered.addLast(new Invocation(this, index));
        try {
            Object result = handler.invoke(request[1], method, (Object[]) request[2]);
            return state == 1 ? new Object[]{false, result} : closed(index);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (NativeAccessFailure failure) {
            if (state == 2 && Arrays
                    .asList("stale_owner", "stale_environment", "owned_value_closed", "stale_world",
                            "world_unavailable", "native_session_closed", "native_implementation_closed")
                    .contains(failure.reason)) {
                return closed(index);
            }
            throw failure;
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new NativeAccessFailure("native_callback_failed", method.getName(), failure);
        } finally {
            entered.removeLast();
            if (entered.isEmpty()) {
                ENTERED.remove();
            }
        }
    }

    private static final class Invocation {
        private final NativeHostDispatch host;
        private final int index;

        private Invocation(NativeHostDispatch host, int index) {
            this.host = host;
            this.index = index;
        }
    }

    private Object[] closed(int index) {
        if (closedPolicies[index].equals("super")) {
            return new Object[]{true, null};
        }
        if (closedPolicies[index].equals("noop")) {
            return new Object[]{false, null};
        }
        throw new NativeAccessFailure("native_implementation_closed", shape.methods.get(index).getName());
    }
}

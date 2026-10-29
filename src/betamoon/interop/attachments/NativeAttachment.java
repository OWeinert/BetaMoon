package betamoon.interop.attachments;

import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.binding.NativeMemberPlan;
import betamoon.interop.runtime.NativeContext;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A declared reversible single-argument registration; no whole-list
 * restoration.
 */
public final class NativeAttachment {
    private static final AtomicInteger RESERVED = new AtomicInteger();
    private Object receiver;
    private Object implementation;
    private final NativeMemberPlan add;
    private final NativeMemberPlan remove;
    private boolean reserved;
    public final Status status = new Status();

    /**
     * Detached status can be retained by Lua without keeping a native consumer
     * alive.
     */
    public static final class Status {
        private volatile String state = "prepared";

        public String state() {
            return state;
        }

        public void pending() {
            state = "pending_removal";
        }
    }

    public NativeAttachment(Object receiver, Object implementation, NativeMemberPlan add, NativeMemberPlan remove) {
        if (!(add.member instanceof Method) || !(remove.member instanceof Method) || add.parameters.length != 1
                || remove.parameters.length != 1 || add.valueType != Void.TYPE || remove.valueType != Void.TYPE
                || add.parameters[0] != remove.parameters[0] || add.parameters[0].isPrimitive()
                || !add.parameters[0].isInstance(implementation) || add.isStatic != remove.isStatic
                || add.member.equals(remove.member)
                || (add.isStatic
                        ? receiver != null
                        : receiver == null || !add.member.getDeclaringClass().isInstance(receiver)
                                || !remove.member.getDeclaringClass().isInstance(receiver))) {
            throw new NativeAccessFailure("invalid_native_attachment",
                    "use a matching exact add/remove (ReferenceType)V pair");
        }
        this.receiver = receiver;
        this.implementation = implementation;
        this.add = add;
        this.remove = remove;
        if (RESERVED.incrementAndGet() > 2048) {
            RESERVED.decrementAndGet();
            throw new NativeAccessFailure("native_attachment_limit", "active and deferred attachments exceed 2048");
        }
        reserved = true;
    }

    public void prepare(NativeContext context) {
        // Prepare BOTH operations before registration can have any side effect.
        try {
            remove.prepareInvocation(context, true);
            add.prepareInvocation(context, true);
        } catch (RuntimeException | Error failure) {
            receiver = null;
            implementation = null;
            releaseReservation();
            throw failure;
        }
    }

    public void install(NativeContext context) {
        status.state = "installing";
        add.call(context, true, receiver, new Object[]{implementation});
        if (status.state.equals("installing")) {
            status.state = "active";
        }
    }

    public void pending() {
        status.pending();
    }

    public String state() {
        return status.state();
    }

    /**
     * Called only at the client lifecycle boundary, after dispatch has been
     * disabled.
     */
    public void detach() {
        try {
            ((Method) remove.member).invoke(receiver, implementation);
            status.state = "closed";
        } catch (InvocationTargetException exception) {
            status.state = "detach_failed";
            Throwable cause = exception.getCause();
            if (cause instanceof VirtualMachineError) {
                throw (VirtualMachineError) cause;
            }
            if (cause instanceof ThreadDeath) {
                throw (ThreadDeath) cause;
            }
            throw new NativeAccessFailure("native_detach_failed", remove.identity(), cause);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            status.state = "detach_failed";
            throw new NativeAccessFailure("native_detach_failed", remove.identity(), exception);
        } finally {
            receiver = null;
            implementation = null;
            releaseReservation();
        }
    }

    private void releaseReservation() {
        if (reserved) {
            reserved = false;
            RESERVED.decrementAndGet();
        }
    }
}

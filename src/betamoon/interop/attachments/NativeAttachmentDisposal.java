package betamoon.interop.attachments;

import betamoon.interop.runtime.NativeContext;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.luamodloader.ScriptExecution;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A closing session can transfer only its native removal work, without keeping
 * its Lua registry alive.
 */
public final class NativeAttachmentDisposal {
    private static final ConcurrentLinkedQueue<Removal> DEFERRED = new ConcurrentLinkedQueue<>();

    private NativeAttachmentDisposal() {
    }

    public static void defer(String owner, NativeAttachment attachment, AtomicLong failures) {
        DEFERRED.add(new Removal(Thread.currentThread(), owner, attachment, failures));
    }

    public static void drain() {
        if (NativeContext.hasFrame() || !NativeContext.cleanupAllowed()) {
            return;
        }
        int available = DEFERRED.size();
        for (int i = 0; i < available; i++) {
            Removal removal = DEFERRED.poll();
            if (removal == null) {
                break;
            }
            if (removal.thread != Thread.currentThread()) {
                DEFERRED.add(removal);
            } else {
                remove(removal.owner, removal.attachment, removal.failures);
            }
        }
    }

    public static void remove(String owner, NativeAttachment attachment, AtomicLong failures) {
        try (NativeContext ignored = NativeContext.denied(owner)) {
            ScriptExecution.withOwner(owner, () -> {
                attachment.detach();
                return null;
            });
        } catch (RuntimeException | LinkageError failure) {
            failures.incrementAndGet();
            LuaScriptErrors.add(owner, "Native attachment removal failed", LuaScriptErrors.Outcome.CALLBACK_DISABLED,
                    failure);
        }
    }

    private static final class Removal {
        private final Thread thread;
        private final String owner;
        private final NativeAttachment attachment;
        private final AtomicLong failures;

        private Removal(Thread thread, String owner, NativeAttachment attachment, AtomicLong failures) {
            this.thread = thread;
            this.owner = owner;
            this.attachment = attachment;
            this.failures = failures;
        }
    }
}

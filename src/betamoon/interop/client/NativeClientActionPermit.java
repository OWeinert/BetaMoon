package betamoon.interop.client;

import java.lang.ref.WeakReference;

/**
 * Issued only while a Java host is dispatching an authorized input action;
 * expires on callback exit.
 */
public final class NativeClientActionPermit {
    final WeakReference<NativeClientSession> session;
    final Thread thread = Thread.currentThread();
    boolean active = true;
    int submitted;

    NativeClientActionPermit(NativeClientSession session) {
        this.session = new WeakReference<>(session);
    }
}

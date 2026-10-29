package betamoon.interop.client;

import java.lang.ref.WeakReference;

/**
 * Prepared Java command identity; no Lua function or receiver is retained by
 * this token.
 */
public final class NativeClientAction {
    final WeakReference<NativeClientSession> session;
    final long identity;

    NativeClientAction(NativeClientSession session, long identity) {
        this.session = new WeakReference<>(session);
        this.identity = identity;
    }
}

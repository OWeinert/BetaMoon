package betamoon.interop.client;

import betamoon.interop.binding.NativeAccessFailure;
import java.lang.ref.WeakReference;

/**
 * Retainable resource identity. The owner session holds the native payload and
 * trusted disposer.
 */
public final class NativeClientResource<T> implements AutoCloseable {
    private final WeakReference<NativeClientSession> session;
    final long identity;

    NativeClientResource(NativeClientSession session, long identity) {
        this.session = new WeakReference<>(session);
        this.identity = identity;
    }

    public T use() {
        NativeClientSession owner = session.get();
        if (owner == null) {
            throw new NativeAccessFailure("native_client_resource_closed", "resource owner is unavailable");
        }
        return owner.use(this);
    }

    @Override
    public void close() {
        NativeClientSession owner = session.get();
        if (owner != null) {
            owner.release(this);
        }
    }
}

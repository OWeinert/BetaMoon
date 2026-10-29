package betamoon.gui.graphics;

import betamoon.luaapi.utils.LuaCallbackScope;
import java.util.Objects;

/**
 * Java host-issued, thread/pass-bound graphics authority. Does not grant
 * native, gameplay or UI mutation permission. Raw Lua registration belongs to
 * P4.
 */
public final class GraphicsRenderScope implements AutoCloseable {
    private static final ThreadLocal<GraphicsRenderScope> CURRENT = new ThreadLocal<>();

    private final Thread thread = Thread.currentThread();
    private final GraphicsRenderScope parent;
    private final Runnable validateOwner;
    private final AutoCloseable state;
    private final LuaCallbackScope callbackScope;
    private boolean active = true;

    private GraphicsRenderScope(Runnable validateOwner, Backend backend, GraphicsTransform transform,
            GraphicsClip.Pixels clip) {
        this.validateOwner = Objects.requireNonNull(validateOwner, "graphics owner validation");
        validateOwner.run();
        parent = CURRENT.get();
        state = Objects.requireNonNull(backend.enter(transform, clip), "graphics state guard");
        callbackScope = LuaCallbackScope.binding();
        CURRENT.set(this);
    }

    public static GraphicsRenderScope open(Runnable validateOwner, Backend backend, GraphicsTransform transform,
            GraphicsClip.Pixels clip) {
        return new GraphicsRenderScope(validateOwner, Objects.requireNonNull(backend, "graphics backend"),
                Objects.requireNonNull(transform, "graphics transform"), Objects.requireNonNull(clip, "graphics clip"));
    }

    public void requireActive() {
        if (!active || thread != Thread.currentThread() || CURRENT.get() != this) {
            throw new IllegalStateException("Graphics access requires its active owning render pass");
        }
        validateOwner.run();
    }

    @Override
    public void close() {
        if (!active) {
            return;
        }
        if (thread != Thread.currentThread() || CURRENT.get() != this) {
            throw new IllegalStateException("Graphics scopes must close on their render thread in reverse order");
        }
        active = false;
        try {
            state.close();
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot restore graphics state", failure);
        } finally {
            callbackScope.close();
            if (parent == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(parent);
            }
        }
    }

    @FunctionalInterface
    public interface Backend {
        /** Must unwind partial setup itself if entry throws. */
        AutoCloseable enter(GraphicsTransform transform, GraphicsClip.Pixels clip);
    }
}

package betamoon.gui.drawing;

import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.graphics.GraphicsClip;
import betamoon.gui.graphics.GraphicsRawStateGuard;
import betamoon.gui.graphics.GraphicsRenderScope;
import betamoon.gui.graphics.GraphicsResources;
import betamoon.gui.graphics.GraphicsTransform;
import betamoon.gui.presentation.GuiPublishedData;
import betamoon.luaapi.client.ui.GuiRawGraphicsAccess;
import betamoon.luaapi.client.ui.GuiReadOnlyValues;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * Explicit per-frame Lua exception to prepared painting. Failures disable only
 * this contribution.
 */
public final class GuiRawDrawing implements AutoCloseable {
    private final String owner;
    private final String path;
    private final ScriptResourceTracker.OwnerGeneration ownerState;
    private final long generation;
    private final long started = System.nanoTime();
    private final GuiPublishedData data = new GuiPublishedData();
    private LuaValue publishedData = GuiReadOnlyValues.fromPublished(Collections.emptyMap());
    private LuaValue callback;
    private Function<LuaCallbackScope, LuaTable> context;
    private Runnable validateHost;
    private GraphicsResources resources;
    private Consumer<Throwable> failures;
    private Throwable pendingFailure;
    private boolean closed;
    private boolean failed;
    private long previous;

    public GuiRawDrawing(String owner, String path, LuaValue callback, Function<LuaCallbackScope, LuaTable> context,
            Runnable validateHost, GraphicsResources resources, Consumer<Throwable> failures) {
        if (callback == null || !callback.isfunction()) {
            throw new IllegalArgumentException("Raw drawing requires an explicitly registered callback");
        }
        this.owner = owner;
        this.path = Objects.requireNonNull(path, "raw drawing path");
        this.callback = callback;
        this.context = Objects.requireNonNull(context, "raw native read context");
        this.validateHost = Objects.requireNonNull(validateHost, "raw host validation");
        this.resources = Objects.requireNonNull(resources, "prepared graphics resources");
        this.failures = Objects.requireNonNull(failures, "raw failure reporting");
        ownerState = ScriptResourceTracker.ownerToken(owner);
        generation = ownerState.current();
    }

    public void publish(Map<String, ?> values) {
        LuaCallbackScope.requireUiStateWrite();
        requireOwnerGeneration();
        if (data.publish(values)) {
            publishedData = GuiReadOnlyValues.fromPublished(data.snapshot());
        }
    }

    public void update() {
        if (pendingFailure != null && !closed) {
            Throwable failure = pendingFailure;
            pendingFailure = null;
            failures.accept(failure);
        }
    }

    public boolean failed() {
        return failed;
    }

    public boolean available() {
        return !closed && !failed && ownerState.matches(generation);
    }

    public boolean draw(GuiContext environment, GuiElement element, Map<String, Object> properties) {
        return draw(environment, element, properties, element.getWidth(), element.getHeight());
    }

    public boolean draw(GuiContext environment, GuiElement element, Map<String, Object> properties, int width,
            int height) {
        if (closed || failed) {
            return false;
        }
        GraphicsClip logicalClip = environment.getElementClip();
        GraphicsClip.Pixels clip = logicalClip.pixels(environment.getScreenWidth(), environment.getScreenHeight(),
                environment.getDisplayWidth(), environment.getDisplayHeight());
        GraphicsTransform origin = new GraphicsTransform(element.getLeft(), element.getTop(), 1, 1);
        long now = System.nanoTime();
        double delta = previous == 0 ? 0 : (now - previous) / 1e9;
        previous = now;
        try (GraphicsRenderScope graphics = GraphicsRenderScope.open(this::requireOwner, GraphicsRawStateGuard::enter,
                origin, clip);
                LuaCallbackScope scope = LuaCallbackScope.binding();
                GuiRawGraphicsAccess access = new GuiRawGraphicsAccess(graphics, resources, clip)) {
            LuaTable frame = context.apply(scope);
            frame.set("gl", access);
            frame.set("data", publishedData);
            frame.set("props", GuiReadOnlyValues.fromPublished(properties));
            frame.set("time", LuaValue.valueOf((now - started) / 1e9));
            frame.set("delta", LuaValue.valueOf(delta));
            frame.set("partialTick", LuaValue.valueOf(environment.getPartialTicks()));
            frame.set("opacity", LuaValue.valueOf(environment.getOpacity()));
            frame.set("bounds",
                    GuiReadOnlyValues.fromPublished(values("x", 0, "y", 0, "width", width, "height", height)));
            frame.set("viewport",
                    GuiReadOnlyValues.fromPublished(values("width", environment.getScreenWidth(), "height",
                            environment.getScreenHeight(), "framebufferWidth", environment.getDisplayWidth(),
                            "framebufferHeight", environment.getDisplayHeight())));
            frame.set("clip",
                    GuiReadOnlyValues.fromPublished(values("left", logicalClip.left, "top", logicalClip.top, "right",
                            logicalClip.right, "bottom", logicalClip.bottom, "pixelX", clip.x, "pixelY", clip.y,
                            "pixelWidth", clip.width, "pixelHeight", clip.height)));
            GraphicsTransform transform = environment.getElementTransform().compose(origin);
            frame.set("transform", GuiReadOnlyValues.fromPublished(values("x", transform.x, "y", transform.y, "scaleX",
                    transform.scaleX, "scaleY", transform.scaleY)));
            frame.set("inverse", GuiReadOnlyValues.fromPublished(values("x", -transform.x / transform.scaleX, "y",
                    -transform.y / transform.scaleY, "scaleX", 1 / transform.scaleX, "scaleY", 1 / transform.scaleY)));
            ScriptExecution.invoke(owner, "ui.rawDraw." + path, callback, frame);
            graphics.requireActive();
            return true;
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            failed = true;
            callback = null;
            pendingFailure = failure;
            return false;
        }
    }

    private void requireOwnerGeneration() {
        if (closed || failed || !ownerState.matches(generation)) {
            throw new IllegalStateException("Raw drawing owner is closed, disabled or expired");
        }
    }

    private void requireOwner() {
        if (closed || failed || !ownerState.matches(generation)) {
            throw new IllegalStateException(path + ": raw drawing owner/revision has expired");
        }
        validateHost.run();
    }

    @Override
    public void close() {
        closed = true;
        callback = null;
        context = null;
        validateHost = null;
        resources = null;
        failures = null;
        pendingFailure = null;
        publishedData = LuaValue.NIL;
        data.publish(Collections.emptyMap());
    }

    private static Map<String, Object> values(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put((String) entries[index], entries[index + 1]);
        }
        return result;
    }
}

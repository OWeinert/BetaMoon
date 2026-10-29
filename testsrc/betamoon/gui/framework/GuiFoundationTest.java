package betamoon.gui.framework;

import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.graphics.GraphicsClip;
import betamoon.gui.graphics.GraphicsRenderScope;
import betamoon.gui.graphics.GraphicsTransform;
import betamoon.gui.presentation.GuiPublishedData;
import betamoon.gui.widget.GuiLink;
import betamoon.gui.widget.GuiMessage;
import betamoon.luaapi.utils.LuaCallbackScope;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.luaj.vm2.LuaError;

/**
 * Headless P1/P2 contracts; GPU behavior is verified separately by the
 * offscreen fixture.
 */
public final class GuiFoundationTest {
    public static void main(String[] args) throws Exception {
        detachedPublication();
        graphicsGeometry();
        graphicsAuthority();
        cachedSuppliers();
        displayedGeometryAndCancellation();
        captureTransfer();
        routedMutation();
        System.out.println("UI foundations passed: detached atomic models, graphics scopes, transforms/clips, "
                + "published suppliers, displayed input, cancellation and deferred routing.");
    }

    private static void detachedPublication() {
        GuiPublishedData data = new GuiPublishedData();
        List<Object> rows = new ArrayList<>(Arrays.asList("first", Collections.singletonMap("value", 7)));
        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("rows", rows);
        require(data.publish(candidate), "Initial publication was ignored");
        Map<String, Object> saved = data.snapshot();
        rows.set(0, "changed");
        require(((List<?>) saved.get("rows")).get(0).equals("first"), "Snapshot retained mutable input");
        expect(UnsupportedOperationException.class, () -> saved.clear());
        expect(UnsupportedOperationException.class, () -> ((List<?>) saved.get("rows")).clear());
        require(!data.publish(saved) && data.revision() == 1, "Equal publication changed revision");
        for (Object invalid : Arrays.asList(new Object(), Double.NaN, Double.POSITIVE_INFINITY, Long.MAX_VALUE)) {
            candidate.put("invalid", invalid);
            expect(IllegalArgumentException.class, () -> data.publish(candidate));
            require(data.snapshot() == saved && data.revision() == 1, "Failed publication replaced usable state");
        }
        candidate.clear();
        candidate.put("cycle", candidate);
        expect(IllegalArgumentException.class, () -> data.publish(candidate));
        List<Object> cycle = new ArrayList<>();
        cycle.add(cycle);
        expect(IllegalArgumentException.class, () -> data.publish(Collections.singletonMap("cycle", cycle)));
        Object nested = "leaf";
        for (int i = 0; i < 17; i++) {
            nested = Collections.singletonList(nested);
        }
        Map<String, Object> deep = Collections.singletonMap("deep", nested);
        expect(IllegalArgumentException.class, () -> data.publish(deep));
        expect(IllegalArgumentException.class,
                () -> data.publish(Collections.singletonMap("many", Collections.nCopies(16385, true))));
        char[] large = new char[1024 * 1024];
        Arrays.fill(large, '\u20ac');
        expect(IllegalArgumentException.class,
                () -> data.publish(Collections.singletonMap("large", new String(large))));
        require(data.snapshot() == saved, "Budget failure corrupted the published model");
    }

    private static void graphicsGeometry() {
        GraphicsTransform transform = new GraphicsTransform(10.5, -3, 2, 0.5)
                .compose(new GraphicsTransform(-4, 8, 0.5, 3));
        near(transform.localX(transform.mapX(-27.5)), -27.5);
        near(transform.localY(transform.mapY(123.5)), 123.5);
        expect(IllegalArgumentException.class, () -> new GraphicsTransform(0, 0, 0, 1));
        expect(IllegalArgumentException.class, () -> new GraphicsTransform(Double.NaN, 0, 1, 1));
        GraphicsClip clip = new GraphicsClip(1.2, 2.4, 8.1, 9.2);
        GraphicsClip.Pixels pixels = clip.pixels(100, 80, 250, 200);
        require(pixels.x == 3 && pixels.y == 177 && pixels.width == 18 && pixels.height == 17,
                "Fractional clip did not round outward / flip Y consistently");
        pixels = clip.intersect(new GraphicsClip(20, 20, 30, 30)).pixels(100, 80, 250, 200);
        require(pixels.width == 0 && pixels.height == 0, "Disjoint clip became visible");
        pixels = new GraphicsClip(1.2, 2, 1.2, 5).pixels(100, 80, 250, 200);
        require(pixels.width == 0 && pixels.height == 0, "Empty fractional clip gained a pixel");
        pixels = new GraphicsClip(-10, -10, 110, 90).pixels(100, 80, 250, 200);
        require(pixels.x == 0 && pixels.y == 0 && pixels.width == 250 && pixels.height == 200,
                "Clip escaped framebuffer bounds");
    }

    private static void graphicsAuthority() throws Exception {
        AtomicInteger restored = new AtomicInteger();
        GraphicsRenderScope.Backend backend = (transform, clip) -> restored::incrementAndGet;
        GraphicsClip.Pixels pixels = new GraphicsClip(0, 0, 10, 10).pixels(10, 10, 10, 10);
        GraphicsRenderScope outer = GraphicsRenderScope.open(() -> {
        }, backend, GraphicsTransform.IDENTITY, pixels);
        outer.requireActive();
        expect(LuaError.class, LuaCallbackScope::requireGameplayWrite);
        expect(LuaError.class, LuaCallbackScope::requireUiStateWrite);
        AtomicReference<Throwable> wrongThread = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                outer.requireActive();
            } catch (Throwable failure) {
                wrongThread.set(failure);
            }
        });
        thread.start();
        thread.join();
        require(wrongThread.get() instanceof IllegalStateException, "Graphics authority crossed threads");
        try (GraphicsRenderScope inner = GraphicsRenderScope.open(() -> {
        }, backend, GraphicsTransform.IDENTITY, pixels)) {
            inner.requireActive();
            expect(IllegalStateException.class, outer::requireActive);
            expect(IllegalStateException.class, outer::close);
        }
        outer.requireActive();
        expect(IllegalArgumentException.class, () -> GraphicsRenderScope.open(() -> {
            throw new IllegalArgumentException("stale owner");
        }, backend, GraphicsTransform.IDENTITY, pixels));
        outer.requireActive();
        outer.close();
        outer.close();
        require(restored.get() == 2, "Nested graphics scopes did not restore exactly once");
        expect(IllegalStateException.class, outer::requireActive);
        GraphicsRenderScope broken = GraphicsRenderScope.open(() -> {
        }, (transform, clip) -> () -> {
            throw new IllegalArgumentException("cleanup fixture");
        }, GraphicsTransform.IDENTITY, pixels);
        expect(IllegalArgumentException.class, broken::close);
        expect(IllegalStateException.class, broken::requireActive);
        LuaCallbackScope.requireGameplayWrite();
        LuaCallbackScope.requireUiStateWrite();
    }

    private static void cachedSuppliers() {
        AtomicInteger calls = new AtomicInteger();
        GuiLink link = new GuiLink();
        link.setTextSupplier(() -> "text " + calls.incrementAndGet());
        link.setTooltipSupplier(() -> "tooltip " + calls.incrementAndGet());
        GuiMessage message = new GuiMessage(() -> "message " + calls.incrementAndGet());
        GuiContainer root = new GuiContainer();
        root.add(link);
        root.add(message);
        GuiScene scene = scene(root);
        scene.prepare();
        require(calls.get() == 3, "Publication did not sample suppliers exactly once");
        for (int i = 0; i < 10; i++) {
            root.measure(scene.getContext(), GuiGeometry.Constraints.tight(100, 80));
            root.arrange(scene.getContext(), new Rect(0, 0, 100, 80));
        }
        require(calls.get() == 3, "Layout called a dynamic supplier");
    }

    private static void displayedGeometryAndCancellation() throws Exception {
        Probe control = new Probe();
        GuiContainer root = new GuiContainer();
        root.add(control);
        GuiScene scene = scene(root);
        root.arrange(scene.getContext(), new Rect(0, 0, 100, 80));
        control.arrange(scene.getContext(), new Rect(10, 10, 30, 30));
        control.setVisualTransform(new GraphicsTransform(20, 5, 2, 2));
        publish(scene, root);
        require(scene.mousePressed(35, 20, 1), "Transformed secondary-button press missed");
        require(control.lastX == 12 && control.lastY == 12, "Input did not use inverse displayed transform");
        scene.mouseDragged(36, 21, true);
        require(control.lastButton == 1, "Drag lost its captured secondary button");
        require(!scene.mouseReleased(35, 20, 0), "Wrong button released capture");
        scene.cancelPointerCapture();
        require(control.cancels == 1 && control.releases == 0, "Cancellation synthesized release");
        require(scene.mouseReleased(35, 20, 1) && control.releases == 0, "Cancelled release reached a control");
        control.setVisualTransform(new GraphicsTransform(60, 0, 1, 1));
        require(scene.mousePressed(35, 20, 0), "Undisplayed transform changed targeting");
        control.setEnabled(false);
        require(control.cancels == 2, "Disabled capture did not receive cancel");
        require(scene.mouseReleased(35, 20, 0) && control.releases == 0, "Disable leaked release");
        control.setEnabled(true);
        scene.updateEnvironment(null, null, 120, 80, 240, 160);
        require(!scene.mousePressed(35, 20, 0), "Resize targeted an obsolete viewport");
    }

    private static void routedMutation() throws Exception {
        List<String> order = new ArrayList<>();
        Probe replacement = new Probe();
        replacement.setFocusable(true);
        GuiContainer parent = new GuiContainer() {
            @Override
            protected boolean onInputCapture(GuiContext context, GuiInputEvent event) {
                order.add("capture");
                return false;
            }

            @Override
            protected boolean onInput(GuiContext context, GuiInputEvent event) {
                order.add("bubble");
                require(getChildren().size() == 1, "Mutation changed the active route");
                return true;
            }
        };
        GuiElement child = new GuiElement() {
            @Override
            protected boolean onInput(GuiContext context, GuiInputEvent event) {
                order.add("target");
                parent.remove(this);
                parent.add(replacement);
                context.requestFocus(replacement);
                require(getParent() == parent, "Tree mutation ran during dispatch");
                return false;
            }
        };
        parent.add(child);
        GuiScene scene = scene(parent);
        parent.arrange(scene.getContext(), new Rect(0, 0, 100, 80));
        publish(scene, parent);
        require(scene.mousePressed(10, 10, 0), "Routed event was lost");
        require(order.equals(Arrays.asList("capture", "target", "bubble")), "Capture/target/bubble order changed");
        require(parent.getChildren().size() == 1 && parent.getChildren().get(0) == replacement
                && child.getParent() == null, "Deferred mutation was not committed");
        require(scene.getFocusedElement() == replacement, "Focus request ran before deferred attachment");
    }

    private static void captureTransfer() throws Exception {
        AtomicInteger cancellations = new AtomicInteger();
        AtomicInteger releases = new AtomicInteger();
        GuiContainer parent = new GuiContainer() {
            @Override
            protected boolean onInput(GuiContext context, GuiInputEvent event) {
                if (event.getType() == GuiInputEvent.Type.POINTER_DOWN) {
                    capturePointer(context);
                    return true;
                }
                if (event.getType() == GuiInputEvent.Type.POINTER_UP) {
                    releases.incrementAndGet();
                    return true;
                }
                return false;
            }
        };
        GuiElement child = new GuiElement() {
            @Override
            protected boolean onInput(GuiContext context, GuiInputEvent event) {
                if (event.getType() == GuiInputEvent.Type.POINTER_DOWN) {
                    capturePointer(context);
                }
                if (event.getType() == GuiInputEvent.Type.POINTER_CANCEL) {
                    cancellations.incrementAndGet();
                }
                return false;
            }
        };
        parent.add(child);
        GuiScene scene = scene(parent);
        publish(scene, parent);
        scene.mousePressed(10, 10, 1);
        require(cancellations.get() == 1, "Capture transfer did not cancel the original owner");
        require(scene.mouseReleased(10, 10, 1) && releases.get() == 1 && scene.capturedPointerButton() == -1,
                "Transferred capture remained cancelled/stuck after release");
    }

    private static GuiScene scene(GuiElement content) {
        GuiScene scene = new GuiScene();
        scene.setContent(content);
        scene.updateEnvironment(null, null, 100, 80, 200, 160);
        content.getParent().arrange(scene.getContext(), new Rect(0, 0, 100, 80));
        return scene;
    }

    private static void publish(GuiScene scene, GuiElement content) throws Exception {
        GuiElement root = content;
        while (root.getParent() != null) {
            root = root.getParent();
        }
        // A headless fixture publishes production geometry without pretending to paint
        // GPU pixels.
        Field displayed = GuiScene.class.getDeclaredField("displayedFrame");
        displayed.setAccessible(true);
        displayed.set(scene, new GuiFrameSnapshot(root, 1, scene.getContext()));
    }

    private static final class Probe extends GuiElement {
        private int cancels;
        private int releases;
        private int lastX;
        private int lastY;
        private int lastButton;

        @Override
        protected boolean onInput(GuiContext context, GuiInputEvent event) {
            if (event.getType() == GuiInputEvent.Type.POINTER_DOWN) {
                lastX = event.getMouseX();
                lastY = event.getMouseY();
                capturePointer(context);
                return true;
            }
            if (event.getType() == GuiInputEvent.Type.POINTER_CANCEL) {
                cancels++;
                return true;
            }
            if (event.getType() == GuiInputEvent.Type.POINTER_DRAG) {
                lastButton = event.getButton();
                return true;
            }
            if (event.getType() == GuiInputEvent.Type.POINTER_UP) {
                releases++;
                return true;
            }
            return false;
        }
    }

    private static void near(double actual, double expected) {
        require(Math.abs(actual - expected) < 1e-9, "Expected " + expected + ", got " + actual);
    }

    private static void expect(Class<? extends Throwable> type, Runnable operation) {
        try {
            operation.run();
        } catch (Throwable failure) {
            if (type.isInstance(failure)) {
                return;
            }
            throw new AssertionError("Expected " + type.getName(), failure);
        }
        throw new AssertionError("Expected " + type.getName());
    }

    private static void require(boolean condition, String detail) {
        if (!condition) {
            throw new AssertionError(detail);
        }
    }
}

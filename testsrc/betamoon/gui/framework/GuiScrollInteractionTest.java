package betamoon.gui.framework;

import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.graphics.GraphicsClip;
import betamoon.gui.graphics.GraphicsTransform;
import betamoon.gui.style.GuiDefaultTemplate;
import betamoon.gui.style.GuiStyleSheet;
import betamoon.gui.widget.GuiScrollView;
import betamoon.gui.widget.GuiStyledView;
import java.util.Collections;
import org.lwjgl.input.Keyboard;

/**
 * Shared scrolling uses displayed geometry, nested focus projection and gesture
 * cancellation.
 */
public final class GuiScrollInteractionTest {
    public static void main(String[] arguments) {
        clippedPointerFocus();
        nestedTransformedFocus();
        keyboardAndPaging();
        scrollbarLifetimes();
        contentGestureCancellation();
        System.out.println("Scroll interaction passed: nested transformed focus reveal, clipped pointer focus, "
                + "keyboard/track paging, displayed thumb routing, reset/resize/move cancellation and wide metrics.");
    }

    private static void clippedPointerFocus() {
        Fixture f = new Fixture(60, 50, 200, 300, 40);
        require(f.scene.mousePressed(20, 45, 0) && f.scene.mouseReleased(20, 45, 0),
                "A visible part of a clipped control did not receive its displayed click");
        require(f.activations == 1 && f.scroll.getScrollY() == 0,
                "Pointer focus scrolled a control away before completing its displayed gesture");
        f.prepare();
        f.scene.mousePressed(20, 55, 0);
        f.scene.mouseReleased(20, 55, 0);
        require(f.activations == 1, "Clipped content received pointer input outside the viewport");
        f.scene.getContext().requestFocus(f.scroll);
        f.scene.getContext().requestFocus(f.button);
        f.prepare();
        require(f.scroll.getScrollY() == 10, "Programmatic focus did not reveal a partly clipped control");
    }

    private static void nestedTransformedFocus() {
        GuiStyledView target = button(() -> {
        });
        target.setVisualTransform(new GraphicsTransform(5, 3, 1, 1));
        GuiContainer innerContent = new GuiContainer() {
            protected void arrangeChildren(GuiContext context) {
                target.arrange(context, Rect.fromPositionAndSize(getLeft() + 200, getTop() + 220, 20, 20));
            }
        };
        innerContent.add(target);
        GuiScrollView inner = new GuiScrollView(innerContent, GuiScrollView.Policy.BOTH);
        inner.setContentSize(300, 300);
        inner.setVisualTransform(new GraphicsTransform(7, 3, 1.5, 1.25));
        GuiContainer outerContent = new GuiContainer() {
            protected void arrangeChildren(GuiContext context) {
                inner.arrange(context, Rect.fromPositionAndSize(getLeft() + 130, getTop() + 140, 60, 60));
            }
        };
        outerContent.add(inner);
        GuiScrollView outer = new GuiScrollView(outerContent, GuiScrollView.Policy.BOTH);
        outer.setContentSize(400, 400);
        GuiScene scene = new GuiScene();
        scene.updateEnvironment(null, null, 100, 100, 100, 100);
        scene.setContent(outer);
        prepare(scene);
        scene.getContext().requestFocus(target);
        prepare(scene);
        require(inner.getScrollX() == 165 && inner.getScrollY() == 183 && outer.getScrollX() == 127
                && outer.getScrollY() == 118,
                "Nested reveal did not project inner offsets, clipping and transforms into the outer viewport");
        GraphicsClip bounds = scene.getDisplayedFrame().node(target).bounds;
        require(bounds.left >= 0 && bounds.top >= 0 && bounds.right <= 100 && bounds.bottom <= 100,
                "Focused transformed bounds remain outside the displayed viewport");
    }

    private static void keyboardAndPaging() {
        Fixture f = new Fixture(100, 100, 300, 400, 0);
        f.scene.getContext().requestFocus(f.scroll);
        require(f.scene.keyTyped('\0', Keyboard.KEY_NEXT, false) && f.scroll.getScrollY() == 100,
                "Page Down did not scroll its viewport");
        f.prepare();
        f.scene.keyTyped('\0', Keyboard.KEY_END, true);
        f.prepare();
        require(f.scroll.getScrollX() == 200 && f.scroll.getScrollY() == 100, "Shift+End changed the wrong axis");
        f.scroll.resetScroll();
        f.prepare();
        f.scene.mousePressed(98, 60, 0);
        f.scene.mouseReleased(98, 60, 0);
        f.prepare();
        require(f.scroll.getScrollY() == 100, "Scrollbar track click did not page vertically");
        f.scroll.resetScroll();
        f.prepare();
        f.scene.mouseScrolled(20, 20, -120, true);
        f.prepare();
        require(f.scroll.getScrollX() == 15 && f.scroll.getScrollY() == 0,
                "Shift-wheel did not select the horizontal axis");
        f.scroll.setContentSize(300, 100);
        f.prepare();
        f.scene.keyTyped('\0', Keyboard.KEY_END, false);
        f.prepare();
        require(f.scroll.getScrollX() == 200, "Home/End ignored the only overflowing axis");
    }

    private static void scrollbarLifetimes() {
        Fixture f = new Fixture(100, 100, 300, 400, 0);
        f.scroll.setContentSize(300, 800);
        f.scene.prepare(); // Deliberately retain the older displayed frame.
        f.scene.mousePressed(98, 5, 0);
        f.scene.mouseDragged(98, 80, true);
        f.scene.mouseReleased(98, 80, 0);
        require(f.scroll.getScrollY() == 0, "A stale displayed thumb began a drag against new metrics");
        f.prepare();
        f.scene.mousePressed(98, 5, 0);
        f.scene.mouseDragged(98, 60, true);
        f.prepare();
        require(f.scroll.getScrollY() > 0, "A fresh displayed thumb could not drag");
        int before = f.scroll.getScrollY();
        f.scroll.setContentSize(300, 900);
        f.prepare();
        f.scene.mouseDragged(98, 90, true);
        f.scene.mouseReleased(98, 90, 0);
        require(f.scroll.getScrollY() == before, "Metric replacement retained thumb capture");
        f.scroll.resetScroll();
        f.prepare();
        f.scene.mousePressed(98, 5, 0);
        f.scroll.resetScroll();
        f.prepare();
        f.scene.mouseDragged(98, 90, true);
        f.scene.mouseReleased(98, 90, 0);
        require(f.scroll.getScrollY() == 0, "Reset retained an active thumb gesture");
        f.scroll.setContentSize(1_000_000_000, 1_000_000_000);
        f.prepare();
        f.scene.getContext().requestFocus(f.scroll);
        f.scene.keyTyped('\0', Keyboard.KEY_END, false);
        f.prepare();
        require(f.scroll.getScrollY() == 1_000_000_000 - 100, "Large content metrics overflowed keyboard scrolling");
        f.scene.mousePressed(98, 90, 0);
        f.scene.mouseDragged(98, 0, true);
        f.scene.mouseReleased(98, 0, 0);
        f.prepare();
        require(f.scroll.getScrollY() == 0, "Large thumb drag arithmetic overflowed");
        f.scroll.setScrollPosition(1_000_000_000, 1_000_000_000);
        GraphicsClip clipped = f.scroll.revealFocus(new GraphicsClip(-1e9, -1e9, 1e9, 1e9));
        require(clipped.left == 0 && clipped.top == 0 && clipped.right == 100 && clipped.bottom == 100,
                "Oversized focused bounds escaped the viewport before bounded graphics clipping");
    }

    private static void contentGestureCancellation() {
        Fixture f = new Fixture(100, 100, 300, 400, 0);
        f.scene.mousePressed(10, 10, 0);
        f.scroll.setScrollPosition(0, 30);
        f.prepare();
        f.scene.mouseReleased(10, 10, 0);
        require(f.activations == 0, "Scrolling retained a content control's old activation gesture");
        f.scroll.resetScroll();
        f.prepare();
        f.scene.mousePressed(10, 10, 0);
        f.scene.mouseReleased(10, 10, 0);
        require(f.activations == 1, "Cancelling a moving gesture prevented a later valid activation");
    }

    private static final class Fixture {
        final GuiScene scene = new GuiScene();
        final GuiStyledView button;
        final GuiScrollView scroll;
        int activations;
        Fixture(int width, int height, int contentWidth, int contentHeight, int buttonY) {
            button = button(() -> activations++);
            GuiContainer content = new GuiContainer() {
                protected void arrangeChildren(GuiContext context) {
                    button.arrange(context, Rect.fromPositionAndSize(getLeft(), getTop() + buttonY, 60, 20));
                }
            };
            content.add(button);
            scroll = new GuiScrollView(content, GuiScrollView.Policy.BOTH, true);
            scroll.setContentSize(contentWidth, contentHeight);
            GuiContainer root = new GuiContainer() {
                protected void arrangeChildren(GuiContext context) {
                    scroll.arrange(context, Rect.fromPositionAndSize(0, 0, width, height));
                }
            };
            root.add(scroll);
            scene.updateEnvironment(null, null, 100, 100, 100, 100);
            scene.setContent(root);
            prepare();
        }

        void prepare() {
            GuiScrollInteractionTest.prepare(scene);
        }
    }
    private static GuiStyledView button(Runnable action) {
        GuiStyledView button = new GuiStyledView("button", GuiStyleSheet.MINIMAL, GuiDefaultTemplate.INSTANCE,
                (event, value) -> {
                    action.run();
                    return true;
                });
        button.configure(Collections.singletonMap("text", "Button"));
        return button;
    }

    private static void prepare(GuiScene scene) {
        scene.prepare();
        HeadlessGuiFrames.layout(scene);
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}

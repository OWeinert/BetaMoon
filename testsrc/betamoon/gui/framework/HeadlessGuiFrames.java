package betamoon.gui.framework;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Geometry/input simulation only. Actual GL and pixels belong to the offscreen
 * fixtures.
 */
public final class HeadlessGuiFrames {
    private HeadlessGuiFrames() {
    }

    public static void layout(GuiScene scene) {
        try {
            Method layout = GuiScene.class.getDeclaredMethod("ensureLayout");
            layout.setAccessible(true);
            layout.invoke(scene);
            GuiElement root = scene.getContent();
            while (root.getParent() != null) {
                root = root.getParent();
            }
            Field displayed = GuiScene.class.getDeclaredField("displayedFrame");
            displayed.setAccessible(true);
            displayed.set(scene, new GuiFrameSnapshot(root, 1, scene.getContext()));
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot simulate a displayed geometry frame", failure);
        }
    }

    public static void renderRecordingElements(GuiScene scene, int x, int y, float partialTick) {
        scene.prepare();
        layout(scene);
        scene.getContext().updateFrame(x, y, partialTick, 0);
        scene.getContent().render(scene.getContext());
    }
}

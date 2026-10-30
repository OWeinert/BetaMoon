package betamoon.gui.framework;

import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.src.FontRenderer;
import betamoon.gui.graphics.GraphicsClip;
import betamoon.gui.graphics.GraphicsTransform;

/** Complete per-scene environment supplied to GUI elements. */
public final class GuiContext {
    private final GuiScene scene;
    private final GuiRenderer renderer;
    private Minecraft minecraft;
    private FontRenderer font;
    private int screenWidth;
    private int screenHeight;
    private int displayWidth;
    private int displayHeight;
    private int mouseX;
    private int mouseY;
    private float partialTicks;
    private long currentTimeMillis;
    private GuiFrameSnapshot.Node currentNode;

    GuiContext(GuiScene scene, GuiRenderer renderer) {
        this.scene = scene;
        this.renderer = renderer;
    }

    public Minecraft getMinecraft() {
        return minecraft;
    }

    public FontRenderer getFont() {
        return font;
    }

    public GuiRenderer getRenderer() {
        return renderer;
    }

    public int getScreenWidth() {
        return screenWidth;
    }

    public int getScreenHeight() {
        return screenHeight;
    }

    public int getDisplayWidth() {
        return displayWidth;
    }

    public int getDisplayHeight() {
        return displayHeight;
    }

    public int getMouseX() {
        return currentNode == null ? mouseX : (int) Math.floor(currentNode.transform.localX(mouseX));
    }

    public int getMouseY() {
        return currentNode == null ? mouseY : (int) Math.floor(currentNode.transform.localY(mouseY));
    }

    public float getPartialTicks() {
        return partialTicks;
    }

    public long getCurrentTimeMillis() {
        return currentTimeMillis;
    }

    public double getOpacity() {
        return currentNode == null ? 1 : currentNode.opacity;
    }

    public GuiFrameContent getFrameContent() {
        return currentNode == null ? null : currentNode.content;
    }

    public GraphicsTransform getElementTransform() {
        return currentNode == null ? GraphicsTransform.IDENTITY : currentNode.transform;
    }

    public GraphicsClip getElementClip() {
        return currentNode == null ? new GraphicsClip(0, 0, screenWidth, screenHeight) : currentNode.hitClip;
    }

    public boolean isFocused(GuiElement element) {
        return scene.getFocusedElement() == element;
    }

    public void requestFocus(GuiElement element) {
        scene.requestFocus(element);
    }

    public void capturePointer(GuiElement element) {
        scene.capturePointer(element);
    }

    public void releasePointer(GuiElement element) {
        scene.releasePointer(element);
    }

    public void showTooltip(String text) {
        if (text != null && !text.isEmpty()) {
            showTooltip(Arrays.asList(text));
        }
    }

    public void showTooltip(List<String> lines) {
        scene.showTooltip(lines);
    }

    /** Runs an action from the next update after at least one rendered frame. */
    public void deferAfterRender(GuiAction action) {
        scene.deferAfterRender(action);
    }

    public void requestLayout() {
        scene.requestLayout();
    }

    void updateEnvironment(Minecraft minecraft, FontRenderer font, int screenWidth, int screenHeight, int displayWidth,
            int displayHeight) {
        this.minecraft = minecraft;
        this.font = font;
        this.screenWidth = Math.max(0, screenWidth);
        this.screenHeight = Math.max(0, screenHeight);
        this.displayWidth = Math.max(0, displayWidth);
        this.displayHeight = Math.max(0, displayHeight);
        renderer.updateEnvironment(minecraft, font, screenWidth, screenHeight, displayWidth, displayHeight);
    }

    void updateFrame(int mouseX, int mouseY, float partialTicks, long currentTimeMillis) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.partialTicks = partialTicks;
        this.currentTimeMillis = currentTimeMillis;
    }

    GuiFrameSnapshot.Node enterElement(GuiElement element) {
        GuiFrameSnapshot.Node previous = currentNode;
        GuiFrameSnapshot.Node next = frameNode(element);
        currentNode = next == null ? previous : next;
        return previous;
    }

    GuiFrameSnapshot.Node enterInputElement(GuiElement element) {
        GuiFrameSnapshot.Node previous = currentNode;
        GuiFrameSnapshot frame = scene.getDisplayedFrame();
        currentNode = frame == null ? null : frame.node(element);
        return previous;
    }

    GuiFrameSnapshot.Node currentFrameNode() {
        return currentNode;
    }

    GuiFrameSnapshot.Node frameNode(GuiElement element) {
        return scene.renderingFrame() == null ? null : scene.renderingFrame().node(element);
    }

    void leaveElement(GuiFrameSnapshot.Node previous) {
        currentNode = previous;
    }
}

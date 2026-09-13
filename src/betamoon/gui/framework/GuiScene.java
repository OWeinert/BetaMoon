package betamoon.gui.framework;

import betamoon.gui.framework.GuiContainer.SceneRoot;
import betamoon.gui.framework.GuiGeometry.Rect;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.src.FontRenderer;
import org.lwjgl.input.Keyboard;

/** Owns one component tree and coordinates layout, rendering, focus, and input. */
public final class GuiScene {
    private final SceneRoot root = new SceneRoot(this);
    private final GuiRenderer renderer;
    private final GuiContext context;
    private final List<GuiAction> waitingForRender = new ArrayList<GuiAction>();
    private final List<GuiAction> readyActions = new ArrayList<GuiAction>();
    private List<String> tooltipLines = Collections.emptyList();
    private GuiElement focusedElement;
    private GuiElement pointerCapture;
    private int pointerCaptureButton = -1;
    private boolean layoutRequested = true;
    private int laidOutWidth = -1;
    private int laidOutHeight = -1;

    public GuiScene() {
        this(GuiTheme.DEFAULT);
    }

    public GuiScene(GuiTheme theme) {
        renderer = new GuiRenderer(theme);
        context = new GuiContext(this, renderer);
    }

    public GuiContext getContext() {
        return context;
    }

    public GuiElement getContent() {
        List<GuiElement> children = root.getChildren();
        return children.isEmpty() ? null : children.get(0);
    }

    public void setContent(GuiElement content) {
        root.clear();
        if (content != null) {
            root.add(content);
        }
        requestLayout();
    }

    public void updateEnvironment(Minecraft minecraft, FontRenderer font, int screenWidth, int screenHeight,
            int displayWidth, int displayHeight) {
        if (font != context.getFont()) {
            requestLayout();
        }
        context.updateEnvironment(minecraft, font, screenWidth, screenHeight, displayWidth, displayHeight);
        if (screenWidth != laidOutWidth || screenHeight != laidOutHeight) {
            requestLayout();
        }
    }

    public void render(int mouseX, int mouseY, float partialTicks) {
        context.updateFrame(mouseX, mouseY, partialTicks, System.currentTimeMillis());
        tooltipLines = Collections.emptyList();
        renderer.beginFrame(context.getMinecraft(), context.getFont(), context.getScreenWidth(),
                context.getScreenHeight(), context.getDisplayWidth(), context.getDisplayHeight());
        try {
            ensureLayout();
            root.render(context);
            if (!tooltipLines.isEmpty()) {
                renderer.drawTooltip(tooltipLines, mouseX, mouseY);
            }
        } finally {
            renderer.endFrame();
        }
        if (!waitingForRender.isEmpty()) {
            readyActions.addAll(waitingForRender);
            waitingForRender.clear();
        }
    }

    public void update() {
        if (readyActions.isEmpty()) {
            return;
        }
        List<GuiAction> actions = new ArrayList<GuiAction>(readyActions);
        readyActions.clear();
        for (int i = 0; i < actions.size(); i++) {
            actions.get(i).run();
        }
    }

    public boolean mousePressed(int mouseX, int mouseY, int button) {
        GuiInputEvent event = GuiInputEvent.pointerDown(mouseX, mouseY, button);
        List<GuiElement> path = hitPath(mouseX, mouseY);
        focusFirstFocusable(path);
        return dispatchPath(path, event);
    }

    public boolean mouseReleased(int mouseX, int mouseY, int button) {
        if (button < 0) {
            return false;
        }
        GuiInputEvent event = GuiInputEvent.pointerUp(mouseX, mouseY, button);
        if (pointerCapture != null) {
            if (button != pointerCaptureButton) {
                return false;
            }
            GuiElement captured = pointerCapture;
            pointerCapture = null;
            pointerCaptureButton = -1;
            return dispatchFromElement(captured, event);
        }
        return dispatchPath(hitPath(mouseX, mouseY), event);
    }

    public boolean mouseDragged(int mouseX, int mouseY, boolean mouseDown) {
        GuiInputEvent event = GuiInputEvent.pointerDrag(mouseX, mouseY, mouseDown);
        if (pointerCapture != null) {
            return dispatchFromElement(pointerCapture, event);
        }
        return dispatchPath(hitPath(mouseX, mouseY), event);
    }

    public boolean mouseScrolled(int mouseX, int mouseY, int wheelDelta, boolean shiftDown) {
        if (wheelDelta == 0) {
            return false;
        }
        return dispatchPath(hitPath(mouseX, mouseY), GuiInputEvent.scroll(mouseX, mouseY, wheelDelta, shiftDown));
    }

    public boolean keyTyped(char typedChar, int keyCode, boolean shiftDown) {
        if (keyCode == Keyboard.KEY_TAB && moveFocus(shiftDown)) {
            return true;
        }
        if (focusedElement == null) {
            return false;
        }
        return dispatchFromElement(focusedElement, GuiInputEvent.keyTyped(typedChar, keyCode, shiftDown));
    }

    public GuiElement getFocusedElement() {
        return focusedElement;
    }

    void requestFocus(GuiElement element) {
        if (element != null && element.belongsTo(this) && element.isVisible() && element.isEnabled()
                && element.isFocusable()) {
            focusedElement = element;
        }
    }

    void clearFocus(GuiElement element) {
        if (focusedElement == element) {
            focusedElement = null;
        }
    }

    void capturePointer(GuiElement element) {
        if (element != null && element.belongsTo(this) && element.isVisible() && element.isEnabled()) {
            pointerCapture = element;
            pointerCaptureButton = 0;
        }
    }

    void releasePointer(GuiElement element) {
        if (pointerCapture == element) {
            pointerCapture = null;
            pointerCaptureButton = -1;
        }
    }

    void releaseOwnership(GuiElement element) {
        if (element == null) {
            return;
        }
        if (focusedElement == element || isDescendantOf(focusedElement, element)) {
            focusedElement = null;
        }
        if (pointerCapture == element || isDescendantOf(pointerCapture, element)) {
            pointerCapture = null;
            pointerCaptureButton = -1;
        }
    }

    void showTooltip(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        tooltipLines = new ArrayList<String>(lines);
    }

    void deferAfterRender(GuiAction action) {
        if (action != null) {
            waitingForRender.add(action);
        }
    }

    void requestLayout() {
        layoutRequested = true;
    }

    private void ensureLayout() {
        if (!layoutRequested) {
            return;
        }
        int width = context.getScreenWidth();
        int height = context.getScreenHeight();
        Rect screenBounds = Rect.fromPositionAndSize(0, 0, width, height);
        root.measure(context, GuiGeometry.Constraints.tight(width, height));
        root.arrange(context, screenBounds);
        laidOutWidth = width;
        laidOutHeight = height;
        layoutRequested = false;
    }

    private List<GuiElement> hitPath(int mouseX, int mouseY) {
        List<GuiElement> path = new ArrayList<GuiElement>();
        root.collectHitPath(mouseX, mouseY, path);
        return path;
    }

    private boolean dispatchPath(List<GuiElement> path, GuiInputEvent event) {
        for (int i = 0; i < path.size(); i++) {
            if (path.get(i).dispatchInput(context, event)) {
                return true;
            }
        }
        return false;
    }

    private boolean dispatchFromElement(GuiElement element, GuiInputEvent event) {
        GuiElement current = element;
        while (current != null) {
            if (current.dispatchInput(context, event)) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private void focusFirstFocusable(List<GuiElement> path) {
        for (int i = 0; i < path.size(); i++) {
            GuiElement element = path.get(i);
            if (element.isFocusable() && element.isEnabled()) {
                focusedElement = element;
                return;
            }
        }
        focusedElement = null;
    }

    private boolean moveFocus(boolean backwards) {
        List<GuiElement> focusable = new ArrayList<GuiElement>();
        root.collectFocusableElements(focusable);
        if (focusable.isEmpty()) {
            focusedElement = null;
            return false;
        }
        int currentIndex = focusable.indexOf(focusedElement);
        int nextIndex;
        if (backwards) {
            nextIndex = currentIndex <= 0 ? focusable.size() - 1 : currentIndex - 1;
        } else {
            nextIndex = currentIndex < 0 || currentIndex >= focusable.size() - 1 ? 0 : currentIndex + 1;
        }
        focusedElement = focusable.get(nextIndex);
        return true;
    }

    private static boolean isDescendantOf(GuiElement element, GuiElement possibleAncestor) {
        GuiElement current = element;
        while (current != null) {
            if (current == possibleAncestor) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }
}

package betamoon.gui.framework;

import betamoon.gui.framework.GuiContainer.SceneRoot;
import betamoon.gui.framework.GuiGeometry.Rect;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.src.FontRenderer;
import org.lwjgl.input.Keyboard;

/**
 * Owns one component tree and coordinates layout, rendering, focus, and input.
 */
public final class GuiScene {
    private final SceneRoot root = new SceneRoot(this);
    private final GuiRenderer renderer;
    private final GuiContext context;
    private final List<GuiAction> waitingForRender = new ArrayList<GuiAction>();
    private final List<GuiAction> readyActions = new ArrayList<GuiAction>();
    private final List<Runnable> mutations = new ArrayList<>();
    private final Set<Integer> ownedGestures = new HashSet<>();
    private final Set<Integer> cancelledGestures = new HashSet<>();
    private final Map<Integer, GuiElement> gestureTargets = new HashMap<>();
    private List<String> tooltipLines = Collections.emptyList();
    private GuiElement focusedElement;
    private GuiElement pointerCapture;
    private int pointerCaptureButton = -1;
    private boolean layoutRequested = true;
    private int laidOutWidth = -1;
    private int laidOutHeight = -1;
    private int dispatchDepth;
    private int dispatchButton = -1;
    private GuiElement handledTarget;
    private GuiFrameSnapshot displayedFrame;
    private GuiFrameSnapshot renderingFrame;
    private long frameRevision;
    private boolean frameDirty = true;

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
        if (content == getContent()) {
            return;
        }
        if (content != null && content.getParent() != null) {
            throw new IllegalStateException("GUI content already belongs to a container");
        }
        if (deferMutation(() -> setContent(content))) {
            return;
        }
        root.clear();
        if (content != null) {
            root.add(content);
        }
        requestLayout();
        displayedFrame = null;
    }

    public void updateEnvironment(Minecraft minecraft, FontRenderer font, int screenWidth, int screenHeight,
            int displayWidth, int displayHeight) {
        if (context.getScreenWidth() != screenWidth || context.getScreenHeight() != screenHeight
                || context.getDisplayWidth() != displayWidth || context.getDisplayHeight() != displayHeight) {
            displayedFrame = null;
            cancelPointerCapture();
        }
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
            renderingFrame = displayedFrame == null || frameDirty
                    ? new GuiFrameSnapshot(root, ++frameRevision, context)
                    : displayedFrame;
            frameDirty = false;
            root.render(context);
            if (!tooltipLines.isEmpty()) {
                renderer.drawTooltip(tooltipLines, mouseX, mouseY);
            }
            displayedFrame = renderingFrame;
        } catch (RuntimeException | Error failure) {
            displayedFrame = null;
            throw failure;
        } finally {
            renderingFrame = null;
            renderer.endFrame();
        }
        if (!waitingForRender.isEmpty()) {
            readyActions.addAll(waitingForRender);
            waitingForRender.clear();
        }
    }

    /**
     * End an interrupted drag when this screen is covered or closed, retaining
     * keyboard focus.
     */
    public void cancelPointerCapture() {
        if (pointerCapture != null) {
            GuiElement captured = pointerCapture;
            int button = pointerCaptureButton;
            pointerCapture = null;
            pointerCaptureButton = -1;
            gestureTargets.remove(button);
            cancelledGestures.add(button);
            dispatchDepth++;
            try {
                captured.cancelPointer(context, button);
            } finally {
                finishDispatch();
            }
        }
    }

    public void update() {
        runActions();
        prepare();
    }

    void runActions() {
        List<GuiAction> actions = new ArrayList<GuiAction>(readyActions);
        readyActions.clear();
        for (int i = 0; i < actions.size(); i++) {
            actions.get(i).run();
        }
    }

    /**
     * Samples Java suppliers at an update boundary, without executing queued
     * actions.
     */
    public void prepare() {
        dispatchDepth++;
        try {
            root.update(context);
        } finally {
            finishDispatch();
        }
    }

    public boolean mousePressed(int mouseX, int mouseY, int button) {
        if (displayedFrame == null) {
            return false;
        }
        GuiInputEvent event = GuiInputEvent.pointerDown(mouseX, mouseY, button);
        cancelledGestures.remove(button);
        List<GuiElement> path = hitPath(mouseX, mouseY);
        focusFirstFocusable(path);
        boolean handled = dispatchPath(path, event);
        if (handled) {
            ownedGestures.add(button);
            if (!cancelledGestures.contains(button)) {
                gestureTargets.put(button, handledTarget);
            }
        }
        return handled;
    }

    public boolean mouseReleased(int mouseX, int mouseY, int button) {
        if (button < 0) {
            return false;
        }
        GuiInputEvent event = GuiInputEvent.pointerUp(mouseX, mouseY, button);
        boolean owned = ownedGestures.remove(button);
        boolean cancelled = cancelledGestures.remove(button);
        GuiElement target = gestureTargets.remove(button);
        if (cancelled) {
            return true;
        }
        if (pointerCapture != null) {
            if (button != pointerCaptureButton) {
                return owned;
            }
            GuiElement captured = pointerCapture;
            pointerCapture = null;
            pointerCaptureButton = -1;
            return dispatchFromElement(captured, event) || owned;
        }
        if (owned) {
            if (target != null && target.belongsTo(this) && target.isAvailable()) {
                dispatchFromElement(target, event);
            }
            return true;
        }
        return dispatchPath(hitPath(mouseX, mouseY), event);
    }

    public boolean mouseDragged(int mouseX, int mouseY, boolean mouseDown) {
        if (pointerCapture != null) {
            return dispatchFromElement(pointerCapture,
                    GuiInputEvent.pointerDrag(mouseX, mouseY, pointerCaptureButton, mouseDown));
        }
        if (ownedGestures.contains(0)) {
            return true;
        }
        return dispatchPath(hitPath(mouseX, mouseY), GuiInputEvent.pointerDrag(mouseX, mouseY, mouseDown));
    }

    int capturedPointerButton() {
        return pointerCaptureButton;
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

    public boolean keyReleased(int keyCode, boolean shiftDown) {
        return focusedElement != null && dispatchFromElement(focusedElement, GuiInputEvent.keyUp(keyCode, shiftDown));
    }

    public GuiElement getFocusedElement() {
        return focusedElement;
    }

    public GuiFrameSnapshot getDisplayedFrame() {
        return displayedFrame;
    }

    GuiFrameSnapshot renderingFrame() {
        return renderingFrame;
    }

    void requestFocus(GuiElement element) {
        if (dispatchDepth > 0) {
            mutations.add(() -> requestFocus(element));
            return;
        }
        if (element != null && element.belongsTo(this) && element.isAvailable() && element.isFocusable()) {
            changeFocus(element);
        }
    }

    void clearFocus(GuiElement element) {
        if (focusedElement == element) {
            changeFocus(null);
        }
    }

    void capturePointer(GuiElement element) {
        if (element != null && element.belongsTo(this) && element.isAvailable() && dispatchButton >= 0) {
            if (pointerCapture != null && pointerCapture != element) {
                cancelPointerCapture();
            }
            cancelledGestures.remove(dispatchButton);
            pointerCapture = element;
            pointerCaptureButton = dispatchButton;
            ownedGestures.add(dispatchButton);
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
            changeFocus(null);
        }
        if (pointerCapture == element || isDescendantOf(pointerCapture, element)) {
            cancelPointerCapture();
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
        requestFrame();
    }

    void requestFrame() {
        frameDirty = true;
    }

    boolean deferMutation(Runnable mutation) {
        if (renderingFrame != null) {
            throw new IllegalStateException("GUI tree mutations must run at an update/input boundary");
        }
        if (dispatchDepth == 0) {
            return false;
        }
        mutations.add(mutation);
        return true;
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
        return displayedFrame == null ? Collections.emptyList() : displayedFrame.hitPath(mouseX, mouseY);
    }

    private boolean dispatchPath(List<GuiElement> path, GuiInputEvent event) {
        int previousButton = dispatchButton;
        dispatchButton = event.isPointerEvent() ? event.getButton() : -1;
        dispatchDepth++;
        GuiFrameSnapshot frame = displayedFrame;
        try {
            for (int i = path.size() - 1; i > 0; i--) {
                if (path.get(i).belongsTo(this)
                        && (!event.isPointerEvent() || frame != null && frame.permits(path.get(i))) && path.get(i)
                                .dispatchCapture(context, frame == null ? event : frame.event(path.get(i), event))) {
                    handledTarget = path.get(i);
                    return true;
                }
            }
            for (int i = 0; i < path.size(); i++) {
                if (path.get(i).belongsTo(this)
                        && (!event.isPointerEvent() || frame != null && frame.permits(path.get(i))) && path.get(i)
                                .dispatchInput(context, frame == null ? event : frame.event(path.get(i), event))) {
                    handledTarget = path.get(i);
                    return true;
                }
            }
            return false;
        } finally {
            dispatchButton = previousButton;
            finishDispatch();
        }
    }

    private boolean dispatchFromElement(GuiElement element, GuiInputEvent event) {
        List<GuiElement> path = new ArrayList<>();
        GuiElement current = element;
        while (current != null) {
            path.add(current);
            current = current.getParent();
        }
        return dispatchPath(path, event);
    }

    private void finishDispatch() {
        dispatchDepth--;
        if (dispatchDepth == 0 && !mutations.isEmpty()) {
            List<Runnable> pending = new ArrayList<>(mutations);
            mutations.clear();
            for (Runnable mutation : pending) {
                mutation.run();
            }
        }
    }

    private void focusFirstFocusable(List<GuiElement> path) {
        for (int i = 0; i < path.size(); i++) {
            GuiElement element = path.get(i);
            if (element.isFocusable() && element.isEnabled()) {
                changeFocus(element);
                return;
            }
        }
        changeFocus(null);
    }

    private boolean moveFocus(boolean backwards) {
        List<GuiElement> focusable;
        if (displayedFrame == null) {
            focusable = new ArrayList<>();
            root.collectFocusableElements(focusable);
        } else {
            focusable = displayedFrame.focusable();
        }
        if (focusable.isEmpty()) {
            changeFocus(null);
            return false;
        }
        int currentIndex = focusable.indexOf(focusedElement);
        int nextIndex;
        if (backwards) {
            nextIndex = currentIndex <= 0 ? focusable.size() - 1 : currentIndex - 1;
        } else {
            nextIndex = currentIndex < 0 || currentIndex >= focusable.size() - 1 ? 0 : currentIndex + 1;
        }
        changeFocus(focusable.get(nextIndex));
        return true;
    }

    private void changeFocus(GuiElement element) {
        if (focusedElement == element) {
            return;
        }
        GuiElement previous = focusedElement;
        focusedElement = element;
        if (previous != null) {
            previous.onFocusChanged(context, false);
        }
        if (element != null) {
            element.onFocusChanged(context, true);
        }
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

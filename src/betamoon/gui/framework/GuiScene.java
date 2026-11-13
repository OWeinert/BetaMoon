package betamoon.gui.framework;

import betamoon.gui.graphics.GraphicsClip;
import betamoon.gui.framework.GuiContainer.SceneRoot;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.presentation.GuiPresentationTree;
import betamoon.interop.runtime.NativeContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
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
    private GuiFrameSnapshot hoverFrame;
    private GuiElement hoverScope;
    private int hoverX;
    private int hoverY;
    private List<GuiElement> hoverPath = Collections.emptyList();
    private long frameRevision;
    private boolean frameDirty = true;
    private final List<GuiLayer> layers = new ArrayList<>();
    private GuiPresentationTree document;
    private boolean painting;
    private boolean layingOut;
    private boolean preparingVisuals;
    private enum PaintPass {
        ALL, CONTENT, LAYERS
    }
    private PaintPass paintPass = PaintPass.ALL;
    private Predicate<GuiElement> layerPaintEligibility = owner -> true;
    private final List<Runnable> deferredEffects = new ArrayList<>();
    private List<GuiElement> inputRegions;

    /**
     * Restricts an attached surface's dispatch and focus to declared, displayed
     * regions.
     */
    public void runRegionalInput(List<GuiElement> regions, Runnable input) {
        List<GuiElement> previous = inputRegions;
        inputRegions = new ArrayList<>(regions);
        hoverFrame = null;
        try {
            if (focusedElement != null && !inputAllowed(focusedElement)) {
                changeFocus(null);
            }
            if (pointerCapture != null && !inputAllowed(pointerCapture)) {
                cancelPointerCapture();
            }
            input.run();
        } finally {
            inputRegions = previous;
            hoverFrame = null;
        }
    }

    /**
     * Releases transient host input while retaining suppression through physical
     * release.
     */
    public void cancelInput() {
        cancelInput(root);
    }

    public void cancelInput(GuiElement owner) {
        cancelGesturesIn(owner);
        releaseOwnership(owner);
    }

    private boolean inputAllowed(GuiElement element) {
        if (inputRegions == null || modalScope() != null && GuiFrameSnapshot.inside(element, modalScope())) {
            return true;
        }
        for (GuiElement region : inputRegions) {
            if (GuiFrameSnapshot.inside(element, region)) {
                return true;
            }
        }
        return false;
    }

    public void setDocument(GuiPresentationTree replacement) {
        if (deferMutation(() -> setDocument(replacement))) {
            return;
        }
        GuiPresentationTree previous = document;
        GuiElement restore = replacement == null || previous == null
                ? null
                : replacement.compatibleElement(previous, focusedElement);
        setContent(replacement == null ? null : replacement.root());
        document = replacement;
        if (replacement != null) {
            replacement.activate();
        }
        if (restore != null) {
            requestFocus(restore);
        }
    }

    /**
     * Prepares an unattached revision using this host's actual dimensions without
     * activating it.
     */
    public void prepareDetached(GuiPresentationTree candidate) {
        prepareDetached(candidate, null);
    }

    /**
     * Preflight a native host's actual bounded document region before publication.
     */
    public void prepareDetached(GuiPresentationTree candidate, Rect viewport) {
        GuiScene preparation = new GuiScene(renderer.getTheme());
        preparation.updateEnvironment(context.getMinecraft(), context.getFont(), context.getScreenWidth(),
                context.getScreenHeight(), context.getDisplayWidth(), context.getDisplayHeight());
        GuiContainer boundary = new GuiContainer() {
            @Override
            protected void arrangeChildren(GuiContext context) {
                GuiElement content = candidate.root();
                Rect bounds = viewport == null ? getBounds() : viewport;
                content.measure(context, GuiGeometry.Constraints.upTo(bounds.getWidth(), bounds.getHeight()));
                content.arrange(context,
                        content instanceof GuiViewportLayout
                                ? ((GuiViewportLayout) content).viewportBounds(context, bounds)
                                : bounds);
            }
        };
        boundary.add(candidate.root());
        preparation.setContent(boundary);
        try {
            preparation.prepare();
        } finally {
            preparation.setContent(null);
            boundary.clear();
        }
    }

    public GuiLayer openLayer(GuiElement content, boolean modal) {
        return openLayer(content, modal, modal, false);
    }

    private GuiLayer openLayer(GuiElement content, boolean modal, boolean dim, boolean closeOnTab) {
        if (content == null || content.getParent() != null) {
            throw new IllegalArgumentException("A layer requires unattached content");
        }
        GuiLayer layer = new GuiLayer(this, content, modal, focusedElement, dim, closeOnTab);
        Runnable attach = () -> {
            if (!layer.isActive()) {
                return;
            }
            if (layers.size() >= 32) {
                throw new IllegalStateException("GUI layer limit reached: 32");
            }
            cancelPointerCapture();
            root.add(content);
            layers.add(layer);
            changeFocus(null);
            List<GuiElement> focusable = new ArrayList<>();
            content.collectFocusableElements(focusable);
            if (!focusable.isEmpty()) {
                requestFocus(focusable.get(0));
            }
        };
        if (!deferMutation(attach)) {
            attach.run();
        }
        return layer;
    }

    public GuiLayer openLayer(GuiPresentationTree document, boolean modal) {
        GuiLayer layer = openLayer(new GuiLayerDocument(document), modal);
        document.activate();
        return layer;
    }

    public GuiLayer openPopover(GuiElement anchor, GuiElement content, boolean atPointer, int x, int y) {
        if (anchor == null || !anchor.belongsTo(this) || !anchor.isAvailable()) {
            throw new IllegalArgumentException("Popover anchor must be an available element in this scene");
        }
        GuiAnchoredPopover portal = new GuiAnchoredPopover(anchor, content, atPointer, x, y);
        GuiLayer layer = openLayer(portal, true, false, true);
        layer.ownedBy(anchor);
        portal.bind(layer);
        return layer;
    }

    public GuiLayer openPopover(GuiElement anchor, GuiPresentationTree document) {
        GuiLayer layer = openPopover(anchor, new GuiLayerDocument(document), false, 0, 0);
        document.activate();
        return layer;
    }

    void replaceLayer(GuiLayer layer, GuiPresentationTree replacement) {
        if (!layer.isActive() || !layers.contains(layer) || !(layer.content instanceof GuiLayer.Document)) {
            throw new IllegalStateException("Layer document is unavailable");
        }
        if (deferMutation(() -> replaceLayer(layer, replacement))) {
            return;
        }
        GuiPresentationTree previous = layer.document();
        GuiElement restore = previous == null ? null : replacement.compatibleElement(previous, focusedElement);
        ((GuiLayer.Document) layer.content).replace(replacement);
        replacement.activate();
        if (restore != null) {
            requestFocus(restore);
        }
        if (previous != null) {
            previous.close();
        }
        requestLayout();
    }

    boolean dimBefore(GuiElement element) {
        for (GuiLayer layer : layers) {
            if (layer.isActive() && layer.dim && layerPaintEligibility.test(layer.paintOwner())) {
                return layer.content == element;
            }
        }
        return false;
    }

    boolean paints(GuiElement element) {
        for (GuiLayer layer : layers) {
            if (layer.content == element) {
                return layer.isActive() && paintPass != PaintPass.CONTENT
                        && layerPaintEligibility.test(layer.paintOwner());
            }
        }
        return paintPass != PaintPass.LAYERS;
    }

    public boolean hasModalLayer() {
        return modalScope() != null;
    }

    void closeLayer(GuiLayer layer) {
        layer.content.setVisible(false);
        Runnable detach = () -> {
            boolean top = !layers.isEmpty() && layers.get(layers.size() - 1) == layer;
            layers.remove(layer);
            root.remove(layer.content);
            layer.finishClose();
            if (top) {
                requestFocus(layer.opener);
                if (focusedElement == null) {
                    GuiElement scope = modalScope() == null ? getContent() : modalScope();
                    if (scope != null) {
                        List<GuiElement> focusable = new ArrayList<>();
                        scope.collectFocusableElements(focusable);
                        if (!focusable.isEmpty()) {
                            requestFocus(focusable.get(0));
                        }
                    }
                }
            }
        };
        if (!deferMutation(detach)) {
            detach.run();
        }
    }

    private GuiElement modalScope() {
        for (int i = layers.size() - 1; i >= 0; i--) {
            GuiLayer layer = layers.get(i);
            if (layer.isActive() && layer.modal) {
                return layer.content;
            }
        }
        return null;
    }

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
        GuiPresentationTree previous = document;
        document = null;
        for (GuiLayer layer : new ArrayList<>(layers)) {
            layer.close();
        }
        root.clear();
        if (content != null) {
            root.add(content);
        }
        requestLayout();
        displayedFrame = null;
        if (previous != null) {
            previous.close();
        }
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
        renderPass(mouseX, mouseY, partialTicks, PaintPass.ALL);
    }

    /**
     * Native hosts can paint items between content and the scene's portal layers.
     */
    public void renderContent(int mouseX, int mouseY, float partialTicks) {
        renderPass(mouseX, mouseY, partialTicks, PaintPass.CONTENT);
    }

    /**
     * Replays layers using the content pass's exact displayed geometry and frame
     * time.
     */
    public void renderLayers() {
        if (displayedFrame != null) {
            renderPass(context.getMouseX(), context.getMouseY(), context.getPartialTicks(), PaintPass.LAYERS, true);
        }
    }

    public void renderLayers(Predicate<GuiElement> eligibility) {
        Predicate<GuiElement> previous = layerPaintEligibility;
        layerPaintEligibility = eligibility;
        try {
            renderLayers();
        } finally {
            layerPaintEligibility = previous;
        }
    }

    /**
     * Replays content from the captured frame without resampling time or geometry.
     */
    public void renderDisplayedContent() {
        if (displayedFrame != null) {
            renderPass(context.getMouseX(), context.getMouseY(), context.getPartialTicks(), PaintPass.CONTENT, true);
        }
    }

    private void renderPass(int mouseX, int mouseY, float partialTicks, PaintPass pass) {
        renderPass(mouseX, mouseY, partialTicks, pass, false);
    }

    private void renderPass(int mouseX, int mouseY, float partialTicks, PaintPass pass, boolean replay) {
        if (!replay) {
            context.updateFrame(mouseX, mouseY, partialTicks, System.currentTimeMillis());
            tooltipLines = Collections.emptyList();
        }
        renderer.beginFrame(context.getMinecraft(), context.getFont(), context.getScreenWidth(),
                context.getScreenHeight(), context.getDisplayWidth(), context.getDisplayHeight());
        paintPass = pass;
        painting = true;
        try {
            if (!replay) {
                if (document != null) {
                    document.sample();
                }
                for (GuiLayer layer : new ArrayList<>(layers)) {
                    if (layer.isActive() && layer.document() != null) {
                        layer.document().sample();
                    }
                }
                ensureLayout();
            }
            if (replay) {
                renderingFrame = displayedFrame;
            } else {
                renderingFrame = displayedFrame == null || frameDirty
                        ? new GuiFrameSnapshot(root, ++frameRevision, context)
                        : displayedFrame;
            }
            if (!replay) {
                frameDirty = false;
            }
            hoverFrame = null;
            root.render(context);
            if (pass != PaintPass.CONTENT && !tooltipLines.isEmpty()) {
                renderer.drawTooltip(tooltipLines, mouseX, mouseY);
            }
            displayedFrame = renderingFrame;
        } catch (RuntimeException | Error failure) {
            displayedFrame = null;
            throw failure;
        } finally {
            renderingFrame = null;
            try {
                renderer.endFrame();
            } finally {
                painting = false;
                paintPass = PaintPass.ALL;
            }
        }
        if (pass != PaintPass.CONTENT && !waitingForRender.isEmpty()) {
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
                Runnable cancel = () -> captured.cancelPointer(context, button);
                if (painting || layingOut || preparingVisuals) {
                    deferredEffects.add(cancel);
                } else {
                    cancel.run();
                }
            } finally {
                finishDispatch();
            }
        }
    }

    public void update() {
        runActions();
        prepare();
    }

    public void runActions() {
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
        if (!GuiPreparationPhase.active() && !NativeContext.hasFrame()) {
            try (GuiPreparationPhase preparation = GuiPreparationPhase.open()) {
                preparePublished();
            }
        } else {
            preparePublished();
        }
    }

    private void preparePublished() {
        if (!deferredEffects.isEmpty()) {
            List<Runnable> effects = new ArrayList<>(deferredEffects);
            deferredEffects.clear();
            for (Runnable effect : effects) {
                effect.run();
            }
        }
        if (document != null) {
            document.update();
        }
        for (GuiLayer layer : new ArrayList<>(layers)) {
            if (!layer.isActive()) {
                layer.close();
                continue;
            }
            if (layer.isActive() && layer.document() != null) {
                layer.document().update();
            }
        }
        dispatchDepth++;
        try {
            root.update(context);
        } finally {
            finishDispatch();
        }
        ensureLayout();
        if (GuiPreparationPhase.active()) {
            dispatchDepth++;
            try {
                root.prepareStructure(context);
            } finally {
                finishDispatch();
            }
            ensureLayout();
        }
        preparingVisuals = true;
        try {
            root.prepareVisuals(context);
        } finally {
            preparingVisuals = false;
        }
    }

    public boolean mousePressed(int mouseX, int mouseY, int button) {
        if (displayedFrame == null) {
            if (modalScope() != null) {
                ownedGestures.add(button);
                return true;
            }
            return false;
        }
        GuiInputEvent event = GuiInputEvent.pointerDown(mouseX, mouseY, button);
        cancelledGestures.remove(button);
        GuiElement scope = modalScope();
        List<GuiElement> path = hitPath(mouseX, mouseY);
        if (!path.isEmpty() || scope == null) {
            focusFirstFocusable(path);
        }
        handledTarget = null;
        boolean handled = dispatchPath(path, event) || scope != null;
        if (handled) {
            ownedGestures.add(button);
            if (!cancelledGestures.contains(button) && handledTarget != null) {
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

    public int capturedPointerButton() {
        return pointerCaptureButton;
    }

    public boolean mouseScrolled(int mouseX, int mouseY, int wheelDelta, boolean shiftDown) {
        if (wheelDelta == 0) {
            return false;
        }
        GuiElement scope = modalScope();
        return dispatchPath(hitPath(mouseX, mouseY), GuiInputEvent.scroll(mouseX, mouseY, wheelDelta, shiftDown))
                || scope != null;
    }

    public boolean keyTyped(char typedChar, int keyCode, boolean shiftDown) {
        boolean control = Keyboard.isCreated()
                && (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL));
        return keyTyped(typedChar, keyCode, shiftDown, control);
    }

    public boolean keyTyped(char typedChar, int keyCode, boolean shiftDown, boolean controlDown) {
        if (keyCode == Keyboard.KEY_TAB && !layers.isEmpty()) {
            GuiLayer top = layers.get(layers.size() - 1);
            if (top.closeOnTab) {
                top.close();
            }
        }
        GuiElement scope = modalScope();
        if (keyCode == Keyboard.KEY_TAB && moveFocus(shiftDown)) {
            return true;
        }
        boolean handled = focusedElement != null && dispatchFromElement(focusedElement,
                GuiInputEvent.keyTyped(typedChar, keyCode, shiftDown, controlDown));
        if (!handled && keyCode == Keyboard.KEY_ESCAPE && !layers.isEmpty()) {
            layers.get(layers.size() - 1).close();
            return true;
        }
        return handled || scope != null;
    }

    public boolean keyReleased(int keyCode, boolean shiftDown) {
        return focusedElement != null && dispatchFromElement(focusedElement, GuiInputEvent.keyUp(keyCode, shiftDown));
    }

    public GuiElement getFocusedElement() {
        return focusedElement;
    }

    /**
     * Hover uses the same displayed transforms, clips and layer ownership as
     * pointer input.
     */
    public boolean isHovered(GuiElement element, int mouseX, int mouseY) {
        GuiFrameSnapshot frame = renderingFrame == null ? displayedFrame : renderingFrame;
        GuiFrameSnapshot.Node node = frame == null ? null : frame.node(element);
        GuiElement scope = modalScope();
        if (node == null || !node.eligible() || !node.bounds.contains(mouseX, mouseY)
                || !node.hitClip.contains(mouseX, mouseY) || !inputAllowed(element)
                || !GuiFrameSnapshot.inside(element, scope)) {
            return false;
        }
        // Pointer-transparent decorations can still expose hover styling/observations.
        if (!node.pointerTarget()) {
            return true;
        }
        if (hoverFrame != frame || hoverScope != scope || hoverX != mouseX || hoverY != mouseY) {
            hoverFrame = frame;
            hoverScope = scope;
            hoverX = mouseX;
            hoverY = mouseY;
            hoverPath = frame.hitPath(mouseX, mouseY, scope, this::inputAllowed);
        }
        return hoverPath.contains(element);
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
        if (element != null && inputAllowed(element) && element.belongsTo(this) && element.isAvailable()
                && element.isFocusable() && GuiFrameSnapshot.inside(element, modalScope())) {
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

    void cancelGesturesIn(GuiElement element) {
        dispatchDepth++;
        try {
            if (isDescendantOf(pointerCapture, element)) {
                cancelPointerCapture();
            }
            for (Map.Entry<Integer, GuiElement> entry : new ArrayList<>(gestureTargets.entrySet())) {
                if (isDescendantOf(entry.getValue(), element)) {
                    gestureTargets.remove(entry.getKey());
                    cancelledGestures.add(entry.getKey());
                    Runnable cancel = () -> entry.getValue().cancelPointer(context, entry.getKey());
                    if (painting || layingOut || preparingVisuals) {
                        deferredEffects.add(cancel);
                    } else {
                        cancel.run();
                    }
                }
            }
        } finally {
            finishDispatch();
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
        hoverFrame = null;
        hoverScope = null;
        hoverPath = Collections.emptyList();
    }

    boolean deferMutation(Runnable mutation) {
        if (painting || layingOut || preparingVisuals) {
            throw new IllegalStateException(
                    "GUI tree mutations are unavailable during layout, visual preparation or paint");
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
        layingOut = true;
        try {
            root.measure(context, GuiGeometry.Constraints.tight(width, height));
            root.arrange(context, screenBounds);
        } finally {
            layingOut = false;
        }
        laidOutWidth = width;
        laidOutHeight = height;
        layoutRequested = false;
    }

    private List<GuiElement> hitPath(int mouseX, int mouseY) {
        return displayedFrame == null
                ? Collections.emptyList()
                : displayedFrame.hitPath(mouseX, mouseY, modalScope(), this::inputAllowed);
    }

    private boolean dispatchPath(List<GuiElement> path, GuiInputEvent event) {
        int previousButton = dispatchButton;
        dispatchButton = event.isPointerEvent() ? event.getButton() : -1;
        dispatchDepth++;
        GuiFrameSnapshot frame = displayedFrame;
        try {
            for (int i = path.size() - 1; i > 0; i--) {
                if (inputAllowed(path.get(i)) && path.get(i).belongsTo(this)
                        && (!event.isPointerEvent() || frame != null && frame.permits(path.get(i))) && path.get(i)
                                .dispatchCapture(context, frame == null ? event : frame.event(path.get(i), event))) {
                    handledTarget = path.get(i);
                    return true;
                }
            }
            for (int i = 0; i < path.size(); i++) {
                if (inputAllowed(path.get(i)) && path.get(i).belongsTo(this)
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
                changeFocus(element, false);
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
            GuiElement scope = modalScope();
            focusable.removeIf(element -> !GuiFrameSnapshot.inside(element, scope));
        } else {
            focusable = displayedFrame.focusable(modalScope());
        }
        focusable.removeIf(element -> !inputAllowed(element));
        focusable.sort(Comparator.comparingInt(GuiElement::getFocusOrder));
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
        changeFocus(element, true);
    }

    private void changeFocus(GuiElement element, boolean reveal) {
        if (focusedElement == element) {
            return;
        }
        GuiElement previous = focusedElement;
        focusedElement = element;
        if (previous != null) {
            notifyFocus(previous, false);
        }
        if (element != null && focusedElement == element) {
            Runnable gained = () -> {
                if (focusedElement != element || !element.belongsTo(this)) {
                    return;
                }
                if (reveal) {
                    revealFocus(element);
                }
                notifyFocus(element, true);
            };
            if (painting || layingOut || preparingVisuals) {
                deferredEffects.add(gained);
            } else {
                gained.run();
            }
        }
    }

    void revealFocusedRegion() {
        if (focusedElement != null) {
            revealFocus(focusedElement);
        }
    }

    private void revealFocus(GuiElement element) {
        ensureLayout();
        element.revealFocus(context);
        boolean hasViewport = false;
        for (GuiContainer parent = element.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof GuiFocusViewport) {
                hasViewport = true;
                break;
            }
        }
        if (!hasViewport) {
            ensureLayout();
            return;
        }
        ensureLayout();
        Rect bounds = element.focusBounds();
        GraphicsClip target = new GraphicsClip(bounds.getLeft(), bounds.getTop(), bounds.getRight(), bounds.getBottom())
                .transform(element.arrangedTransform());
        for (GuiContainer parent = element.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof GuiFocusViewport) {
                target = ((GuiFocusViewport) parent).revealFocus(target);
            }
            target = target.transform(parent.arrangedTransform());
        }
        ensureLayout();
    }

    private void notifyFocus(GuiElement element, boolean focused) {
        if (painting || layingOut || preparingVisuals) {
            deferredEffects.add(() -> element.onFocusChanged(context, focused));
        } else {
            element.onFocusChanged(context, focused);
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

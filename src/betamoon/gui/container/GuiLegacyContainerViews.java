package betamoon.gui.container;

import betamoon.gui.control.GuiActivation;
import betamoon.gui.control.GuiTextEditKeys;
import betamoon.gui.control.GuiTextEditorModel;
import betamoon.gui.framework.GuiContainer;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.framework.GuiElement;
import betamoon.gui.framework.GuiFrameContent;
import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.framework.GuiInputEvent;
import betamoon.gui.framework.GuiScene;
import betamoon.gui.presentation.GuiControlType;
import betamoon.gui.presentation.GuiGeometry;
import betamoon.tileentity.ContainerControlDefinition;
import betamoon.tileentity.ContainerControlRuntime;
import betamoon.tileentity.ContainerGuiDefinition;
import betamoon.tileentity.ContainerPresentationRuntime;
import java.util.IdentityHashMap;
import java.util.Map;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.lwjgl.input.Keyboard;

/**
 * Retained compatibility views for legacy container declarations. The domain
 * runtime keeps validation and the native adapter keeps its established
 * painter.
 */
public final class GuiLegacyContainerViews implements AutoCloseable {
    private final GuiScene scene;
    private final ContainerControlRuntime controls;
    private final ContainerGuiDefinition definition;
    private final Runnable clickSound;
    private final Map<ContainerGuiDefinition.ControlElement, ControlView> views = new IdentityHashMap<>();
    private final ContainerPresentationRuntime presentation;
    private int originX;
    private int originY;
    private final LegacyRoot root = new LegacyRoot();

    private final class LegacyRoot extends GuiContainer {
        void refreshProjection() {
            requestLayout();
        }

        @Override
        protected void arrangeChildren(GuiContext context) {
            for (GuiElement child : getChildren()) {
                ControlView view = (ControlView) child;
                GuiGeometry geometry = presentation.geometry(view.element);
                GuiGeometry.Bounds bounds = geometry.bounds().intersect(geometry.clip);
                child.arrange(context,
                        new Rect(originX + (int) Math.floor(bounds.x), originY + (int) Math.floor(bounds.y),
                                originX + (int) Math.ceil(bounds.x + bounds.width),
                                originY + (int) Math.ceil(bounds.y + bounds.height)));
                child.setVisible(geometry.available());
            }
        }
    }

    public GuiLegacyContainerViews(GuiScene scene, ContainerGuiDefinition definition,
            ContainerPresentationRuntime presentation, ContainerControlRuntime controls, Runnable clickSound) {
        this.scene = scene;
        this.definition = definition;
        this.presentation = presentation;
        this.controls = controls;
        this.clickSound = clickSound;
        // Geometry already includes the authored hierarchy. Keep the legacy global
        // layer/declaration order, including controls in separate root groups.
        for (int layer = 0; layer <= 2; layer++) {
            for (ContainerGuiDefinition.Element element : definition.elements) {
                if (element.layer == layer && element instanceof ContainerGuiDefinition.ControlElement) {
                    ContainerGuiDefinition.ControlElement control = (ContainerGuiDefinition.ControlElement) element;
                    ControlView view = new ControlView(control);
                    views.put(control, view);
                    root.add(view);
                }
            }
        }
    }

    public GuiContainer root() {
        return root;
    }

    public GuiElement element(ContainerGuiDefinition.ControlElement element) {
        return view(element);
    }

    /** Logical preparation; callback-producing ownership changes run here. */
    public void update(int x, int y) {
        originX = x;
        originY = y;
        for (ControlView view : views.values()) {
            boolean enabled = controls.enabled(view.control) && presentation.geometry(view.element).available();
            view.focusable(enabled && view.element.focusable);
            if (!enabled) {
                scene.cancelInput(view);
            }
        }
        root.refreshProjection();
    }

    /** Animation sampling requests only Java layout; painting cannot enter Lua. */
    public void sample() {
        root.refreshProjection();
    }

    public boolean focused(ContainerGuiDefinition.ControlElement element) {
        return view(element).isFocused();
    }

    public boolean pressed(ContainerGuiDefinition.ControlElement element) {
        return view(element).pressedButton >= 0;
    }

    public boolean invalid(ContainerGuiDefinition.ControlElement element) {
        return view(element).invalidUntil > System.currentTimeMillis();
    }

    public GuiTextEditorModel editor(ContainerGuiDefinition.ControlElement element) {
        return view(element).editor;
    }

    public String text(ContainerGuiDefinition.ControlElement element) {
        ControlView view = view(element);
        return view.editor == null ? String.valueOf(controls.value(view.control)) : view.editor.text();
    }

    public int textOffset(ContainerGuiDefinition.ControlElement element, int availableWidth) {
        ControlView view = view(element);
        if (view.editor == null || !view.isFocused()) {
            view.textOffset = 0;
        } else {
            String text = view.editor.text();
            int caret = view.editor.caret();
            view.textOffset = Math.min(view.textOffset, caret);
            while (view.textOffset < caret && scene.getContext().getFont()
                    .getStringWidth(text.substring(view.textOffset, caret)) > availableWidth) {
                view.textOffset++;
            }
            while (view.textOffset > 0 && scene.getContext().getFont()
                    .getStringWidth(text.substring(view.textOffset - 1, caret)) <= availableWidth) {
                view.textOffset--;
            }
        }
        return view.textOffset;
    }

    private ControlView view(ContainerGuiDefinition.ControlElement element) {
        ControlView view = views.get(element);
        if (view == null) {
            throw new IllegalArgumentException("Control view belongs to another container revision");
        }
        return view;
    }

    @Override
    public void close() {
        scene.cancelInput(root);
        if (root.getParent() != null) {
            root.getParent().remove(root);
        }
        views.clear();
    }

    private final class ControlView extends GuiElement {
        final ContainerGuiDefinition.ControlElement element;
        final ContainerControlDefinition control;
        final GuiActivation activation = new GuiActivation(GuiActivation.Timing.RELEASE);
        GuiTextEditorModel editor;
        String editOrigin;
        int textOffset;
        int pressedButton = -1;
        int choiceDirection;
        int lastX;
        int lastY;
        long invalidUntil;

        ControlView(ContainerGuiDefinition.ControlElement element) {
            this.element = element;
            control = definition.container.controls.get(element.control);
            setFocusOrder(definition.elements.indexOf(element));
        }

        void focusable(boolean value) {
            setFocusable(value);
        }

        @Override
        protected GuiFrameContent sampleFrameContent() {
            return new Frame(presentation.geometry(element), getBounds(), originX, originY);
        }

        @Override
        protected void onFocusChanged(GuiContext context, boolean focused) {
            if (control.type == GuiControlType.TEXT) {
                Keyboard.enableRepeatEvents(focused);
                if (focused) {
                    editOrigin = String.valueOf(controls.value(control));
                    editor = new GuiTextEditorModel(editOrigin, control.maximumLength);
                    textOffset = 0;
                } else if (editor != null) {
                    String current = String.valueOf(controls.value(control));
                    boolean conflict = !editOrigin.equals(current);
                    if (conflict || !editor.text().equals(editOrigin)) {
                        LuaTable input = ContainerControlRuntime.input("focus");
                        input.set("conflicted", LuaValue.valueOf(conflict));
                        result(controls.editText(control, conflict ? current : editor.text(), input, true));
                    }
                    editor = null;
                    editOrigin = null;
                    textOffset = 0;
                }
            } else if (control.type == GuiControlType.CUSTOM) {
                LuaTable input = ContainerControlRuntime.input("focus");
                input.set("phase", focused ? "focus" : "blur");
                controls.custom(control, input);
            }
        }

        @Override
        protected boolean onInput(GuiContext context, GuiInputEvent event) {
            if (event.getType() == GuiInputEvent.Type.POINTER_CANCEL) {
                pressedButton = -1;
                choiceDirection = 0;
                activation.input(event, false, isFocused());
                return true;
            }
            if (!controls.enabled(control)) {
                return event.getType() == GuiInputEvent.Type.POINTER_DOWN;
            }
            if (event.getType() == GuiInputEvent.Type.KEY_TYPED) {
                return key(context, event);
            }
            Frame frame = (Frame) context.getFrameContent();
            if (frame == null) {
                return false;
            }
            boolean inside = frame.geometry.contains(event.getMouseX() - frame.originX,
                    event.getMouseY() - frame.originY);
            if (event.getType() == GuiInputEvent.Type.SCROLL) {
                if (control.type != GuiControlType.CUSTOM) {
                    return false;
                }
                LuaTable input = pointer(frame, event, "scroll");
                input.set("wheel", event.getWheelDelta());
                controls.custom(control, input);
                return true;
            }
            if (event.getType() == GuiInputEvent.Type.POINTER_DOWN) {
                pressedButton = event.getButton();
                lastX = event.getMouseX();
                lastY = event.getMouseY();
                choiceDirection = direction(frame, event.getMouseX());
                activation.input(event, inside, isFocused());
                if (element.captureDrag && event.getButton() == 0) {
                    capturePointer(context);
                }
                LuaTable input = pointer(frame, event, "press");
                if (control.type == GuiControlType.TEXT && event.getButton() == 0 && editor != null) {
                    int caret = textOffset;
                    double x = frame.localX(event.getMouseX()) - 4;
                    while (caret < editor.text().length()
                            && context.getFont().getStringWidth(editor.text().substring(textOffset, caret + 1)) <= x) {
                        caret++;
                    }
                    editor.move(caret, false);
                } else if (control.type == GuiControlType.NUMBER && event.getButton() == 0) {
                    slider(frame, event, input);
                } else if (control.type == GuiControlType.CUSTOM) {
                    controls.custom(control, input);
                }
                return true;
            }
            if (event.getType() == GuiInputEvent.Type.POINTER_DRAG) {
                if (pressedButton != 0 || !element.captureDrag || !event.isMouseDown()
                        || event.getMouseX() == lastX && event.getMouseY() == lastY) {
                    return pressedButton >= 0;
                }
                LuaTable input = pointer(frame, event, "drag");
                lastX = event.getMouseX();
                lastY = event.getMouseY();
                if (control.type == GuiControlType.NUMBER) {
                    slider(frame, event, input);
                } else if (control.type == GuiControlType.CUSTOM) {
                    controls.custom(control, input);
                }
                return true;
            }
            if (event.getType() != GuiInputEvent.Type.POINTER_UP || pressedButton != event.getButton()) {
                return false;
            }
            LuaTable input = pointer(frame, event, "release");
            int activationResult = activation.input(event, inside, isFocused());
            boolean changed = false;
            if (control.type == GuiControlType.NUMBER && event.getButton() == 0) {
                slider(frame, event, input);
            } else if (control.type == GuiControlType.CHOICE && inside) {
                int direction = event.getButton() == 1 ? -1 : choiceDirection;
                if (direction != 0 && (event.getButton() == 1 || direction == direction(frame, event.getMouseX()))) {
                    changed = result(controls.cycle(control, direction, input));
                }
            } else if ((control.type == GuiControlType.ACTION || control.type == GuiControlType.TOGGLE)
                    && (activationResult & GuiActivation.ACTIVATE) != 0) {
                changed = result(controls.activate(control, input));
            } else if (control.type == GuiControlType.CUSTOM) {
                controls.custom(control, input);
            }
            pressedButton = -1;
            choiceDirection = 0;
            if (changed) {
                clickSound.run();
            }
            return true;
        }

        private boolean key(GuiContext context, GuiInputEvent event) {
            int key = event.getKeyCode();
            LuaTable input = ContainerControlRuntime.input("keyboard");
            input.set("key", key);
            input.set("character", String.valueOf(event.getTypedChar()));
            input.set("shift", LuaValue.valueOf(event.isShiftDown()));
            input.set("control", LuaValue.valueOf(event.isControlDown()));
            if (control.type == GuiControlType.TEXT && editor != null) {
                if (key == Keyboard.KEY_ESCAPE) {
                    editor = null;
                    editOrigin = null;
                    scene.cancelInput(this);
                    Keyboard.enableRepeatEvents(false);
                    return true;
                }
                if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                    if (result(controls.editText(control, editor.text(), input, true))) {
                        editOrigin = String.valueOf(controls.value(control));
                        editor.observe(editOrigin);
                        editor.cancel();
                    }
                    return true;
                }
                GuiTextEditorModel.Snapshot previous = editor.snapshot();
                GuiTextEditKeys.Result edit = GuiTextEditKeys.apply(editor, event.getTypedChar(), key,
                        event.isShiftDown(), event.isControlDown(), code -> true);
                if (edit == GuiTextEditKeys.Result.EDIT
                        && !result(controls.editText(control, editor.text(), input, false))) {
                    editor.restoreSnapshot(previous);
                }
                return edit != GuiTextEditKeys.Result.UNHANDLED;
            }
            if (control.type == GuiControlType.NUMBER) {
                double delta;
                if (key == Keyboard.KEY_LEFT || key == Keyboard.KEY_DOWN) {
                    delta = -control.step;
                } else if (key == Keyboard.KEY_RIGHT || key == Keyboard.KEY_UP) {
                    delta = control.step;
                } else if (key == Keyboard.KEY_PRIOR || key == Keyboard.KEY_NEXT) {
                    delta = key == Keyboard.KEY_PRIOR ? control.pageStep : -control.pageStep;
                } else {
                    delta = 0;
                }
                if (delta != 0) {
                    result(controls.changeNumber(control, ((Number) controls.value(control)).doubleValue() + delta,
                            input));
                    return true;
                }
            }
            if (control.type == GuiControlType.CHOICE && (key == Keyboard.KEY_LEFT || key == Keyboard.KEY_UP
                    || key == Keyboard.KEY_RIGHT || key == Keyboard.KEY_DOWN)) {
                if (result(
                        controls.cycle(control, key == Keyboard.KEY_LEFT || key == Keyboard.KEY_UP ? -1 : 1, input))) {
                    clickSound.run();
                }
                return true;
            }
            if ((key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER || key == Keyboard.KEY_SPACE)
                    && result(controls.activate(control, input))) {
                clickSound.run();
                return true;
            }
            return control.type == GuiControlType.CUSTOM && controls.custom(control, input);
        }

        private void slider(Frame frame, GuiInputEvent event, LuaTable input) {
            double ratio = "vertical".equals(element.orientation)
                    ? 1 - frame.localY(event.getMouseY()) / Math.max(1, frame.geometry.height - 1)
                    : frame.localX(event.getMouseX()) / Math.max(1, frame.geometry.width - 1);
            result(controls.changeNumber(control,
                    control.minimum + Math.max(0, Math.min(1, ratio)) * (control.maximum - control.minimum), input));
        }

        private int direction(Frame frame, int x) {
            int local = (int) frame.localX(x);
            int arrow = Math.min(16, Math.max(1, (int) frame.geometry.width / 3));
            return local >= 0 && local < arrow
                    ? -1
                    : local >= frame.geometry.width - arrow && local < frame.geometry.width ? 1 : 0;
        }

        private LuaTable pointer(Frame frame, GuiInputEvent event, String phase) {
            LuaTable input = ContainerControlRuntime.input("mouse");
            input.set("phase", phase);
            input.set("button", event.getButton());
            input.set("x",
                    LuaValue.valueOf(Math.max(0, Math.min(frame.geometry.width - 1, frame.localX(event.getMouseX())))));
            input.set("y", LuaValue
                    .valueOf(Math.max(0, Math.min(frame.geometry.height - 1, frame.localY(event.getMouseY())))));
            input.set("deltaX",
                    LuaValue.valueOf("press".equals(phase) ? 0 : (event.getMouseX() - lastX) / frame.geometry.scaleX));
            input.set("deltaY",
                    LuaValue.valueOf("press".equals(phase) ? 0 : (event.getMouseY() - lastY) / frame.geometry.scaleY));
            input.set("shift", LuaValue.valueOf(shiftDown()));
            input.set("control", LuaValue.valueOf(controlDown()));
            return input;
        }

        private boolean result(boolean accepted) {
            invalidUntil = accepted ? 0 : System.currentTimeMillis() + 750;
            return accepted;
        }
    }

    private static final class Frame implements GuiFrameContent {
        final GuiGeometry geometry;
        final Rect bounds;
        final int originX;
        final int originY;

        Frame(GuiGeometry geometry, Rect bounds, int originX, int originY) {
            this.geometry = geometry;
            this.bounds = bounds;
            this.originX = originX;
            this.originY = originY;
        }

        public boolean contains(double x, double y) {
            return geometry.contains(bounds.getLeft() + x - originX, bounds.getTop() + y - originY);
        }

        double localX(int x) {
            return geometry.localX(x - originX);
        }

        double localY(int y) {
            return geometry.localY(y - originY);
        }
    }

    public static boolean shiftDown() {
        return Keyboard.isCreated()
                && (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));
    }

    public static boolean controlDown() {
        return Keyboard.isCreated()
                && (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL));
    }
}

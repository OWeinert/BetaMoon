package betamoon.tileentity;

import betamoon.gui.framework.GuiRenderer;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.src.GuiContainer;
import net.minecraft.src.InventoryPlayer;
import net.minecraft.src.ItemStack;
import net.minecraft.src.RenderItem;
import net.minecraft.src.Tessellator;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.luaj.vm2.LuaTable;

/**
 * Renders prevalidated Lua container definitions without exposing rendering
 * APIs to Lua.
 */
public final class GuiLuaContainer extends GuiContainer {
    private static final RenderItem ITEM_RENDERER = new RenderItem();
    private final LuaTileEntity entity;
    private final ContainerGuiDefinition definition;
    private final LuaContainer luaContainer;
    private ContainerGuiDefinition.ControlElement focused;
    private ContainerGuiDefinition.ControlElement pressed;
    private ContainerGuiDefinition.ControlElement captured;
    private final Map<String, String> drafts = new HashMap<String, String>();
    private final Map<String, String> draftOrigins = new HashMap<String, String>();
    private final Map<String, Long> invalidUntil = new HashMap<String, Long>();
    private int caret;
    private int selectionAnchor = -1;
    private int textOffset;
    private int lastPointerX;
    private int lastPointerY;
    private int currentMouseX;
    private int currentMouseY;
    private int pressedChoiceDirection;

    public GuiLuaContainer(InventoryPlayer player, LuaTileEntity entity, ContainerGuiDefinition definition) {
        this(player, entity, definition, new LuaContainer(player, entity, definition.container));
    }

    private GuiLuaContainer(InventoryPlayer player, LuaTileEntity entity, ContainerGuiDefinition definition,
            LuaContainer container) {
        super(container);
        this.entity = entity;
        this.definition = definition;
        this.luaContainer = container;
        this.xSize = definition.width;
        this.ySize = definition.height;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return definition.pauseGame;
    }

    @Override
    public void onGuiClosed() {
        blurFocused();
        luaContainer.controls().close();
        super.onGuiClosed();
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            ContainerGuiDefinition.ControlElement element = controlAt(mouseX, mouseY);
            if (element != null && enabled(element)
                    && control(element).type == ContainerControlDefinition.Type.CUSTOM) {
                LuaTable input = pointerInput("mouse", element, mouseX, mouseY, -1, "scroll");
                input.set("wheel", wheel);
                luaContainer.controls().custom(control(element), input);
            }
        }
        if (captured != null && Mouse.isButtonDown(0)) {
            drag(captured, mouseX, mouseY);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        ContainerGuiDefinition.ControlElement element = controlAt(mouseX, mouseY);
        if (element == null) {
            blurFocused();
            super.mouseClicked(mouseX, mouseY, button);
            return;
        }
        if (focused != element) {
            blurFocused();
            if (element.focusable && enabled(element)) {
                focus(element);
            }
        }
        if (!enabled(element)) {
            return;
        }
        if (control(element).type == ContainerControlDefinition.Type.TEXT && button == 0) {
            placeTextCaret(element, mouseX);
        }
        pressed = element;
        pressedChoiceDirection = control(element).type == ContainerControlDefinition.Type.CHOICE
                ? choiceDirection(element, mouseX)
                : 0;
        captured = element.captureDrag && button == 0 ? element : null;
        lastPointerX = mouseX;
        lastPointerY = mouseY;
        LuaTable input = pointerInput("mouse", element, mouseX, mouseY, button, "press");
        ContainerControlDefinition control = control(element);
        if (control.type == ContainerControlDefinition.Type.NUMBER && button == 0) {
            changeSlider(element, mouseX, mouseY, input);
        } else if (control.type == ContainerControlDefinition.Type.CUSTOM) {
            luaContainer.controls().custom(control, input);
        }
    }

    @Override
    protected void mouseMovedOrUp(int mouseX, int mouseY, int button) {
        if (button < 0) {
            return;
        }
        ContainerGuiDefinition.ControlElement target = captured != null ? captured : pressed;
        if (target == null) {
            super.mouseMovedOrUp(mouseX, mouseY, button);
            return;
        }
        LuaTable input = pointerInput("mouse", target, mouseX, mouseY, button, "release");
        ContainerControlDefinition control = control(target);
        if (control.type == ContainerControlDefinition.Type.CUSTOM) {
            input.set("deltaX", mouseX - lastPointerX);
            input.set("deltaY", mouseY - lastPointerY);
        }
        boolean inside = contains(target, mouseX, mouseY);
        boolean changed = false;
        if (control.type == ContainerControlDefinition.Type.NUMBER && button == 0) {
            changeSlider(target, mouseX, mouseY, input);
        } else if (control.type == ContainerControlDefinition.Type.CHOICE && inside) {
            int direction = button == 1 ? -1 : pressedChoiceDirection;
            if (direction != 0 && (button == 1 || direction == choiceDirection(target, mouseX))) {
                changed = recordResult(control, luaContainer.controls().cycle(control, direction, input));
            }
        } else if ((control.type == ContainerControlDefinition.Type.ACTION
                || control.type == ContainerControlDefinition.Type.TOGGLE) && button == 0 && inside) {
            changed = recordResult(control, luaContainer.controls().activate(control, input));
        } else if (control.type == ContainerControlDefinition.Type.CUSTOM) {
            luaContainer.controls().custom(control, input);
        }
        if (changed) {
            playClickSound();
        }
        pressed = null;
        captured = null;
        pressedChoiceDirection = 0;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_TAB && moveFocus(isShiftDown() ? -1 : 1)) {
            return;
        }
        if (focused == null || !enabled(focused)) {
            super.keyTyped(typedChar, keyCode);
            return;
        }
        ContainerControlDefinition control = control(focused);
        LuaTable input = keyInput(focused, typedChar, keyCode);
        if (control.type == ContainerControlDefinition.Type.TEXT && editText(control, typedChar, keyCode, input)) {
            return;
        }
        if (control.type == ContainerControlDefinition.Type.NUMBER && adjustNumber(control, keyCode, input)) {
            return;
        }
        if (control.type == ContainerControlDefinition.Type.CHOICE
                && (keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_UP
                        || keyCode == Keyboard.KEY_RIGHT || keyCode == Keyboard.KEY_DOWN)) {
            if (recordResult(control, luaContainer.controls().cycle(control,
                    keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_UP ? -1 : 1, input))) {
                playClickSound();
            }
            return;
        }
        if ((keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER
                || keyCode == Keyboard.KEY_SPACE)
                && recordResult(control, luaContainer.controls().activate(control, input))) {
            playClickSound();
            return;
        }
        if (control.type == ContainerControlDefinition.Type.CUSTOM
                && luaContainer.controls().custom(control, input)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        currentMouseX = mouseX;
        currentMouseY = mouseY;
        releaseUnavailableOwnership();
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawElementTooltip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerForegroundLayer() {
        drawLabel(definition.title);
        drawLabel(definition.playerInventoryLabel);
        drawElements(2);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks) {
        int originX = (width - xSize) / 2;
        int originY = (height - ySize) / 2;
        drawBackground(originX, originY);
        GL11.glPushMatrix();
        GL11.glTranslatef(originX, originY, 0.0F);
        drawElements(0);
        drawElements(1);
        GL11.glPopMatrix();
    }

    private void drawBackground(int x, int y) {
        ContainerGuiDefinition.Background background = definition.background;
        if ("minecraft".equals(background.style) && background.texture == null) {
            drawMinecraftPanel(x, y);
        } else if ("chest".equals(background.style)) {
            drawChestBackground(x, y, background.chestRows);
        } else if (background.texture != null) {
            drawTexture(background.texture, x, y, definition.width, definition.height);
        }
        if (background.drawSlotFrames) {
            drawSlotFrames(x, y);
        }
    }

    /** Draws the two variable-height pieces used by vanilla chest screens. */
    private void drawChestBackground(int x, int y, int rows) {
        ContainerGuiDefinition.Texture texture = definition.background.texture;
        if (texture == null) {
            texture = new ContainerGuiDefinition.Texture("/gui/container.png", 0, 0, 176, 166, 256, 256);
        }
        int upperHeight = 17 + rows * 18;
        drawTextureRegion(texture.resource, x, y, 0, 0, 176, upperHeight, 176, upperHeight, 256, 256);
        drawTextureRegion(texture.resource, x, y + upperHeight, 0, 126, 176, 96, 176, 96, 256, 256);
    }

    private void drawMinecraftPanel(int x, int y) {
        drawRect(x, y, x + xSize, y + ySize, 0xFFC6C6C6);
        drawRect(x, y, x + xSize, y + 2, 0xFFFFFFFF);
        drawRect(x, y, x + 2, y + ySize, 0xFFFFFFFF);
        drawRect(x, y + ySize - 2, x + xSize, y + ySize, 0xFF555555);
        drawRect(x + xSize - 2, y, x + xSize, y + ySize, 0xFF555555);
    }

    private void drawSlotFrames(int originX, int originY) {
        Iterator<ContainerDefinition.SlotDefinition> slots = definition.container.slots.iterator();
        while (slots.hasNext()) {
            ContainerDefinition.SlotDefinition slot = slots.next();
            drawSlotFrame(originX + slot.x - 1, originY + slot.y - 1);
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawSlotFrame(originX + definition.container.playerX + column * 18 - 1,
                        originY + definition.container.playerY + row * 18 - 1);
            }
        }
        if (definition.container.includeHotbar) {
            for (int column = 0; column < 9; column++) {
                drawSlotFrame(originX + definition.container.playerX + column * 18 - 1,
                        originY + definition.container.playerY + 58 - 1);
            }
        }
    }

    private void drawSlotFrame(int x, int y) {
        drawRect(x, y, x + 18, y + 18, 0xFF373737);
        drawRect(x + 1, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        drawRect(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
    }

    private void drawLabel(ContainerGuiDefinition.Label label) {
        if (label == null || label.text == null) {
            return;
        }
        drawAlignedText(label.text, label.x, label.y, label.width, label.align, label.color, label.shadow);
    }

    private void drawElements(int layer) {
        for (int i = 0; i < definition.elements.size(); i++) {
            ContainerGuiDefinition.Element element = definition.elements.get(i);
            if (element.layer == layer && visible(element.visibleWhen)) {
                drawElement(element);
            }
        }
    }

    private void drawElement(ContainerGuiDefinition.Element element) {
        int elementWidth = elementWidth(element);
        int elementHeight = elementHeight(element);
        int x = anchoredX(element.anchor, element.x, elementWidth);
        int y = anchoredY(element.anchor, element.y, elementHeight);
        if (element instanceof ContainerGuiDefinition.ImageElement) {
            ContainerGuiDefinition.ImageElement image = (ContainerGuiDefinition.ImageElement) element;
            drawTexture(image.image, x, y, image.width, image.height);
        } else if (element instanceof ContainerGuiDefinition.TextElement) {
            ContainerGuiDefinition.TextElement text = (ContainerGuiDefinition.TextElement) element;
            drawAlignedText(resolveText(text), x, y, text.width, text.align, text.color, text.shadow);
        } else if (element instanceof ContainerGuiDefinition.ProgressElement) {
            drawProgress((ContainerGuiDefinition.ProgressElement) element, x, y);
        } else if (element instanceof ContainerGuiDefinition.StateImageElement) {
            ContainerGuiDefinition.StateImageElement state = (ContainerGuiDefinition.StateImageElement) element;
            Object current = entity.getDataValue(state.field);
            ContainerGuiDefinition.Texture image = state.states.get(String.valueOf(current));
            if (image == null) {
                image = state.defaultImage;
            }
            if (image != null) {
                drawTexture(image, x, y, image.width, image.height);
            }
        } else if (element instanceof ContainerGuiDefinition.RectangleElement) {
            ContainerGuiDefinition.RectangleElement rectangle = (ContainerGuiDefinition.RectangleElement) element;
            int color = rectangle.color >>> 24 == 0 ? rectangle.color | 0xFF000000 : rectangle.color;
            drawRect(x, y, x + rectangle.width, y + rectangle.height, color);
        } else if (element instanceof ContainerGuiDefinition.ItemElement) {
            ContainerGuiDefinition.ItemElement item = (ContainerGuiDefinition.ItemElement) element;
            ItemStack stack = item.slot == null ? item.getItem() : entity.getStackInNamedSlot(item.slot);
            if (stack != null) {
                GL11.glEnable(GL11.GL_LIGHTING);
                ITEM_RENDERER.renderItemIntoGUI(fontRenderer, mc.renderEngine, stack, x, y);
                if (item.showCount) {
                    ITEM_RENDERER.renderItemOverlayIntoGUI(fontRenderer, mc.renderEngine, stack, x, y);
                }
                GL11.glDisable(GL11.GL_LIGHTING);
            }
        } else if (element instanceof ContainerGuiDefinition.ControlElement) {
            drawControl((ContainerGuiDefinition.ControlElement) element, x, y);
        }
    }

    private void drawControl(ContainerGuiDefinition.ControlElement element, int x, int y) {
        ContainerControlDefinition control = control(element);
        boolean enabled = enabled(element);
        boolean hovered = containsLocal(element, currentMouseX, currentMouseY);
        boolean invalid = invalid(element);
        String state = interactionState(element, enabled, hovered, invalid);
        boolean selected = control.type == ContainerControlDefinition.Type.TOGGLE
                && Boolean.TRUE.equals(luaContainer.controls().value(control));
        ContainerGuiDefinition.Texture image = customControlTexture(element, state, selected);
        if (image != null) {
            if (element.presentation == ContainerGuiDefinition.ControlElement.Presentation.CHECKBOX) {
                drawTexture(image, x, y + (element.height - 12) / 2, 12, 12);
            } else {
                drawTexture(image, x, y, element.width, element.height);
            }
        } else {
            drawStyledControl(element, control, x, y, state, selected);
        }
        if (control.type == ContainerControlDefinition.Type.TEXT) {
            drawTextControl(element, control, x, y, enabled);
            return;
        }
        if (element.presentation == ContainerGuiDefinition.ControlElement.Presentation.ICON_BUTTON
                && element.icon != null) {
            int iconX = x + (element.width - element.icon.width) / 2;
            int iconY = y + (element.height - element.icon.height) / 2;
            drawTexture(element.icon, iconX, iconY, element.icon.width, element.icon.height);
        }
        String text = controlText(element, control);
        if (text != null && control.type != ContainerControlDefinition.Type.NUMBER) {
            int color = enabled ? 0xFFFFFF : 0xA0A0A0;
            int textX = x + 4;
            int textWidth = Math.max(0, element.width - 8);
            String alignment = "center";
            if (element.presentation == ContainerGuiDefinition.ControlElement.Presentation.CHECKBOX) {
                textX = x + 16;
                textWidth = Math.max(0, element.width - 16);
                alignment = "left";
            } else if (element.presentation == ContainerGuiDefinition.ControlElement.Presentation.CHOICE) {
                textX = x + choiceArrowWidth(element);
                textWidth = Math.max(0, element.width - choiceArrowWidth(element) * 2);
            }
            drawAlignedText(text, textX, y + (element.height - 8) / 2, textWidth, alignment, color, true);
        }
    }

    private String interactionState(ContainerGuiDefinition.ControlElement element, boolean enabled, boolean hovered,
            boolean invalid) {
        return !enabled ? "disabled" : invalid ? "invalid" : pressed == element ? "pressed"
                : focused == element ? "focused" : hovered ? "hovered" : "normal";
    }

    private ContainerGuiDefinition.Texture customControlTexture(ContainerGuiDefinition.ControlElement element,
            String state, boolean selected) {
        ContainerGuiDefinition.Texture image = selected
                ? element.visuals.get("normal".equals(state) ? "selected" : "selected_" + state)
                : null;
        if (image == null) {
            image = element.visuals.get(state);
        }
        return image;
    }

    private void drawTextControl(ContainerGuiDefinition.ControlElement element, ContainerControlDefinition control,
            int x, int y, boolean enabled) {
        String value = draft(control);
        int availableWidth = Math.max(1, element.width - 8);
        if (focused == element) {
            keepCaretVisible(value, availableWidth);
        }
        int visibleEnd = visibleTextEnd(value, textOffset, availableWidth);
        String visible = value.substring(textOffset, visibleEnd);
        if (focused == element && hasSelection()) {
            int start = Math.max(textOffset, selectionStart());
            int end = Math.min(visibleEnd, selectionEnd());
            if (start < end) {
                int selectionX = x + 4 + fontRenderer.getStringWidth(value.substring(textOffset, start));
                int selectionWidth = fontRenderer.getStringWidth(value.substring(start, end));
                drawRect(selectionX, y + 3, selectionX + selectionWidth, y + element.height - 3, 0xFF4A6A9A);
            }
        }
        drawAlignedText(visible, x + 4, y + (element.height - 8) / 2, availableWidth, "left",
                enabled ? 0xFFFFFF : 0xA0A0A0, true);
        if (focused == element && (System.currentTimeMillis() / 500L & 1L) == 0L) {
            int caretX = x + 4 + fontRenderer.getStringWidth(value.substring(textOffset, caret));
            drawRect(caretX, y + 4, caretX + 1, y + element.height - 4, 0xFFFFFFFF);
        }
    }

    private void drawStyledControl(ContainerGuiDefinition.ControlElement element, ContainerControlDefinition control,
            int x, int y, String state, boolean selected) {
        if (element.presentation == ContainerGuiDefinition.ControlElement.Presentation.SLIDER) {
            drawSlider(element, control, x, y, state);
            return;
        }
        if (element.presentation == ContainerGuiDefinition.ControlElement.Presentation.CHECKBOX) {
            drawTexture(element.styleTextures.get((selected ? "on." : "off.") + state),
                    x, y + (element.height - 12) / 2, 12, 12);
            return;
        }
        if (element.presentation == ContainerGuiDefinition.ControlElement.Presentation.TOGGLE_BUTTON) {
            drawTexture(element.styleTextures.get((selected ? "on." : "off.") + state),
                    x, y, element.width, element.height);
            return;
        }
        ContainerGuiDefinition.Texture frame = element.styleTextures.get("frame." + state);
        if (frame != null) {
            drawTexture(frame, x, y, element.width, element.height);
        }
        if (element.presentation == ContainerGuiDefinition.ControlElement.Presentation.CHOICE) {
            drawChoiceArrows(element, x, y, state);
        }
    }

    private void drawSlider(ContainerGuiDefinition.ControlElement element, ContainerControlDefinition control,
            int x, int y, String state) {
            double current = ((Number) luaContainer.controls().value(control)).doubleValue();
            double ratio = (current - control.minimum) / (control.maximum - control.minimum);
            ratio = Math.max(0.0D, Math.min(1.0D, ratio));
            if ("vertical".equals(element.orientation)) {
                ContainerGuiDefinition.Texture track = element.styleTextures.get("track." + state);
                ContainerGuiDefinition.Texture handle = element.styleTextures.get("vertical." + state);
                drawTexture(track, x + (element.width - 6) / 2, y + 4, 6, Math.max(1, element.height - 8));
                int position = (int) Math.round((1.0D - ratio) * Math.max(0, element.height - 8));
                drawTexture(handle, x + (element.width - 12) / 2, y + position, 12, 8);
            } else {
                ContainerGuiDefinition.Texture track = element.styleTextures.get("track." + state);
                ContainerGuiDefinition.Texture handle = element.styleTextures.get("horizontal." + state);
                drawTexture(track, x + 4, y + (element.height - 6) / 2, Math.max(1, element.width - 8), 6);
                int position = (int) Math.round(ratio * Math.max(0, element.width - 8));
                drawTexture(handle, x + position, y + (element.height - 12) / 2, 8, 12);
            }
    }

    private void drawChoiceArrows(ContainerGuiDefinition.ControlElement element, int x, int y, String state) {
        ContainerGuiDefinition.Texture left = element.styleTextures.get("left." + state);
        ContainerGuiDefinition.Texture right = element.styleTextures.get("right." + state);
        drawTexture(left, x + 5, y + (element.height - left.height) / 2, left.width, left.height);
        drawTexture(right, x + element.width - right.width - 5,
                y + (element.height - right.height) / 2, right.width, right.height);
        int arrowWidth = choiceArrowWidth(element);
        drawRect(x + arrowWidth, y + 3, x + arrowWidth + 1, y + element.height - 3, 0xFF555555);
        drawRect(x + element.width - arrowWidth - 1, y + 3,
                x + element.width - arrowWidth, y + element.height - 3, 0xFF555555);
    }

    private void drawProgress(ContainerGuiDefinition.ProgressElement progress, int x, int y) {
        int maximum = progress.maximumField == null ? progress.maximum : entity.getDataInt(progress.maximumField);
        if (maximum <= 0) {
            maximum = 1;
        }
        int value = Math.max(0, Math.min(maximum, entity.getDataInt(progress.field)));
        if (progress.background != null) {
            drawTexture(progress.background, x, y, progress.background.width, progress.background.height);
        }
        if (value == 0 && progress.hideWhenEmpty) {
            return;
        }
        ContainerGuiDefinition.Texture texture = progress.image;
        ProgressDirection.Slice slice = progress.direction.slice(texture.width, texture.height, value, maximum,
                progress.minimumPixels);
        if (!slice.isEmpty()) {
            drawTextureRegion(texture.resource, x + slice.x, y + slice.y, texture.u + slice.x, texture.v + slice.y,
                    slice.width, slice.height, slice.width, slice.height, texture.textureWidth, texture.textureHeight);
        }
    }

    private boolean visible(ContainerGuiDefinition.Condition condition) {
        return GuiConditionEvaluator.evaluate(condition, entity::getDataValue, luaContainer.controls().session()::get);
    }

    private String resolveText(ContainerGuiDefinition.TextElement element) {
        if (element.field == null) {
            return element.text;
        }
        Object value = entity.getDataValue(element.field);
        if (element.format == null) {
            return String.valueOf(value);
        }
        try {
            return String.format(Locale.ENGLISH, element.format, new Object[]{value});
        } catch (RuntimeException ignored) {
            return String.valueOf(value);
        }
    }

    private void drawAlignedText(String text, int x, int y, int areaWidth, String align, int color, boolean shadow) {
        if (text == null) {
            return;
        }
        int drawX = x;
        if (areaWidth > 0 && "center".equals(align)) {
            drawX += (areaWidth - fontRenderer.getStringWidth(text)) / 2;
        } else if (areaWidth > 0 && "right".equals(align)) {
            drawX += areaWidth - fontRenderer.getStringWidth(text);
        }
        if (shadow) {
            fontRenderer.drawStringWithShadow(text, drawX, y, color);
        } else {
            fontRenderer.drawString(text, drawX, y, color);
        }
    }

    private void drawElementTooltip(int mouseX, int mouseY) {
        int originX = (width - xSize) / 2;
        int originY = (height - ySize) / 2;
        for (int i = definition.elements.size() - 1; i >= 0; i--) {
            ContainerGuiDefinition.Element element = definition.elements.get(i);
            if (element.tooltip.isEmpty() || !visible(element.visibleWhen)) {
                continue;
            }
            int elementWidth = elementWidth(element);
            int elementHeight = elementHeight(element);
            int x = originX + anchoredX(element.anchor, element.x, elementWidth);
            int y = originY + anchoredY(element.anchor, element.y, elementHeight);
            if (mouseX >= x && mouseX < x + elementWidth && mouseY >= y && mouseY < y + elementHeight) {
                drawTooltip(resolveTooltip(element.tooltip), mouseX, mouseY);
                return;
            }
        }
    }

    private List<String> resolveTooltip(List<String> source) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            String line = source.get(i);
            Iterator<String> fields = definition.container.tileEntity.fields.keySet().iterator();
            while (fields.hasNext()) {
                String field = fields.next();
                line = line.replace("{" + field + "}", String.valueOf(entity.getDataValue(field)));
            }
            result.add(line);
        }
        return result;
    }

    private void drawTooltip(List<String> lines, int mouseX, int mouseY) {
        GuiRenderer.drawTooltipLines(fontRenderer, width, height, lines, mouseX, mouseY);
    }

    private int elementWidth(ContainerGuiDefinition.Element element) {
        if (element instanceof ContainerGuiDefinition.ImageElement) {
            return ((ContainerGuiDefinition.ImageElement) element).width;
        }
        if (element instanceof ContainerGuiDefinition.ProgressElement) {
            return ((ContainerGuiDefinition.ProgressElement) element).image.width;
        }
        if (element instanceof ContainerGuiDefinition.StateImageElement) {
            ContainerGuiDefinition.StateImageElement state = (ContainerGuiDefinition.StateImageElement) element;
            ContainerGuiDefinition.Texture texture = state.defaultImage != null
                    ? state.defaultImage
                    : state.states.values().iterator().next();
            return texture.width;
        }
        if (element instanceof ContainerGuiDefinition.RectangleElement) {
            return ((ContainerGuiDefinition.RectangleElement) element).width;
        }
        if (element instanceof ContainerGuiDefinition.TooltipElement) {
            return ((ContainerGuiDefinition.TooltipElement) element).width;
        }
        if (element instanceof ContainerGuiDefinition.ItemElement) {
            return 16;
        }
        if (element instanceof ContainerGuiDefinition.ControlElement) {
            return ((ContainerGuiDefinition.ControlElement) element).width;
        }
        ContainerGuiDefinition.TextElement text = (ContainerGuiDefinition.TextElement) element;
        return text.width > 0 ? text.width : fontRenderer.getStringWidth(resolveText(text));
    }

    private int elementHeight(ContainerGuiDefinition.Element element) {
        if (element instanceof ContainerGuiDefinition.ImageElement) {
            return ((ContainerGuiDefinition.ImageElement) element).height;
        }
        if (element instanceof ContainerGuiDefinition.ProgressElement) {
            return ((ContainerGuiDefinition.ProgressElement) element).image.height;
        }
        if (element instanceof ContainerGuiDefinition.StateImageElement) {
            ContainerGuiDefinition.StateImageElement state = (ContainerGuiDefinition.StateImageElement) element;
            ContainerGuiDefinition.Texture texture = state.defaultImage != null
                    ? state.defaultImage
                    : state.states.values().iterator().next();
            return texture.height;
        }
        if (element instanceof ContainerGuiDefinition.RectangleElement) {
            return ((ContainerGuiDefinition.RectangleElement) element).height;
        }
        if (element instanceof ContainerGuiDefinition.TooltipElement) {
            return ((ContainerGuiDefinition.TooltipElement) element).height;
        }
        if (element instanceof ContainerGuiDefinition.ControlElement) {
            return ((ContainerGuiDefinition.ControlElement) element).height;
        }
        return element instanceof ContainerGuiDefinition.ItemElement ? 16 : 8;
    }

    private int anchoredX(GuiAnchor anchor, int x, int elementWidth) {
        return anchor.positionX(x, elementWidth, xSize);
    }

    private int anchoredY(GuiAnchor anchor, int y, int elementHeight) {
        return anchor.positionY(y, elementHeight, ySize);
    }

    private ContainerControlDefinition control(ContainerGuiDefinition.ControlElement element) {
        return definition.container.controls.get(element.control);
    }

    private boolean enabled(ContainerGuiDefinition.ControlElement element) {
        return visible(element.visibleWhen) && luaContainer.controls().enabled(control(element));
    }

    private ContainerGuiDefinition.ControlElement controlAt(int mouseX, int mouseY) {
        for (int layer = 2; layer >= 0; layer--) {
            for (int index = definition.elements.size() - 1; index >= 0; index--) {
                ContainerGuiDefinition.Element value = definition.elements.get(index);
                if (value.layer == layer && value instanceof ContainerGuiDefinition.ControlElement
                        && visible(value.visibleWhen)
                        && contains((ContainerGuiDefinition.ControlElement) value, mouseX, mouseY)) {
                    return (ContainerGuiDefinition.ControlElement) value;
                }
            }
        }
        return null;
    }

    private boolean contains(ContainerGuiDefinition.ControlElement element, int mouseX, int mouseY) {
        int originX = (width - xSize) / 2;
        int originY = (height - ySize) / 2;
        int x = originX + anchoredX(element.anchor, element.x, element.width);
        int y = originY + anchoredY(element.anchor, element.y, element.height);
        return mouseX >= x && mouseX < x + element.width && mouseY >= y && mouseY < y + element.height;
    }

    private boolean containsLocal(ContainerGuiDefinition.ControlElement element, int mouseX, int mouseY) {
        return contains(element, mouseX, mouseY);
    }

    private int choiceDirection(ContainerGuiDefinition.ControlElement element, int mouseX) {
        int originX = (width - xSize) / 2 + anchoredX(element.anchor, element.x, element.width);
        int localX = mouseX - originX;
        int arrowWidth = choiceArrowWidth(element);
        if (localX >= 0 && localX < arrowWidth) {
            return -1;
        }
        if (localX >= element.width - arrowWidth && localX < element.width) {
            return 1;
        }
        return 0;
    }

    private static int choiceArrowWidth(ContainerGuiDefinition.ControlElement element) {
        return Math.min(16, Math.max(1, element.width / 3));
    }

    private void focus(ContainerGuiDefinition.ControlElement element) {
        focused = element;
        ContainerControlDefinition control = control(element);
        if (control.type == ContainerControlDefinition.Type.TEXT) {
            String value = String.valueOf(luaContainer.controls().value(control));
            drafts.put(control.name, value);
            draftOrigins.put(control.name, value);
            caret = value.length();
            selectionAnchor = -1;
            textOffset = 0;
        } else if (control.type == ContainerControlDefinition.Type.CUSTOM) {
            LuaTable input = ContainerControlRuntime.input("focus");
            input.set("phase", "focus");
            luaContainer.controls().custom(control, input);
        }
    }

    private void blurFocused() {
        if (focused == null) {
            return;
        }
        ContainerControlDefinition control = control(focused);
        if (control.type == ContainerControlDefinition.Type.TEXT && drafts.containsKey(control.name)) {
            String current = String.valueOf(luaContainer.controls().value(control));
            String origin = draftOrigins.get(control.name);
            LuaTable input = ContainerControlRuntime.input("focus");
            boolean conflicted = origin != null && !origin.equals(current);
            input.set("conflicted", org.luaj.vm2.LuaValue.valueOf(conflicted));
            String draft = drafts.get(control.name);
            if (conflicted || !draft.equals(origin)) {
                String committed = conflicted ? current : draft;
                recordResult(control, luaContainer.controls().editText(control, committed, input, true));
            }
            drafts.remove(control.name);
            draftOrigins.remove(control.name);
            textOffset = 0;
        } else if (control.type == ContainerControlDefinition.Type.CUSTOM) {
            LuaTable input = ContainerControlRuntime.input("focus");
            input.set("phase", "blur");
            luaContainer.controls().custom(control, input);
        }
        focused = null;
        pressed = null;
        captured = null;
        pressedChoiceDirection = 0;
    }

    private void releaseUnavailableOwnership() {
        if (focused != null && !enabled(focused)) {
            ContainerControlDefinition control = control(focused);
            drafts.remove(control.name);
            draftOrigins.remove(control.name);
            focused = null;
        }
        if (pressed != null && !enabled(pressed)) {
            pressed = null;
            pressedChoiceDirection = 0;
        }
        if (captured != null && !enabled(captured)) {
            captured = null;
        }
    }

    private boolean moveFocus(int direction) {
        List<ContainerGuiDefinition.ControlElement> available = new ArrayList<ContainerGuiDefinition.ControlElement>();
        for (ContainerGuiDefinition.Element value : definition.elements) {
            if (value instanceof ContainerGuiDefinition.ControlElement) {
                ContainerGuiDefinition.ControlElement element = (ContainerGuiDefinition.ControlElement) value;
                if (element.focusable && enabled(element)) {
                    available.add(element);
                }
            }
        }
        if (available.isEmpty()) {
            return false;
        }
        int index = available.indexOf(focused);
        blurFocused();
        index = direction < 0 ? (index <= 0 ? available.size() - 1 : index - 1)
                : (index < 0 || index == available.size() - 1 ? 0 : index + 1);
        focus(available.get(index));
        return true;
    }

    private void drag(ContainerGuiDefinition.ControlElement element, int mouseX, int mouseY) {
        ContainerControlDefinition control = control(element);
        LuaTable input = pointerInput("mouse", element, mouseX, mouseY, 0, "drag");
        input.set("deltaX", mouseX - lastPointerX);
        input.set("deltaY", mouseY - lastPointerY);
        lastPointerX = mouseX;
        lastPointerY = mouseY;
        if (control.type == ContainerControlDefinition.Type.NUMBER) {
            changeSlider(element, mouseX, mouseY, input);
        } else if (control.type == ContainerControlDefinition.Type.CUSTOM) {
            luaContainer.controls().custom(control, input);
        }
    }

    private void changeSlider(ContainerGuiDefinition.ControlElement element, int mouseX, int mouseY, LuaTable input) {
        int originX = (width - xSize) / 2 + anchoredX(element.anchor, element.x, element.width);
        int originY = (height - ySize) / 2 + anchoredY(element.anchor, element.y, element.height);
        double ratio = "vertical".equals(element.orientation)
                ? 1.0D - (mouseY - originY) / (double) Math.max(1, element.height - 1)
                : (mouseX - originX) / (double) Math.max(1, element.width - 1);
        ratio = Math.max(0.0D, Math.min(1.0D, ratio));
        ContainerControlDefinition control = control(element);
        recordResult(control, luaContainer.controls().changeNumber(control,
                control.minimum + ratio * (control.maximum - control.minimum), input));
    }

    private boolean adjustNumber(ContainerControlDefinition control, int keyCode, LuaTable input) {
        double direction;
        double amount;
        if (keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_DOWN) {
            direction = -1.0D;
            amount = control.step;
        } else if (keyCode == Keyboard.KEY_RIGHT || keyCode == Keyboard.KEY_UP) {
            direction = 1.0D;
            amount = control.step;
        } else if (keyCode == Keyboard.KEY_PRIOR) {
            direction = 1.0D;
            amount = control.pageStep;
        } else if (keyCode == Keyboard.KEY_NEXT) {
            direction = -1.0D;
            amount = control.pageStep;
        } else {
            return false;
        }
        double current = ((Number) luaContainer.controls().value(control)).doubleValue();
        recordResult(control, luaContainer.controls().changeNumber(control, current + direction * amount, input));
        return true;
    }

    private boolean editText(ContainerControlDefinition control, char typedChar, int keyCode, LuaTable input) {
        String value = draft(control);
        if (keyCode == Keyboard.KEY_ESCAPE) {
            drafts.remove(control.name);
            draftOrigins.remove(control.name);
            focused = null;
            selectionAnchor = -1;
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            if (recordResult(control, luaContainer.controls().editText(control, value, input, true))) {
                String committed = String.valueOf(luaContainer.controls().value(control));
                drafts.put(control.name, committed);
                draftOrigins.put(control.name, committed);
                caret = Math.min(caret, committed.length());
                selectionAnchor = -1;
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_LEFT) {
            updateSelectionAnchor();
            caret = Math.max(0, caret - 1);
            return true;
        }
        if (keyCode == Keyboard.KEY_RIGHT) {
            updateSelectionAnchor();
            caret = Math.min(value.length(), caret + 1);
            return true;
        }
        if (keyCode == Keyboard.KEY_HOME) {
            updateSelectionAnchor();
            caret = 0;
            return true;
        }
        if (keyCode == Keyboard.KEY_END) {
            updateSelectionAnchor();
            caret = value.length();
            return true;
        }
        String changed = null;
        if (isControlDown() && keyCode == Keyboard.KEY_A) {
            selectionAnchor = 0;
            caret = value.length();
            return true;
        } else if (isControlDown() && keyCode == Keyboard.KEY_C && hasSelection()) {
            copyToClipboard(value.substring(selectionStart(), selectionEnd()));
            return true;
        } else if (isControlDown() && keyCode == Keyboard.KEY_X && hasSelection()) {
            copyToClipboard(value.substring(selectionStart(), selectionEnd()));
            changed = replaceSelection(value, "");
        } else if (keyCode == Keyboard.KEY_BACK && hasSelection()) {
            changed = replaceSelection(value, "");
        } else if (keyCode == Keyboard.KEY_DELETE && hasSelection()) {
            changed = replaceSelection(value, "");
        } else if (keyCode == Keyboard.KEY_BACK && caret > 0) {
            changed = value.substring(0, caret - 1) + value.substring(caret);
            caret--;
        } else if (keyCode == Keyboard.KEY_DELETE && caret < value.length()) {
            changed = value.substring(0, caret) + value.substring(caret + 1);
        } else if (isControlDown() && keyCode == Keyboard.KEY_V) {
            String clipboard = getClipboardString();
            if (clipboard != null) {
                changed = replaceSelection(value, printable(clipboard));
            }
        } else if (typedChar >= 32 && typedChar != 127
                && value.length() - (hasSelection() ? selectionEnd() - selectionStart() : 0) < control.maximumLength) {
            changed = replaceSelection(value, String.valueOf(typedChar));
        }
        if (changed == null) {
            return false;
        }
        if (changed.length() > control.maximumLength) {
            changed = changed.substring(0, control.maximumLength);
        }
        caret = Math.min(caret, changed.length());
        if (!recordResult(control, luaContainer.controls().editText(control, changed, input, false))) {
            return true;
        }
        drafts.put(control.name, changed);
        selectionAnchor = -1;
        return true;
    }

    private static void copyToClipboard(String value) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value), null);
        } catch (RuntimeException ignored) {
            // Clipboard ownership can be temporarily unavailable. Text editing remains usable.
        }
    }

    private void updateSelectionAnchor() {
        if (isShiftDown()) {
            if (selectionAnchor < 0) {
                selectionAnchor = caret;
            }
        } else {
            selectionAnchor = -1;
        }
    }

    private boolean hasSelection() {
        return selectionAnchor >= 0 && selectionAnchor != caret;
    }

    private int selectionStart() {
        return Math.min(selectionAnchor, caret);
    }

    private int selectionEnd() {
        return Math.max(selectionAnchor, caret);
    }

    private String replaceSelection(String value, String insertion) {
        int start = hasSelection() ? selectionStart() : caret;
        int end = hasSelection() ? selectionEnd() : caret;
        String result = value.substring(0, start) + insertion + value.substring(end);
        caret = start + insertion.length();
        selectionAnchor = -1;
        return result;
    }

    private String draft(ContainerControlDefinition control) {
        String value = drafts.get(control.name);
        return value == null ? String.valueOf(luaContainer.controls().value(control)) : value;
    }

    private void placeTextCaret(ContainerGuiDefinition.ControlElement element, int mouseX) {
        ContainerControlDefinition control = control(element);
        String value = draft(control);
        int originX = (width - xSize) / 2 + anchoredX(element.anchor, element.x, element.width) + 4;
        int localX = Math.max(0, mouseX - originX);
        int best = textOffset;
        for (int index = textOffset; index <= value.length(); index++) {
            int widthToIndex = fontRenderer.getStringWidth(value.substring(textOffset, index));
            if (widthToIndex > localX) {
                break;
            }
            best = index;
        }
        caret = best;
        selectionAnchor = -1;
    }

    private void keepCaretVisible(String value, int availableWidth) {
        textOffset = Math.max(0, Math.min(textOffset, caret));
        while (textOffset < caret
                && fontRenderer.getStringWidth(value.substring(textOffset, caret)) > availableWidth) {
            textOffset++;
        }
        while (textOffset > 0
                && fontRenderer.getStringWidth(value.substring(textOffset - 1, caret)) <= availableWidth) {
            textOffset--;
        }
    }

    private int visibleTextEnd(String value, int start, int availableWidth) {
        int end = start;
        while (end < value.length()
                && fontRenderer.getStringWidth(value.substring(start, end + 1)) <= availableWidth) {
            end++;
        }
        return end;
    }

    private void playClickSound() {
        mc.sndManager.playSoundFX("random.click", 1.0F, 1.0F);
    }

    private boolean recordResult(ContainerControlDefinition control, boolean accepted) {
        if (accepted) {
            invalidUntil.remove(control.name);
        } else {
            invalidUntil.put(control.name, Long.valueOf(System.currentTimeMillis() + 750L));
        }
        return accepted;
    }

    private boolean invalid(ContainerGuiDefinition.ControlElement element) {
        Long until = invalidUntil.get(element.control);
        if (until == null) {
            return false;
        }
        if (until.longValue() > System.currentTimeMillis()) {
            return true;
        }
        invalidUntil.remove(element.control);
        return false;
    }

    private String controlText(ContainerGuiDefinition.ControlElement element, ContainerControlDefinition control) {
        if (control.type == ContainerControlDefinition.Type.TEXT) {
            return draft(control);
        }
        if (element.text != null) {
            return element.text;
        }
        if (control.type == ContainerControlDefinition.Type.CHOICE) {
            return String.valueOf(luaContainer.controls().value(control));
        }
        return null;
    }

    private LuaTable pointerInput(String source, ContainerGuiDefinition.ControlElement element,
            int mouseX, int mouseY, int button, String phase) {
        int x = (width - xSize) / 2 + anchoredX(element.anchor, element.x, element.width);
        int y = (height - ySize) / 2 + anchoredY(element.anchor, element.y, element.height);
        LuaTable input = ContainerControlRuntime.input(source);
        input.set("phase", phase);
        input.set("button", button);
        input.set("x", Math.max(0, Math.min(element.width - 1, mouseX - x)));
        input.set("y", Math.max(0, Math.min(element.height - 1, mouseY - y)));
        input.set("deltaX", 0);
        input.set("deltaY", 0);
        input.set("shift", org.luaj.vm2.LuaValue.valueOf(isShiftDown()));
        input.set("control", org.luaj.vm2.LuaValue.valueOf(isControlDown()));
        return input;
    }

    private LuaTable keyInput(ContainerGuiDefinition.ControlElement element, char character, int keyCode) {
        LuaTable input = ContainerControlRuntime.input("keyboard");
        input.set("key", keyCode);
        input.set("character", String.valueOf(character));
        input.set("shift", org.luaj.vm2.LuaValue.valueOf(isShiftDown()));
        input.set("control", org.luaj.vm2.LuaValue.valueOf(isControlDown()));
        return input;
    }

    private static boolean isShiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    private static boolean isControlDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
    }

    private static String printable(String value) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character >= 32 && character != 127) {
                result.append(character);
            }
        }
        return result.toString();
    }

    private void drawTexture(ContainerGuiDefinition.Texture texture, int x, int y, int width, int height) {
        if (texture.border > 0 && width >= texture.border * 2 && height >= texture.border * 2) {
            drawNineSlice(texture, x, y, width, height);
            return;
        }
        drawTextureRegion(texture.resource, x, y, texture.u, texture.v, texture.width, texture.height, width, height,
                texture.textureWidth, texture.textureHeight);
    }

    private void drawNineSlice(ContainerGuiDefinition.Texture texture, int x, int y, int width, int height) {
        int border = texture.border;
        int middleSourceWidth = texture.width - border * 2;
        int middleSourceHeight = texture.height - border * 2;
        int middleWidth = width - border * 2;
        int middleHeight = height - border * 2;
        int[] sourceX = {0, border, texture.width - border};
        int[] sourceY = {0, border, texture.height - border};
        int[] sourceWidths = {border, middleSourceWidth, border};
        int[] sourceHeights = {border, middleSourceHeight, border};
        int[] drawX = {x, x + border, x + border + middleWidth};
        int[] drawY = {y, y + border, y + border + middleHeight};
        int[] drawWidths = {border, middleWidth, border};
        int[] drawHeights = {border, middleHeight, border};
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                drawTextureRegion(texture.resource, drawX[column], drawY[row],
                        texture.u + sourceX[column], texture.v + sourceY[row],
                        sourceWidths[column], sourceHeights[row], drawWidths[column], drawHeights[row],
                        texture.textureWidth, texture.textureHeight);
            }
        }
    }

    private void drawTextureRegion(String resource, int x, int y, int u, int v, int sourceWidth, int sourceHeight,
            int drawWidth, int drawHeight, int textureWidth, int textureHeight) {
        mc.renderEngine.bindTexture(mc.renderEngine.getTexture(resource));
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        float minU = u / (float) textureWidth;
        float maxU = (u + sourceWidth) / (float) textureWidth;
        float minV = v / (float) textureHeight;
        float maxV = (v + sourceHeight) / (float) textureHeight;
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + drawHeight, zLevel, minU, maxV);
        tessellator.addVertexWithUV(x + drawWidth, y + drawHeight, zLevel, maxU, maxV);
        tessellator.addVertexWithUV(x + drawWidth, y, zLevel, maxU, minV);
        tessellator.addVertexWithUV(x, y, zLevel, minU, minV);
        tessellator.draw();
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }
}

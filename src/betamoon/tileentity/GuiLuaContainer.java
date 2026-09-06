package betamoon.tileentity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import net.minecraft.src.FontRenderer;
import net.minecraft.src.GuiContainer;
import net.minecraft.src.InventoryPlayer;
import net.minecraft.src.ItemStack;
import net.minecraft.src.RenderItem;
import net.minecraft.src.Tessellator;
import org.lwjgl.opengl.GL11;

/** Renders prevalidated Lua container definitions without exposing rendering APIs to Lua. */
public final class GuiLuaContainer extends GuiContainer {
    private static final RenderItem ITEM_RENDERER = new RenderItem();
    private final LuaTileEntity entity;
    private final ContainerGuiDefinition definition;

    public GuiLuaContainer(InventoryPlayer player, LuaTileEntity entity, ContainerGuiDefinition definition) {
        super(new LuaContainer(player, entity, definition.container));
        this.entity = entity; this.definition = definition;
        this.xSize = definition.width; this.ySize = definition.height;
    }

    public boolean doesGuiPauseGame() { return definition.pauseGame; }

    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawElementTooltip(mouseX, mouseY);
    }

    protected void drawGuiContainerForegroundLayer() {
        drawLabel(definition.title);
        drawLabel(definition.playerInventoryLabel);
        drawElements(2);
    }

    protected void drawGuiContainerBackgroundLayer(float partialTicks) {
        int originX = (width - xSize) / 2, originY = (height - ySize) / 2;
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
        if (background.drawSlotFrames) drawSlotFrames(x, y);
    }

    /** Draws the two variable-height pieces used by vanilla chest screens. */
    private void drawChestBackground(int x, int y, int rows) {
        ContainerGuiDefinition.Texture texture = definition.background.texture;
        if (texture == null) texture = new ContainerGuiDefinition.Texture("/gui/container.png", 0, 0, 176, 166, 256, 256);
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
        Iterator slots = definition.container.slots.iterator();
        while (slots.hasNext()) {
            ContainerDefinition.SlotDefinition slot = (ContainerDefinition.SlotDefinition) slots.next();
            drawSlotFrame(originX + slot.x - 1, originY + slot.y - 1);
        }
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            drawSlotFrame(originX + definition.container.playerX + column * 18 - 1,
                originY + definition.container.playerY + row * 18 - 1);
        if (definition.container.includeHotbar) for (int column = 0; column < 9; column++)
            drawSlotFrame(originX + definition.container.playerX + column * 18 - 1,
                originY + definition.container.playerY + 58 - 1);
    }

    private void drawSlotFrame(int x, int y) {
        drawRect(x, y, x + 18, y + 18, 0xFF373737);
        drawRect(x + 1, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        drawRect(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
    }

    private void drawLabel(ContainerGuiDefinition.Label label) {
        if (label == null || label.text == null) return;
        drawAlignedText(label.text, label.x, label.y, label.width, label.align, label.color, label.shadow);
    }

    private void drawElements(int layer) {
        for (int i = 0; i < definition.elements.size(); i++) {
            ContainerGuiDefinition.Element element = (ContainerGuiDefinition.Element) definition.elements.get(i);
            if (element.layer == layer && visible(element.visibleWhen)) drawElement(element);
        }
    }

    private void drawElement(ContainerGuiDefinition.Element element) {
        int elementWidth = elementWidth(element), elementHeight = elementHeight(element);
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
            ContainerGuiDefinition.Texture image = (ContainerGuiDefinition.Texture) state.states.get(String.valueOf(current));
            if (image == null) image = state.defaultImage;
            if (image != null) drawTexture(image, x, y, image.width, image.height);
        } else if (element instanceof ContainerGuiDefinition.RectangleElement) {
            ContainerGuiDefinition.RectangleElement rectangle = (ContainerGuiDefinition.RectangleElement) element;
            int color = rectangle.color >>> 24 == 0 ? rectangle.color | 0xFF000000 : rectangle.color;
            drawRect(x, y, x + rectangle.width, y + rectangle.height, color);
        } else if (element instanceof ContainerGuiDefinition.ItemElement) {
            ContainerGuiDefinition.ItemElement item = (ContainerGuiDefinition.ItemElement) element;
            ItemStack stack = item.slot == null ? item.item : entity.getStackInNamedSlot(item.slot);
            if (stack != null) {
                GL11.glEnable(GL11.GL_LIGHTING);
                ITEM_RENDERER.renderItemIntoGUI(fontRenderer, mc.renderEngine, stack, x, y);
                if (item.showCount) ITEM_RENDERER.renderItemOverlayIntoGUI(fontRenderer, mc.renderEngine, stack, x, y);
                GL11.glDisable(GL11.GL_LIGHTING);
            }
        }
    }

    private void drawProgress(ContainerGuiDefinition.ProgressElement progress, int x, int y) {
        int maximum = progress.maximumField == null ? progress.maximum : entity.getDataInt(progress.maximumField);
        if (maximum <= 0) maximum = 1;
        int value = Math.max(0, Math.min(maximum, entity.getDataInt(progress.field)));
        if (progress.background != null) drawTexture(progress.background, x, y,
            progress.background.width, progress.background.height);
        if (value == 0 && progress.hideWhenEmpty) return;
        ContainerGuiDefinition.Texture texture = progress.image;
        int amount;
        if ("top_to_bottom".equals(progress.direction) || "bottom_to_top".equals(progress.direction)) {
            amount = value * texture.height / maximum;
            if (value > 0) amount = Math.max(progress.minimumPixels, amount);
            amount = Math.min(texture.height, amount);
            if (amount > 0 && "bottom_to_top".equals(progress.direction))
                drawTextureRegion(texture.resource, x, y + texture.height - amount, texture.u,
                    texture.v + texture.height - amount, texture.width, amount, texture.width, amount,
                    texture.textureWidth, texture.textureHeight);
            else if (amount > 0) drawTextureRegion(texture.resource, x, y, texture.u, texture.v,
                texture.width, amount, texture.width, amount, texture.textureWidth, texture.textureHeight);
        } else {
            amount = value * texture.width / maximum;
            if (value > 0) amount = Math.max(progress.minimumPixels, amount);
            amount = Math.min(texture.width, amount);
            if (amount > 0 && "right_to_left".equals(progress.direction))
                drawTextureRegion(texture.resource, x + texture.width - amount, y,
                    texture.u + texture.width - amount, texture.v, amount, texture.height,
                    amount, texture.height, texture.textureWidth, texture.textureHeight);
            else if (amount > 0) drawTextureRegion(texture.resource, x, y, texture.u, texture.v,
                amount, texture.height, amount, texture.height, texture.textureWidth, texture.textureHeight);
        }
    }

    private boolean visible(ContainerGuiDefinition.Condition condition) {
        if (condition == null) return true;
        if ("all".equals(condition.operator) || "any".equals(condition.operator)) {
            boolean all = "all".equals(condition.operator);
            for (int i = 0; i < condition.children.size(); i++) {
                boolean child = visible((ContainerGuiDefinition.Condition) condition.children.get(i));
                if (all && !child) return false; if (!all && child) return true;
            }
            return all;
        }
        Object actual = entity.getDataValue(condition.field), expected = condition.expected;
        int comparison = compare(actual, expected);
        if ("equals".equals(condition.operator)) return equal(actual, expected);
        if ("notEquals".equals(condition.operator)) return !equal(actual, expected);
        if ("greaterThan".equals(condition.operator)) return comparison > 0;
        if ("greaterOrEqual".equals(condition.operator)) return comparison >= 0;
        if ("lessThan".equals(condition.operator)) return comparison < 0;
        return comparison <= 0;
    }

    private static boolean equal(Object left, Object right) {
        if (left instanceof Number && right instanceof Number)
            return Double.compare(((Number) left).doubleValue(), ((Number) right).doubleValue()) == 0;
        return left == null ? right == null : left.equals(right);
    }

    private static int compare(Object left, Object right) {
        if (left instanceof Number && right instanceof Number)
            return Double.compare(((Number) left).doubleValue(), ((Number) right).doubleValue());
        return String.valueOf(left).compareTo(String.valueOf(right));
    }

    private String resolveText(ContainerGuiDefinition.TextElement element) {
        if (element.field == null) return element.text;
        Object value = entity.getDataValue(element.field);
        if (element.format == null) return String.valueOf(value);
        try { return String.format(Locale.ENGLISH, element.format, new Object[] { value }); }
        catch (RuntimeException ignored) { return String.valueOf(value); }
    }

    private void drawAlignedText(String text, int x, int y, int areaWidth, String align, int color, boolean shadow) {
        if (text == null) return;
        int drawX = x;
        if (areaWidth > 0 && "center".equals(align)) drawX += (areaWidth - fontRenderer.getStringWidth(text)) / 2;
        else if (areaWidth > 0 && "right".equals(align)) drawX += areaWidth - fontRenderer.getStringWidth(text);
        if (shadow) fontRenderer.drawStringWithShadow(text, drawX, y, color);
        else fontRenderer.drawString(text, drawX, y, color);
    }

    private void drawElementTooltip(int mouseX, int mouseY) {
        int originX = (width - xSize) / 2, originY = (height - ySize) / 2;
        for (int i = definition.elements.size() - 1; i >= 0; i--) {
            ContainerGuiDefinition.Element element = (ContainerGuiDefinition.Element) definition.elements.get(i);
            if (element.tooltip.isEmpty() || !visible(element.visibleWhen)) continue;
            int elementWidth = elementWidth(element), elementHeight = elementHeight(element);
            int x = originX + anchoredX(element.anchor, element.x, elementWidth);
            int y = originY + anchoredY(element.anchor, element.y, elementHeight);
            if (mouseX >= x && mouseX < x + elementWidth && mouseY >= y && mouseY < y + elementHeight) {
                drawTooltip(resolveTooltip(element.tooltip), mouseX, mouseY); return;
            }
        }
    }

    private List resolveTooltip(List source) {
        List result = new ArrayList();
        for (int i = 0; i < source.size(); i++) {
            String line = (String) source.get(i);
            Iterator fields = definition.container.tileEntity.fields.keySet().iterator();
            while (fields.hasNext()) {
                String field = (String) fields.next();
                line = line.replace("{" + field + "}", String.valueOf(entity.getDataValue(field)));
            }
            result.add(line);
        }
        return result;
    }

    private void drawTooltip(List lines, int mouseX, int mouseY) {
        if (lines.isEmpty()) return;
        int textWidth = 0;
        for (int i = 0; i < lines.size(); i++) textWidth = Math.max(textWidth, fontRenderer.getStringWidth((String) lines.get(i)));
        int boxWidth = textWidth + 8, boxHeight = lines.size() * 10 + 6;
        int x = mouseX + 12, y = mouseY + 8;
        if (x + boxWidth > width - 4) x = width - boxWidth - 4;
        if (y + boxHeight > height - 4) y = height - boxHeight - 4;
        drawRect(x, y, x + boxWidth, y + boxHeight, 0xF0100010);
        drawRect(x, y, x + boxWidth, y + 1, 0xFF5000FF);
        for (int i = 0; i < lines.size(); i++) fontRenderer.drawStringWithShadow((String) lines.get(i), x + 4, y + 4 + i * 10, 0xFFFFFF);
    }

    private int elementWidth(ContainerGuiDefinition.Element element) {
        if (element instanceof ContainerGuiDefinition.ImageElement) return ((ContainerGuiDefinition.ImageElement) element).width;
        if (element instanceof ContainerGuiDefinition.ProgressElement) return ((ContainerGuiDefinition.ProgressElement) element).image.width;
        if (element instanceof ContainerGuiDefinition.StateImageElement) {
            ContainerGuiDefinition.StateImageElement state = (ContainerGuiDefinition.StateImageElement) element;
            ContainerGuiDefinition.Texture texture = state.defaultImage != null ? state.defaultImage
                : (ContainerGuiDefinition.Texture) state.states.values().iterator().next(); return texture.width;
        }
        if (element instanceof ContainerGuiDefinition.RectangleElement) return ((ContainerGuiDefinition.RectangleElement) element).width;
        if (element instanceof ContainerGuiDefinition.TooltipElement) return ((ContainerGuiDefinition.TooltipElement) element).width;
        if (element instanceof ContainerGuiDefinition.ItemElement) return 16;
        ContainerGuiDefinition.TextElement text = (ContainerGuiDefinition.TextElement) element;
        return text.width > 0 ? text.width : fontRenderer.getStringWidth(resolveText(text));
    }

    private int elementHeight(ContainerGuiDefinition.Element element) {
        if (element instanceof ContainerGuiDefinition.ImageElement) return ((ContainerGuiDefinition.ImageElement) element).height;
        if (element instanceof ContainerGuiDefinition.ProgressElement) return ((ContainerGuiDefinition.ProgressElement) element).image.height;
        if (element instanceof ContainerGuiDefinition.StateImageElement) {
            ContainerGuiDefinition.StateImageElement state = (ContainerGuiDefinition.StateImageElement) element;
            ContainerGuiDefinition.Texture texture = state.defaultImage != null ? state.defaultImage
                : (ContainerGuiDefinition.Texture) state.states.values().iterator().next(); return texture.height;
        }
        if (element instanceof ContainerGuiDefinition.RectangleElement) return ((ContainerGuiDefinition.RectangleElement) element).height;
        if (element instanceof ContainerGuiDefinition.TooltipElement) return ((ContainerGuiDefinition.TooltipElement) element).height;
        return element instanceof ContainerGuiDefinition.ItemElement ? 16 : 8;
    }

    private int anchoredX(String anchor, int x, int elementWidth) {
        if (anchor.endsWith("center") || "center".equals(anchor)) return (xSize - elementWidth) / 2 + x;
        if (anchor.endsWith("right")) return xSize - elementWidth + x;
        return x;
    }

    private int anchoredY(String anchor, int y, int elementHeight) {
        if ("center".equals(anchor)) return (ySize - elementHeight) / 2 + y;
        if (anchor.startsWith("bottom")) return ySize - elementHeight + y;
        return y;
    }

    private void drawTexture(ContainerGuiDefinition.Texture texture, int x, int y, int width, int height) {
        drawTextureRegion(texture.resource, x, y, texture.u, texture.v, texture.width, texture.height,
            width, height, texture.textureWidth, texture.textureHeight);
    }

    private void drawTextureRegion(String resource, int x, int y, int u, int v, int sourceWidth,
        int sourceHeight, int drawWidth, int drawHeight, int textureWidth, int textureHeight) {
        mc.renderEngine.bindTexture(mc.renderEngine.getTexture(resource));
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        float minU = u / (float) textureWidth, maxU = (u + sourceWidth) / (float) textureWidth;
        float minV = v / (float) textureHeight, maxV = (v + sourceHeight) / (float) textureHeight;
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

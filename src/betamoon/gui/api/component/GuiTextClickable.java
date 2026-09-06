package betamoon.gui.api.component;

import betamoon.gui.api.util.GuiColors;
import betamoon.gui.api.util.GuiText;
import betamoon.gui.api.util.GuiUtils;
import net.minecraft.src.FontRenderer;
import java.util.List;
import java.util.function.Supplier;

/**
 * Clickable text component with optional tooltip and custom click action.
 */
public class GuiTextClickable extends GuiComponentBase {
    private String text;
    private String tooltip;
    private IGuiAction action;
    private Supplier textSupplier;
    private Supplier tooltipSupplier;
    private int screenWidth;
    private int screenHeight;
    private boolean wrapText;

    public GuiTextClickable() {
    }

    /**
     * Creates clickable text that runs the provided action on click.
     *
     * @param action action invoked when the text is clicked
     */
    public GuiTextClickable(IGuiAction action) {
        this.action = action;
    }

    /**
     * Sets the static text to render.
     *
     * @param text text to render
     */
    public void setText(String text) {
        this.text = text;
        this.textSupplier = null;
    }

    /**
     * Sets the tooltip to show on hover.
     *
     * @param tooltip tooltip text
     */
    public void setTooltip(String tooltip) {
        this.tooltip = tooltip;
        this.tooltipSupplier = null;
    }

    /**
     * Updates the click action.
     *
     * @param action action invoked on click
     */
    public void setAction(IGuiAction action) {
        this.action = action;
    }

    /**
     * Provides text lazily for dynamic updates.
     *
     * @param textSupplier supplier returning current text
     */
    public void setTextSupplier(Supplier textSupplier) {
        this.textSupplier = textSupplier;
    }

    /**
     * Provides tooltip text lazily for dynamic updates.
     *
     * @param tooltipSupplier supplier returning current tooltip
     */
    public void setTooltipSupplier(Supplier tooltipSupplier) {
        this.tooltipSupplier = tooltipSupplier;
    }

    /**
     * Sets the screen size used for tooltip placement.
     *
     * @param screenWidth current screen width
     * @param screenHeight current screen height
     */
    public void setScreenSize(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    /**
     * Enables wrapping within the component bounds for long clickable text.
     */
    public void setWrapText(boolean wrapText) {
        this.wrapText = wrapText;
    }

    /**
     * Draws the text and hover underline/tooltip.
     */
    public void draw(FontRenderer font, int mouseX, int mouseY, float partialTicks) {
        if (font == null) {
            return;
        }
        String resolvedText = resolveText();
        if (resolvedText == null || resolvedText.isEmpty()) {
            return;
        }
        boolean hovered = isMouseOver(mouseX, mouseY);
        int color = hovered ? GuiColors.LINK_PATH_HOVER : GuiColors.LINK_PATH;
        int availableWidth = Math.max(0, right - left);
        if (wrapText && availableWidth > 0) {
            font.func_27278_a(resolvedText, left, top, availableWidth, color);
        } else {
            font.drawStringWithShadow(resolvedText, left, top, color);
        }
        if (hovered) {
            if (!wrapText) {
                int height = GuiText.getLineHeight(font);
                // Draw underline and tooltip only while hovered to avoid clutter.
                GuiUtils.drawRect(left, top + height + 1, left + font.getStringWidth(resolvedText),
                    top + height + 2, GuiColors.LINK_PATH_HOVER_UNDERLINE);
            }
            String resolvedTooltip = resolveTooltip();
            if (resolvedTooltip != null && !resolvedTooltip.isEmpty()) {
                GuiText.drawTooltip(font, screenWidth, screenHeight, resolvedTooltip, mouseX, mouseY);
            }
        }
    }

    /**
     * Handles click events for the text.
     *
     * @return true if the click was handled
     */
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0 || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        if (action != null) {
            action.onPress();
            return true;
        }
        return false;
    }

    /**
     * Resolves text using the supplier when present.
     */
    private String resolveText() {
        if (textSupplier != null) {
            return (String) textSupplier.get();
        }
        return text;
    }

    /**
     * Resolves tooltip text using the supplier when present.
     */
    private String resolveTooltip() {
        if (tooltipSupplier != null) {
            return (String) tooltipSupplier.get();
        }
        return tooltip;
    }

    /**
     * Draws text with an inline clickable segment and registers it for click handling.
     *
     * @return the rendered height for this line
     */
    public int drawInline(FontRenderer font, String fullText, String linkText, IGuiAction action, int left,
        int top, int maxWidth, int screenWidth, int screenHeight, int textColor, int mouseX, int mouseY,
        float partialTicks, List linkSink) {
        if (font == null || fullText == null) {
            return 0;
        }
        if (linkText == null || linkText.length() == 0) {
            font.func_27278_a(fullText, left, top, maxWidth, textColor);
            return font.func_27277_a(fullText, maxWidth);
        }
        int linkIndex = fullText.indexOf(linkText);
        if (linkIndex < 0) {
            font.func_27278_a(fullText, left, top, maxWidth, textColor);
            return font.func_27277_a(fullText, maxWidth);
        }
        String remainder = trimInlineRemainder(fullText.substring(linkIndex + linkText.length()));
        int linkWidth = font.getStringWidth(linkText);
        int linkHeight = GuiText.getLineHeight(font);
        GuiTextClickable link = new GuiTextClickable(action);
        link.setText(linkText);
        link.setBounds(left, top, left + linkWidth, top + linkHeight);
        link.setScreenSize(screenWidth, screenHeight);
        // Track the link so mouse clicks can be routed later.
        link.draw(font, mouseX, mouseY, partialTicks);
        if (linkSink != null) {
            linkSink.add(link);
        }
        int remainderX = left + linkWidth + 4;
        int remainderWidth = Math.max(10, maxWidth - linkWidth - 4);
        if (remainder.length() > 0) {
            font.func_27278_a(remainder, remainderX, top, remainderWidth, textColor);
        }
        int remainderHeight = remainder.length() > 0 ? font.func_27277_a(remainder, remainderWidth) : 0;
        return Math.max(linkHeight, remainderHeight);
    }

    private static String trimInlineRemainder(String value) {
        if (value == null || value.length() == 0) {
            return "";
        }
        int index = 0;
        while (index < value.length()) {
            char ch = value.charAt(index);
            if (ch == ' ' || ch == '\t') {
                index++;
                continue;
            }
            break;
        }
        return value.substring(index);
    }
}

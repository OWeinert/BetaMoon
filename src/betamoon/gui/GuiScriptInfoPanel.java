package betamoon.gui;

import betamoon.gui.api.GuiColors;
import betamoon.gui.api.GuiText;
import betamoon.gui.api.GuiUtils;
import betamoon.gui.api.ScrollState;
import betamoon.scriptloader.LuaScriptRegistry;
import betamoon.scriptloader.ScriptMod;
import java.util.List;
import net.minecraft.src.FontRenderer;

public final class GuiScriptInfoPanel {
    private static final String LABEL_DESCRIPTION = "Description";
    private static final String LABEL_ERRORS = "Errors";
    private static final String LABEL_DEPENDENCIES = "Dependencies";
    private static final String SUFFIX_DEPENDENCY_MISSING = "(Missing)";
    private static final int SECTION_PADDING = 64;
    private static final int CONTENT_PADDING = 6;
    private static final int LINE_SPACING = 16;
    private static final int DEPENDENCY_LINE_HEIGHT = 12;
    private final ScrollState scrollState = new ScrollState();

    private int detailLeft;
    private int detailRight;
    private int detailTop;
    private int detailBottom;

    /**
     * Draws script details for the selected entry.
     *
     * @param font font renderer
     * @param selected selected script entry
     * @param detailLeft left x position
     * @param detailRight right x position
     * @param headerY y position for the header row
     * @param detailTop top y position for details
     * @param detailBottom bottom y position for details
     * @param headerScale scale factor for the title
     */
    public void draw(FontRenderer font, ScriptMod selected, int detailLeft, int detailRight, int headerY, int detailTop, int detailBottom, int screenWidth, int screenHeight, int displayWidth, int displayHeight, float headerScale) {
        if (selected == null) {
            return;
        }
        this.detailLeft = detailLeft;
        this.detailRight = detailRight;
        this.detailTop = detailTop;
        this.detailBottom = detailBottom;
        int contentWidth = detailRight - detailLeft;
        // Title row: script name and version.
        String title = selected.getDisplayName() + "  v" + selected.getVersion();
        GuiUtils.drawScaledString(font, title, detailLeft, headerY, GuiColors.TEXT_PRIMARY, headerScale);

        // Resolve content flags and starting Y based on the current scroll offset.
        int y = detailTop - scrollState.getScrollOffset();
        String description = selected.getDescription();
        boolean hasDescription = description != null && !description.trim().isEmpty();
        String failure = selected.getFailureReason();
        boolean hasErrors = selected.isFailed() && failure != null && !failure.trim().isEmpty();
        List dependencies = selected.getDependencies();
        boolean hasDependencies = dependencies != null && !dependencies.isEmpty();

        // Measure full content height to drive scrolling limits.
        int contentHeight = calculateContentHeight(font, contentWidth, hasDescription ? description : null,
            hasErrors ? failure : null, hasDependencies ? dependencies : null);
        scrollState.setBounds(detailLeft, detailTop, detailRight, detailBottom);
        scrollState.updateContentHeight(contentHeight);
        y = detailTop - scrollState.getScrollOffset();
        int scissorTop = detailTop - 2;
        int scissorBottom = detailBottom + 2;
        // Clip body text so it doesn't overlap the title row.
        GuiUtils.beginScissor(detailLeft, scissorTop, detailRight, scissorBottom, screenWidth, screenHeight, displayWidth, displayHeight);

        // Description is always shown, even when empty.
        float descriptionScale = 1.25F;
        GuiUtils.drawScaledStringUL(font, LABEL_DESCRIPTION, detailLeft, y, GuiColors.TEXT_PRIMARY, descriptionScale);
        y += (int) (10 * descriptionScale) + CONTENT_PADDING;
        if (hasDescription) {
            y += drawWrappedClipped(font, description, detailLeft, y, contentWidth, GuiColors.TEXT_PRIMARY, detailBottom)
                + CONTENT_PADDING;
        }
        y += LINE_SPACING;

        if (hasDescription && hasErrors) {
            // Separator between description and errors.
            GuiUtils.drawHorizontalLine(detailLeft, detailRight, y + 2, GuiUtils.COLOR_LIST_SEPERATOR);
            y += SECTION_PADDING;
        }

        // Only show errors when the script failed.
        if (hasErrors) {
            float errorScale = 1.25F;
            // Errors header with underline.
            GuiUtils.drawScaledStringUL(font, LABEL_ERRORS, detailLeft, y, GuiColors.TEXT_ERROR, errorScale);
            y += (int) (10 * errorScale) + CONTENT_PADDING;
            y += drawWrappedClipped(font, failure, detailLeft, y, contentWidth, GuiColors.TEXT_ERROR, detailBottom)
                + CONTENT_PADDING;
            y += LINE_SPACING;
        }

        // Show dependencies if existent
        if (hasDependencies) {
            float dependencyScale = 1.25F;
            // Dependencies header with underline.
            GuiUtils.drawScaledStringUL(font, LABEL_DEPENDENCIES, detailLeft, y, GuiColors.TEXT_PRIMARY, dependencyScale);
            y += (int) (10 * dependencyScale) + CONTENT_PADDING;
            y += drawDependenciesClipped(font, dependencies, selected.getMissingDependencies(), detailLeft, y, contentWidth, detailBottom)
                + CONTENT_PADDING;
        }

        scrollState.drawScrollbar(contentHeight);
        GuiUtils.endScissor();
    }

    /**
     * Routes mouse wheel and drag events into the scroll state.
     *
     * @param mouseX mouse x in GUI coordinates
     * @param mouseY mouse y in GUI coordinates
     * @param wheelDelta mouse wheel delta
     * @param mouseDown true when left mouse button is down
     */
    public void handleMouseInput(int mouseX, int mouseY, int wheelDelta, boolean mouseDown) {
        scrollState.handleMouseWheel(mouseX, mouseY, wheelDelta);
        scrollState.handleMouseDrag(mouseY, mouseDown);
    }

    /**
     * Handles scrollbar clicks.
     *
     * @param mouseX mouse x in GUI coordinates
     * @param mouseY mouse y in GUI coordinates
     * @param button mouse button id
     */
    public void mouseClicked(int mouseX, int mouseY, int button) {
        scrollState.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Ends any scrollbar drag on mouse release.
     *
     * @param button mouse button id
     */
    public void mouseReleased(int button) {
        scrollState.mouseReleased(button);
    }

    private int calculateContentHeight(FontRenderer font, int contentWidth, String description, String failure, List dependencies) {
        int height = 0;
        float sectionScale = 1.25F;
        height += (int) (10 * sectionScale) + CONTENT_PADDING;
        if (description != null && !description.trim().isEmpty()) {
            height += font.func_27277_a(description, contentWidth) + CONTENT_PADDING;
        }
        height += LINE_SPACING;
        if (description != null && !description.trim().isEmpty()
            && failure != null && !failure.trim().isEmpty()) {
            height += SECTION_PADDING;
        }
        if (failure != null && !failure.trim().isEmpty()) {
            height += (int) (10 * sectionScale) + CONTENT_PADDING;
            height += font.func_27277_a(failure, contentWidth) + CONTENT_PADDING;
            height += LINE_SPACING;
        }
        if (dependencies != null && !dependencies.isEmpty()) {
            height += (int) (10 * sectionScale) + CONTENT_PADDING;
            height += dependencies.size() * DEPENDENCY_LINE_HEIGHT + CONTENT_PADDING;
        }
        return height;
    }

    private int drawWrappedClipped(FontRenderer font, String text, int x, int y, int width, int color, int bottom) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int startY = y;
        // Manual line wrapping to keep control over clipping boundaries.
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String[] words = line.split(" ");
            int index = 0;
            while (index < words.length) {
                if (y + 8 > bottom) {
                    return y - startY;
                }
                String current = words[index];
                index++;
                while (index < words.length && font.getStringWidth(current + " " + words[index]) <= width) {
                    current = current + " " + words[index];
                    index++;
                }
                font.drawStringWithShadow(current, x, y, color);
                y += 8;
            }
            if (line.length() == 0) {
                if (y + 8 > bottom) {
                    return y - startY;
                }
                y += 8;
            }
        }
        return y - startY;
    }

    private int drawDependenciesClipped(FontRenderer font, List dependencies, List missingDeps, int x, int y, int width, int bottom) {
        if (dependencies == null || dependencies.isEmpty()) {
            return 0;
        }
        int startY = y;
        for (int i = 0; i < dependencies.size(); i++) {
            if (y + DEPENDENCY_LINE_HEIGHT > bottom) {
                return y - startY;
            }
            Object dep = dependencies.get(i);
            if (dep == null) {
                continue;
            }
            String name = dep.toString();
            boolean isMissing = false;
            if (missingDeps != null) {
                isMissing = missingDeps.contains(name);
            } else {
                isMissing = !LuaScriptRegistry.hasScriptName(name);
            }
            int color = isMissing ? GuiColors.TEXT_ERROR : GuiColors.TEXT_PRIMARY;
            String displayName = isMissing ? "- " + name + " " + SUFFIX_DEPENDENCY_MISSING : "- " + name;
            String display = GuiText.trimToWidth(font, displayName, width);
            font.drawStringWithShadow(display, x, y, color);
            y += DEPENDENCY_LINE_HEIGHT;
        }
        return y - startY;
    }
}

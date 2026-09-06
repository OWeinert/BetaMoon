package betamoon.gui;

import betamoon.gui.api.component.EnumScrollMode;
import betamoon.gui.api.component.GuiComponentBase;
import betamoon.gui.api.component.GuiTextClickable;
import betamoon.gui.api.component.GuiNonReloadableIndicator;
import betamoon.gui.api.component.IGuiAction;
import betamoon.gui.api.util.GuiColors;
import betamoon.gui.api.component.GuiScrollPanel;
import betamoon.gui.api.util.GuiText;
import betamoon.gui.api.util.GuiUtils;
import betamoon.io.ImageIo;
import betamoon.io.IoUtils;
import betamoon.luamodloader.LuaModLoader;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.luamodloader.LuaScriptRegistry;
import betamoon.luamodloader.ScriptMod;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.src.FontRenderer;
import net.minecraft.src.ModLoader;
import net.minecraft.src.Tessellator;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public final class GuiPanelScriptInfo extends GuiComponentBase {
    private static final String LABEL_DESCRIPTION = "Description";
    private static final String LABEL_ERRORS = "Errors";
    private static final String LABEL_DEPENDENCIES = "Dependencies";
    private static final String SUFFIX_DEPENDENCY_MISSING = "(Missing)";
    private static final int SECTION_PADDING = 64;
    private static final int CONTENT_PADDING = 6;
    private static final int LINE_SPACING = 16;
    private static final int DEPENDENCY_LINE_HEIGHT = 12;
    private static final int IMAGE_PADDING = 12;
    private static final int IMAGE_FIXED_SIZE = 72;
    private static final Map IMAGE_CACHE = new HashMap();
    private static final ImageTexture INVALID_IMAGE = new ImageTexture(-1, 0, 0);
    private int detailLeft;
    private int detailRight;
    private int detailTop;
    private int detailBottom;
    private int headerY;
    private float headerScale = 1.0F;
    private int screenWidth;
    private int screenHeight;
    private ScriptMod selected;
    private final List issueLinks = new ArrayList();
    private final GuiTextClickable inlineHelper = new GuiTextClickable();
    private final ScriptInfoContent content = new ScriptInfoContent();
    private final GuiScrollPanel scrollPanel = new GuiScrollPanel(content, EnumScrollMode.VERTICAL);
    private static final int WARNING_SIZE = 11;
    private static final int WARNING_GAP = 4;
    private int warningIconX = -1;
    private int warningIconY = -1;

    public GuiPanelScriptInfo() {
    }

    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);
        scrollPanel.setBounds(left, top, right, bottom);
    }

    public void layout(int screenWidth, int screenHeight) {
        scrollPanel.layout(screenWidth, screenHeight);
    }

    public void setSelected(ScriptMod selected) {
        this.selected = selected;
    }

    public void setHeaderY(int headerY) {
        this.headerY = headerY;
    }

    public void setHeaderScale(float headerScale) {
        this.headerScale = headerScale;
    }

    public void setDisplayMetrics(int screenWidth, int screenHeight, int displayWidth, int displayHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        scrollPanel.setDisplayMetrics(screenWidth, screenHeight, displayWidth, displayHeight);
    }

    public void draw(FontRenderer font, int mouseX, int mouseY, float partialTicks) {
        if (selected == null) {
            return;
        }
        this.detailLeft = left;
        this.detailRight = right;
        this.detailTop = top;
        this.detailBottom = bottom;
        int contentWidth = detailRight - detailLeft;
        // Title row: script name and version.
        String title = selected.getDisplayName() + "  v" + selected.getVersion();
        boolean nonReloadable = GuiNonReloadableIndicator.isVisible(selected.getSourceFileName());
        int titleX = detailLeft;
        warningIconX = -1;
        if (nonReloadable) {
            int iconY = headerY - 1;
            GuiNonReloadableIndicator.draw(ModLoader.getMinecraftInstance(), detailLeft, iconY, WARNING_SIZE);
            warningIconX = detailLeft;
            warningIconY = iconY;
            titleX += WARNING_SIZE + WARNING_GAP;
        }
        GuiUtils.drawScaledString(font, title, titleX, headerY, GuiColors.TEXT_PRIMARY, headerScale);

        String description = selected.getDescription();
        boolean hasDescription = description != null && !description.trim().isEmpty();
        String failure = selected.getFailureReason();
        List issues = LuaScriptErrors.getIssuesFor(selected.getDisplayName(), selected.getSourceFileName());
        List errorIssues = filterIssues(issues, false);
        List warningIssues = filterIssues(issues, true);
        boolean hasErrors = !errorIssues.isEmpty()
            || (selected.isFailed() && failure != null && !failure.trim().isEmpty());
        boolean hasWarnings = !warningIssues.isEmpty();
        List dependencies = selected.getDependencies();
        boolean hasDependencies = dependencies != null && !dependencies.isEmpty();
        int imageSize = calculateImageDrawSize();

        // Measure full content height to drive scrolling limits.
        int contentHeight = calculateContentHeight(font, contentWidth, hasDescription ? description : null,
            hasErrors ? failure : null, errorIssues, warningIssues, hasDependencies ? dependencies : null, imageSize);
        scrollPanel.setContentSize(Math.max(0, contentWidth), contentHeight);
        scrollPanel.draw(font, mouseX, mouseY, partialTicks);
        if (warningIconX >= 0) {
            GuiNonReloadableIndicator.drawTooltip(font, screenWidth, screenHeight,
                selected.getSourceFileName(), warningIconX, warningIconY, WARNING_SIZE, mouseX, mouseY);
        }
    }

    private final class ScriptInfoContent extends GuiComponentBase {
        public void draw(FontRenderer font, int mouseX, int mouseY, float partialTicks) {
            if (selected == null) {
                return;
            }
            issueLinks.clear();
            detailLeft = left;
            detailRight = right;
            detailTop = top;
            detailBottom = bottom;
            int contentWidth = detailRight - detailLeft;
            int y = detailTop;
            String description = selected.getDescription();
            boolean hasDescription = description != null && !description.trim().isEmpty();
            String failure = selected.getFailureReason();
            List issues = LuaScriptErrors.getIssuesFor(selected.getDisplayName(), selected.getSourceFileName());
            List errorIssues = filterIssues(issues, false);
            List warningIssues = filterIssues(issues, true);
            boolean hasErrors = !errorIssues.isEmpty()
                || (selected.isFailed() && failure != null && !failure.trim().isEmpty());
            boolean hasWarnings = !warningIssues.isEmpty();
            List dependencies = selected.getDependencies();
            boolean hasDependencies = dependencies != null && !dependencies.isEmpty();
            ImageTexture image = resolveImageTexture(selected.getImagePath());
            int imageSize = calculateImageDrawSize();

            if (imageSize > 0) {
                int imageX = detailLeft;
                if (image != null) {
                    drawImage(image, imageX, y, imageSize);
                } else {
                    drawImagePlaceholder(font, imageX, y, imageSize);
                }
                y += imageSize + IMAGE_PADDING;
            }

            float descriptionScale = 1.25F;
            GuiUtils.drawScaledStringUL(font, LABEL_DESCRIPTION, detailLeft, y, GuiColors.TEXT_PRIMARY,
                descriptionScale);
            y += (int) (10 * descriptionScale) + CONTENT_PADDING;
            if (hasDescription) {
                y += drawWrappedClipped(font, description, detailLeft, y, contentWidth, GuiColors.TEXT_PRIMARY,
                    detailBottom) + CONTENT_PADDING;
            }
            y += LINE_SPACING;

            if (hasDescription && (hasErrors || hasWarnings)) {
                GuiUtils.drawHorizontalLine(detailLeft, detailRight, y + 2, GuiUtils.COLOR_LIST_SEPERATOR);
                y += getIssueSectionPadding(hasErrors, hasWarnings);
            }

            if (hasErrors || hasWarnings) {
                float errorScale = 1.25F;
                int headerColor = hasErrors ? GuiColors.TEXT_ERROR : GuiColors.TEXT_WARNING;
                GuiUtils.drawScaledStringUL(font, LABEL_ERRORS, detailLeft, y, headerColor, errorScale);
                y += (int) (10 * errorScale) + CONTENT_PADDING;
                if (!errorIssues.isEmpty()) {
                    y += drawIssuesClipped(font, errorIssues, detailLeft, y, contentWidth, GuiColors.TEXT_ERROR,
                        detailBottom, mouseX, mouseY, partialTicks) + CONTENT_PADDING;
                    y += LINE_SPACING;
                } else if (hasErrors) {
                    y += drawWrappedClipped(font, failure, detailLeft, y, contentWidth, GuiColors.TEXT_ERROR,
                        detailBottom) + CONTENT_PADDING;
                    y += LINE_SPACING;
                }
                if (!warningIssues.isEmpty()) {
                    y += drawIssuesClipped(font, warningIssues, detailLeft, y, contentWidth, GuiColors.TEXT_WARNING,
                        detailBottom, mouseX, mouseY, partialTicks) + CONTENT_PADDING;
                    y += LINE_SPACING;
                }
            }

            if (hasDependencies) {
                float dependencyScale = 1.25F;
                GuiUtils.drawScaledStringUL(font, LABEL_DEPENDENCIES, detailLeft, y, GuiColors.TEXT_PRIMARY,
                    dependencyScale);
                y += (int) (10 * dependencyScale) + CONTENT_PADDING;
                drawDependenciesClipped(font, dependencies, selected.getMissingDependencies(), detailLeft, y,
                    contentWidth, detailBottom);
            }
        }

        public boolean mouseClicked(int mouseX, int mouseY, int button) {
            for (int i = 0; i < issueLinks.size(); i++) {
                GuiTextClickable link = (GuiTextClickable) issueLinks.get(i);
                if (link.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
            return false;
        }
    }

    private int calculateContentHeight(FontRenderer font, int contentWidth, String description, String failure,
        List errorIssues, List warningIssues, List dependencies, int imageSize) {
        int height = 0;
        if (imageSize > 0) {
            height += imageSize + IMAGE_PADDING;
        }
        float sectionScale = 1.25F;
        height += (int) (10 * sectionScale) + CONTENT_PADDING;
        if (description != null && !description.trim().isEmpty()) {
            height += font.func_27277_a(description, contentWidth) + CONTENT_PADDING;
        }
        height += LINE_SPACING;
        boolean hasErrorIssues = errorIssues != null && !errorIssues.isEmpty();
        boolean hasWarningIssues = warningIssues != null && !warningIssues.isEmpty();
        if (description != null && !description.trim().isEmpty()
            && ((failure != null && !failure.trim().isEmpty()) || hasErrorIssues || hasWarningIssues)) {
            boolean hasErrors = (failure != null && !failure.trim().isEmpty()) || hasErrorIssues;
            boolean hasWarnings = hasWarningIssues;
            height += getIssueSectionPadding(hasErrors, hasWarnings);
        }
        if (hasErrorIssues) {
            height += (int) (10 * sectionScale) + CONTENT_PADDING;
            height += measureIssuesHeight(font, errorIssues, contentWidth) + CONTENT_PADDING;
            height += LINE_SPACING;
        } else if (failure != null && !failure.trim().isEmpty()) {
            height += (int) (10 * sectionScale) + CONTENT_PADDING;
            height += font.func_27277_a(failure, contentWidth) + CONTENT_PADDING;
            height += LINE_SPACING;
        }
        if (hasWarningIssues) {
            height += (int) (10 * sectionScale) + CONTENT_PADDING;
            height += measureIssuesHeight(font, warningIssues, contentWidth) + CONTENT_PADDING;
            height += LINE_SPACING;
        }
        if (dependencies != null && !dependencies.isEmpty()) {
            height += (int) (10 * sectionScale) + CONTENT_PADDING;
            height += dependencies.size() * DEPENDENCY_LINE_HEIGHT + CONTENT_PADDING;
        }
        return height;
    }

    private List filterIssues(List issues, boolean warning) {
        List out = new ArrayList();
        if (issues == null) {
            return out;
        }
        for (int i = 0; i < issues.size(); i++) {
            LuaScriptErrors.ScriptIssue issue = (LuaScriptErrors.ScriptIssue) issues.get(i);
            if (issue.isWarning() == warning) {
                out.add(issue);
            }
        }
        return out;
    }

    private int getIssueSectionPadding(boolean hasErrors, boolean hasWarnings) {
        if (!hasErrors && hasWarnings) {
            return LINE_SPACING;
        }
        return SECTION_PADDING;
    }

    private int calculateImageDrawSize() {
        return IMAGE_FIXED_SIZE;
    }

    private static ImageTexture resolveImageTexture(String imagePath) {
        if (imagePath == null || imagePath.trim().isEmpty()) {
            return null;
        }
        String trimmed = imagePath.trim();
        ImageTexture cached = (ImageTexture) IMAGE_CACHE.get(trimmed);
        if (cached != null) {
            return cached == INVALID_IMAGE ? null : cached;
        }
        ImageTexture loaded = loadImageTexture(trimmed);
        IMAGE_CACHE.put(trimmed, loaded != null ? loaded : INVALID_IMAGE);
        return loaded;
    }

    private static ImageTexture loadImageTexture(String imagePath) {
        if (!imagePath.toLowerCase().endsWith(".png")) {
            return null;
        }
        File luaModsDir = LuaModLoader.getLuaModsDir();
        if (luaModsDir == null) {
            return null;
        }
        String trimmed = imagePath;
        while (trimmed.startsWith("/") || trimmed.startsWith("\\")) {
            trimmed = trimmed.substring(1);
        }
        File imageFile = new File(luaModsDir, trimmed);
        if (!imageFile.isFile()) {
            return null;
        }
        BufferedImage image;
        try {
            image = ImageIo.loadImage(imageFile);
        } catch (IOException e) {
            return null;
        }
        if (image == null) {
            return null;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0 || width != height) {
            return null;
        }
        int textureId = createTexture(image, width, height);
        if (textureId <= 0) {
            return null;
        }
        return new ImageTexture(textureId, width, height);
    }

    private static int createTexture(BufferedImage image, int width, int height) {
        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);
        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = pixels[y * width + x];
                buffer.put((byte) ((pixel >> 16) & 0xFF));
                buffer.put((byte) ((pixel >> 8) & 0xFF));
                buffer.put((byte) (pixel & 0xFF));
                buffer.put((byte) ((pixel >> 24) & 0xFF));
            }
        }
        buffer.flip();
        int textureId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA,
            GL11.GL_UNSIGNED_BYTE, buffer);
        return textureId;
    }

    private static void drawImage(ImageTexture image, int x, int y, int size) {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, image.textureId);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV((double) x, (double) (y + size), 0.0D, 0.0D, 1.0D);
        tessellator.addVertexWithUV((double) (x + size), (double) (y + size), 0.0D, 1.0D, 1.0D);
        tessellator.addVertexWithUV((double) (x + size), (double) y, 0.0D, 1.0D, 0.0D);
        tessellator.addVertexWithUV((double) x, (double) y, 0.0D, 0.0D, 0.0D);
        tessellator.draw();
    }

    private static void drawImagePlaceholder(FontRenderer font, int x, int y, int size) {
        GuiUtils.drawRect(x, y, x + size, y + size, GuiColors.BUTTON_BG);
        String mark = "?";
        int textWidth = font.getStringWidth(mark);
        int textHeight = 8;
        float targetSize = size * 0.6F;
        float scale = targetSize / (float) Math.max(textWidth, textHeight);
        if (scale < 1.0F) {
            scale = 1.0F;
        }
        GuiText.drawCenteredScaledString(font, mark, x, y, size, size, GuiColors.TEXT_PRIMARY, scale);
    }

    private static final class ImageTexture {
        private final int textureId;
        private final int width;
        private final int height;

        private ImageTexture(int textureId, int width, int height) {
            this.textureId = textureId;
            this.width = width;
            this.height = height;
        }
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

    private int measureIssuesHeight(FontRenderer font, List issues, int width) {
        if (issues == null || issues.isEmpty()) {
            return 0;
        }
        int height = 0;
        for (int i = 0; i < issues.size(); i++) {
            LuaScriptErrors.ScriptIssue issue = (LuaScriptErrors.ScriptIssue) issues.get(i);
            String entry = issue.getMessage();
            String linkText = buildLinkText(issue);
            height += measureEntryHeight(font, entry, linkText, width);
        }
        return height;
    }

    private int drawIssuesClipped(FontRenderer font, List issues, int x, int y, int width, int color, int bottom,
        int mouseX, int mouseY, float partialTicks) {
        if (issues == null || issues.isEmpty()) {
            return 0;
        }
        int startY = y;
        for (int i = 0; i < issues.size(); i++) {
            LuaScriptErrors.ScriptIssue issue = (LuaScriptErrors.ScriptIssue) issues.get(i);
            String linkText = buildLinkText(issue);
            File linkPath = resolveScriptFile(issue);
            int entryHeight = drawEntry(font, issue.getMessage(), linkText, linkPath, x, y, width, color, mouseX, mouseY,
                partialTicks);
            y += entryHeight;
        }
        return y - startY;
    }

    private int drawEntry(FontRenderer font, String fullText, String linkText, File linkPath, int left, int top,
        int maxWidth, int textColor, int mouseX, int mouseY, float partialTicks) {
        if (font == null || fullText == null) {
            return 0;
        }
        String[] lines = splitLines(fullText);
        if (lines.length == 0) {
            return 0;
        }
        int y = top;
        int usedHeight = 0;
        IGuiAction action = linkPath == null ? null : () -> IoUtils.openPath(linkPath);
        int firstHeight = inlineHelper.drawInline(font, lines[0], linkText, action, left, y, maxWidth,
            screenWidth, screenHeight, textColor, mouseX, mouseY, partialTicks, issueLinks);
        if (firstHeight <= 0) {
            firstHeight = GuiText.getLineHeight(font);
        }
        y += firstHeight;
        usedHeight += firstHeight;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.length() == 0) {
                int lineHeight = GuiText.getLineHeight(font);
                y += lineHeight;
                usedHeight += lineHeight;
                continue;
            }
            font.func_27278_a(line, left, y, maxWidth, textColor);
            int lineHeightUsed = font.func_27277_a(line, maxWidth);
            y += lineHeightUsed;
            usedHeight += lineHeightUsed;
        }
        return usedHeight;
    }

    private int measureEntryHeight(FontRenderer font, String fullText, String linkText, int maxWidth) {
        if (font == null || fullText == null) {
            return 0;
        }
        String[] lines = splitLines(fullText);
        if (lines.length == 0) {
            return 0;
        }
        int height = 0;
        int lineHeight = GuiText.getLineHeight(font);
        int firstLineHeight = measureInlineLineHeight(font, lines[0], linkText, maxWidth);
        height += firstLineHeight > 0 ? firstLineHeight : lineHeight;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.length() == 0) {
                height += lineHeight;
            } else {
                height += font.func_27277_a(line, maxWidth);
            }
        }
        return height;
    }

    private int measureInlineLineHeight(FontRenderer font, String fullText, String linkText, int maxWidth) {
        if (font == null || fullText == null) {
            return 0;
        }
        if (linkText == null || linkText.length() == 0) {
            return font.func_27277_a(fullText, maxWidth);
        }
        int linkIndex = fullText.indexOf(linkText);
        if (linkIndex < 0) {
            return font.func_27277_a(fullText, maxWidth);
        }
        String remainder = trimInlineRemainder(fullText.substring(linkIndex + linkText.length()));
        int linkHeight = GuiText.getLineHeight(font);
        int remainderWidth = Math.max(10, maxWidth - font.getStringWidth(linkText) - 4);
        int remainderHeight = remainder.length() > 0 ? font.func_27277_a(remainder, remainderWidth) : 0;
        return Math.max(linkHeight, remainderHeight);
    }

    private String trimInlineRemainder(String value) {
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

    private String[] splitLines(String value) {
        if (value == null) {
            return new String[0];
        }
        return value.split("\\n", -1);
    }

    private String buildLinkText(LuaScriptErrors.ScriptIssue issue) {
        if (issue == null || issue.getSourceFile() == null) {
            return null;
        }
        if (issue.getLine() > 0) {
            return issue.getSourceFile() + ":" + issue.getLine();
        }
        return issue.getSourceFile();
    }

    private File resolveScriptFile(LuaScriptErrors.ScriptIssue issue) {
        if (issue == null || issue.getSourceFile() == null) {
            return null;
        }
        File scriptsDir = LuaModLoader.getLuaModsDir();
        if (scriptsDir == null) {
            return null;
        }
        File file = new File(scriptsDir, issue.getSourceFile());
        if (file.isFile()) {
            return file;
        }
        return null;
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        return scrollPanel.mouseClicked(mouseX, mouseY, button);
    }

    public boolean mouseReleased(int mouseX, int mouseY, int button) {
        return scrollPanel.mouseReleased(mouseX, mouseY, button);
    }

    public boolean mouseDragged(int mouseX, int mouseY, boolean mouseDown) {
        return scrollPanel.mouseDragged(mouseX, mouseY, mouseDown);
    }

    public boolean mouseScrolled(int mouseX, int mouseY, int wheelDelta, boolean shiftDown) {
        return scrollPanel.mouseScrolled(mouseX, mouseY, wheelDelta, shiftDown);
    }

    public boolean keyTyped(char typedChar, int keyCode) {
        return scrollPanel.keyTyped(typedChar, keyCode);
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

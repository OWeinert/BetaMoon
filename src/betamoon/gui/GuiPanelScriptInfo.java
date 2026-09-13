package betamoon.gui;

import betamoon.gui.api.component.EnumScrollMode;
import betamoon.gui.api.component.GuiComponentBase;
import betamoon.gui.api.component.GuiNonReloadableIndicator;
import betamoon.gui.api.component.GuiScrollPanel;
import betamoon.gui.api.component.GuiTextClickable;
import betamoon.gui.api.util.GuiColors;
import betamoon.gui.api.util.GuiText;
import betamoon.gui.api.util.GuiUtils;
import betamoon.io.ImageIo;
import betamoon.luamodloader.LuaModLoader;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.luamodloader.LuaScriptErrors.ScriptIssue;
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
    private static final Map<String, ImageTexture> IMAGE_CACHE = new HashMap<>();
    private static final ImageTexture INVALID_IMAGE = new ImageTexture(-1, 0, 0);
    private int detailLeft;
    private int detailRight;
    private int detailTop;
    private int headerY;
    private float headerScale = 1.0F;
    private int screenWidth;
    private int screenHeight;
    private ScriptMod selected;
    private final List<GuiTextClickable> issueLinks = new ArrayList<>();
    private final GuiTextClickable inlineHelper = new GuiTextClickable();
    private final ScriptInfoContent content = new ScriptInfoContent();
    private final GuiScrollPanel scrollPanel = new GuiScrollPanel(content, EnumScrollMode.VERTICAL);
    private ScriptInfoLayout currentLayout;
    private static final int WARNING_SIZE = GuiNonReloadableIndicator.DEFAULT_SIZE;
    private static final int WARNING_GAP = 4;
    private final GuiNonReloadableIndicator warningIndicator = new GuiNonReloadableIndicator(null);

    public GuiPanelScriptInfo() {
        warningIndicator.setTooltipDeferred(true);
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);
        scrollPanel.setBounds(left, top, right, bottom);
    }

    @Override
    public void layout(int screenWidth, int screenHeight) {
        scrollPanel.layout(screenWidth, screenHeight);
    }

    public void setSelected(ScriptMod selected) {
        this.selected = selected;
        currentLayout = null;
        issueLinks.clear();
        warningIndicator.setSourceFileName(selected == null ? null : selected.getSourceFileName());
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
        warningIndicator.layout(screenWidth, screenHeight);
        scrollPanel.setDisplayMetrics(screenWidth, screenHeight, displayWidth, displayHeight);
    }

    @Override
    public void draw(FontRenderer font, int mouseX, int mouseY, float partialTicks) {
        if (selected == null) {
            return;
        }
        this.detailLeft = left;
        this.detailRight = right;
        this.detailTop = top;
        int contentWidth = detailRight - detailLeft;
        // Title row: script name and version.
        String title = selected.getDisplayName() + "  v" + selected.getVersion();
        boolean nonReloadable = warningIndicator.isVisible();
        int titleX = detailLeft;
        if (nonReloadable) {
            int iconY = headerY + ((int) (8 * headerScale) - WARNING_SIZE) / 2;
            warningIndicator.setMinecraft(ModLoader.getMinecraftInstance());
            warningIndicator.setBounds(detailLeft, iconY, detailLeft + WARNING_SIZE, iconY + WARNING_SIZE);
            warningIndicator.draw(font, mouseX, mouseY, partialTicks);
            titleX += WARNING_SIZE + WARNING_GAP;
        }
        GuiUtils.drawScaledString(font, title, titleX, headerY, GuiColors.TEXT_PRIMARY, headerScale);

        currentLayout = new ScriptInfoLayout(font, contentWidth);
        scrollPanel.setContentSize(Math.max(0, contentWidth), currentLayout.contentHeight);
        scrollPanel.draw(font, mouseX, mouseY, partialTicks);
        warningIndicator.drawTooltip(font, mouseX, mouseY);
    }

    private final class ScriptInfoContent extends GuiComponentBase {
        @Override
        public void draw(FontRenderer font, int mouseX, int mouseY, float partialTicks) {
            if (selected == null || currentLayout == null) {
                return;
            }
            issueLinks.clear();
            detailLeft = left;
            detailRight = right;
            detailTop = top;
            int contentWidth = detailRight - detailLeft;
            int y = detailTop;
            ScriptInfoLayout layout = currentLayout;

            if (layout.imageSize > 0) {
                int imageX = detailLeft;
                if (layout.image != null) {
                    drawImage(layout.image, imageX, y, layout.imageSize);
                } else {
                    drawImagePlaceholder(font, imageX, y, layout.imageSize);
                }
                y += layout.imageSize + IMAGE_PADDING;
            }

            float descriptionScale = 1.25F;
            GuiUtils.drawScaledStringUL(font, LABEL_DESCRIPTION, detailLeft, y, GuiColors.TEXT_PRIMARY,
                    descriptionScale);
            y += (int) (10 * descriptionScale) + CONTENT_PADDING;
            if (layout.hasDescription) {
                y += drawWrapped(font, layout.description, detailLeft, y, contentWidth, GuiColors.TEXT_PRIMARY)
                        + CONTENT_PADDING;
            }
            y += LINE_SPACING;

            if (layout.hasDescription && layout.hasIssues()) {
                GuiUtils.drawHorizontalLine(detailLeft, detailRight, y + 2, GuiUtils.COLOR_LIST_SEPARATOR);
                y += getIssueSectionPadding(layout.hasErrors, layout.hasWarnings);
            }

            if (layout.hasIssues()) {
                float errorScale = 1.25F;
                int headerColor = layout.hasErrors ? GuiColors.TEXT_ERROR : GuiColors.TEXT_WARNING;
                GuiUtils.drawScaledStringUL(font, LABEL_ERRORS, detailLeft, y, headerColor, errorScale);
                y += (int) (10 * errorScale) + CONTENT_PADDING;
                if (!layout.errorIssues.getEntries().isEmpty()) {
                    y += drawIssues(font, layout.errorIssues, detailLeft, y, contentWidth, GuiColors.TEXT_ERROR, mouseX,
                            mouseY, partialTicks) + CONTENT_PADDING;
                    y += LINE_SPACING;
                } else if (layout.hasErrors) {
                    y += drawWrapped(font, layout.failure, detailLeft, y, contentWidth, GuiColors.TEXT_ERROR)
                            + CONTENT_PADDING;
                    y += LINE_SPACING;
                }
                if (!layout.warningIssues.getEntries().isEmpty()) {
                    y += drawIssues(font, layout.warningIssues, detailLeft, y, contentWidth, GuiColors.TEXT_WARNING,
                            mouseX, mouseY, partialTicks) + CONTENT_PADDING;
                    y += LINE_SPACING;
                }
            }

            if (layout.hasDependencies) {
                float dependencyScale = 1.25F;
                GuiUtils.drawScaledStringUL(font, LABEL_DEPENDENCIES, detailLeft, y, GuiColors.TEXT_PRIMARY,
                        dependencyScale);
                y += (int) (10 * dependencyScale) + CONTENT_PADDING;
                drawDependencies(font, layout.dependencies, selected.getMissingDependencies(), detailLeft, y,
                        contentWidth);
            }
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int button) {
            for (int i = 0; i < issueLinks.size(); i++) {
                GuiTextClickable link = issueLinks.get(i);
                if (link.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
            return false;
        }
    }

    private final class ScriptInfoLayout {
        private final String description;
        private final String failure;
        private final List<String> dependencies;
        private final GuiIssueLayout errorIssues;
        private final GuiIssueLayout warningIssues;
        private final ImageTexture image;
        private final int imageSize;
        private final boolean hasDescription;
        private final boolean hasErrors;
        private final boolean hasWarnings;
        private final boolean hasDependencies;
        private final int contentHeight;

        private ScriptInfoLayout(FontRenderer font, int contentWidth) {
            description = selected.getDescription();
            failure = selected.getFailureReason();
            dependencies = selected.getDependencies();
            List<ScriptIssue> issues = LuaScriptErrors.getIssuesFor(selected.getDisplayName(),
                    selected.getSourceFileName());
            errorIssues = GuiIssueLayout.prepare(font, filterIssues(issues, false), contentWidth, 0);
            warningIssues = GuiIssueLayout.prepare(font, filterIssues(issues, true), contentWidth, 0);
            image = resolveImageTexture(selected.getImagePath());
            imageSize = calculateImageDrawSize();
            hasDescription = hasText(description);
            hasErrors = !errorIssues.getEntries().isEmpty() || (selected.isFailed() && hasText(failure));
            hasWarnings = !warningIssues.getEntries().isEmpty();
            hasDependencies = dependencies != null && !dependencies.isEmpty();
            contentHeight = calculateHeight(font, contentWidth);
        }

        private boolean hasIssues() {
            return hasErrors || hasWarnings;
        }

        private int calculateHeight(FontRenderer font, int contentWidth) {
            int height = imageSize > 0 ? imageSize + IMAGE_PADDING : 0;
            int headerHeight = (int) (10 * 1.25F) + CONTENT_PADDING;
            height += headerHeight;
            if (hasDescription) {
                height += measureWrapped(font, description, contentWidth) + CONTENT_PADDING;
            }
            height += LINE_SPACING;

            if (hasDescription && hasIssues()) {
                height += getIssueSectionPadding(hasErrors, hasWarnings);
            }
            if (hasIssues()) {
                height += headerHeight;
                if (!errorIssues.getEntries().isEmpty()) {
                    height += errorIssues.getHeight() + CONTENT_PADDING + LINE_SPACING;
                } else if (hasErrors) {
                    height += measureWrapped(font, failure, contentWidth) + CONTENT_PADDING + LINE_SPACING;
                }
                if (!warningIssues.getEntries().isEmpty()) {
                    height += warningIssues.getHeight() + CONTENT_PADDING + LINE_SPACING;
                }
            }
            if (hasDependencies) {
                height += headerHeight + dependencies.size() * DEPENDENCY_LINE_HEIGHT + CONTENT_PADDING;
            }
            return height;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private List<ScriptIssue> filterIssues(List<ScriptIssue> issues, boolean warning) {
        List<ScriptIssue> out = new ArrayList<>();
        if (issues == null) {
            return out;
        }
        for (int i = 0; i < issues.size(); i++) {
            ScriptIssue issue = issues.get(i);
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
        ImageTexture cached = IMAGE_CACHE.get(trimmed);
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
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE,
                buffer);
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

    private int drawWrapped(FontRenderer font, String text, int x, int y, int width, int color) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        font.func_27278_a(text, x, y, width, color);
        return measureWrapped(font, text, width);
    }

    private int measureWrapped(FontRenderer font, String text, int width) {
        if (font == null || text == null || text.isEmpty()) {
            return 0;
        }
        return font.func_27277_a(text, width);
    }

    private int drawIssues(FontRenderer font, GuiIssueLayout layout, int x, int y, int width, int color, int mouseX,
            int mouseY, float partialTicks) {
        if (layout == null) {
            return 0;
        }
        int entryY = y;
        List<GuiIssueLayout.Entry> entries = layout.getEntries();
        for (int i = 0; i < entries.size(); i++) {
            GuiIssueLayout.Entry entry = entries.get(i);
            entry.draw(font, inlineHelper, issueLinks, x, entryY, width, screenWidth, screenHeight, color, mouseX,
                    mouseY, partialTicks);
            entryY += entry.getHeight();
        }
        return layout.getHeight();
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        return scrollPanel.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int button) {
        return scrollPanel.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(int mouseX, int mouseY, boolean mouseDown) {
        return scrollPanel.mouseDragged(mouseX, mouseY, mouseDown);
    }

    @Override
    public boolean mouseScrolled(int mouseX, int mouseY, int wheelDelta, boolean shiftDown) {
        return scrollPanel.mouseScrolled(mouseX, mouseY, wheelDelta, shiftDown);
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        return scrollPanel.keyTyped(typedChar, keyCode);
    }

    private int drawDependencies(FontRenderer font, List<String> dependencies, List<String> missingDeps, int x, int y,
            int width) {
        if (dependencies == null || dependencies.isEmpty()) {
            return 0;
        }
        int startY = y;
        for (int i = 0; i < dependencies.size(); i++) {
            String dep = dependencies.get(i);
            if (dep == null) {
                continue;
            }
            String name = dep;
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

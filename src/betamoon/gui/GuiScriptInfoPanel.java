package betamoon.gui;

import betamoon.gui.api.component.EnumScrollMode;
import betamoon.gui.api.util.GuiColors;
import betamoon.gui.api.component.GuiScrollPanel;
import betamoon.gui.api.util.GuiText;
import betamoon.gui.api.util.GuiUtils;
import betamoon.scriptloader.LuaModLoader;
import betamoon.scriptloader.LuaScriptRegistry;
import betamoon.scriptloader.ScriptMod;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.src.FontRenderer;
import net.minecraft.src.Tessellator;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public final class GuiScriptInfoPanel extends GuiScrollPanel {
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
    private int displayWidth;
    private int displayHeight;
    private ScriptMod selected;

    public GuiScriptInfoPanel() {
        super(EnumScrollMode.VERTICAL);
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
        this.displayWidth = displayWidth;
        this.displayHeight = displayHeight;
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
        GuiUtils.drawScaledString(font, title, detailLeft, headerY, GuiColors.TEXT_PRIMARY, headerScale);

        // Resolve content flags and starting Y based on the current scroll offset.
        int y = detailTop - getScrollOffsetY();
        String description = selected.getDescription();
        boolean hasDescription = description != null && !description.trim().isEmpty();
        String failure = selected.getFailureReason();
        boolean hasErrors = selected.isFailed() && failure != null && !failure.trim().isEmpty();
        List dependencies = selected.getDependencies();
        boolean hasDependencies = dependencies != null && !dependencies.isEmpty();
        ImageTexture image = resolveImageTexture(selected.getImagePath());
        int imageSize = calculateImageDrawSize();

        // Measure full content height to drive scrolling limits.
        int contentHeight = calculateContentHeight(font, contentWidth, hasDescription ? description : null,
            hasErrors ? failure : null, hasDependencies ? dependencies : null, imageSize);
        updateScrollContentSize(contentWidth, contentHeight);
        y = detailTop - getScrollOffsetY();
        int scissorTop = detailTop - 2;
        int scissorBottom = detailBottom + 2;
        // Clip body text so it doesn't overlap the title row.
        GuiUtils.beginScissor(detailLeft, scissorTop, detailRight, scissorBottom, screenWidth, screenHeight, displayWidth, displayHeight);

        if (imageSize > 0) {
            int imageX = detailLeft;
            if (image != null) {
                drawImage(image, imageX, y, imageSize);
            } else {
                drawImagePlaceholder(font, imageX, y, imageSize);
            }
            y += imageSize + IMAGE_PADDING;
        }

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

        drawScrollbar(contentHeight);
        GuiUtils.endScissor();
    }

    private int calculateContentHeight(FontRenderer font, int contentWidth, String description, String failure, List dependencies,
        int imageSize) {
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
            image = ImageIO.read(imageFile);
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

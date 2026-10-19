package betamoon.gui;

import betamoon.gui.framework.GuiRenderer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.src.FontRenderer;

/** Keep canonical keys readable even when a menu label must be shortened. */
final class GenerationTooltips {
    private GenerationTooltips() {
    }

    static void draw(FontRenderer font, int width, int height, int x, int y, String... paragraphs) {
        List<String> lines = new ArrayList<String>();
        int maximumWidth = Math.max(40, Math.min(280, width - 24));
        int maximumLines = Math.max(1, (height - 24) / 10);
        for (String paragraph : paragraphs) {
            if (paragraph == null || paragraph.isEmpty()) {
                continue;
            }
            String remaining = paragraph;
            while (!remaining.isEmpty() && lines.size() < maximumLines) {
                int end = remaining.length();
                while (end > 1 && font.getStringWidth(remaining.substring(0, end)) > maximumWidth) {
                    end--;
                }
                if (end < remaining.length()) {
                    int space = remaining.lastIndexOf(' ', end);
                    if (space > end / 2) {
                        end = space;
                    }
                }
                lines.add(remaining.substring(0, end));
                remaining = remaining.substring(end).trim();
            }
        }
        GuiRenderer.drawTooltipLines(font, width, height, lines, x, y);
    }
}

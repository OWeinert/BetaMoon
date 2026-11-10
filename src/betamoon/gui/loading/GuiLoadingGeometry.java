package betamoon.gui.loading;

import betamoon.gui.framework.GuiGeometry.Rect;

/**
 * Constant-size background geometry; resizing never prepares or loads an image.
 */
public final class GuiLoadingGeometry {
    public final Rect bounds;
    public final float minU;
    public final float minV;
    public final float maxU;
    public final float maxV;

    private GuiLoadingGeometry(int width, int height, double minU, double minV, double maxU, double maxV) {
        bounds = new Rect(0, 0, Math.max(0, width), Math.max(0, height));
        this.minU = (float) minU;
        this.minV = (float) minV;
        this.maxU = (float) maxU;
        this.maxV = (float) maxV;
    }

    public static GuiLoadingGeometry background(GuiLoadingAppearance.Mode mode, int width, int height, int imageWidth,
            int imageHeight, int tileSize) {
        if (imageWidth <= 0 || imageHeight <= 0 || tileSize <= 0 || mode == null) {
            throw new IllegalArgumentException("Loading background requires positive image/tile sizes and a mode");
        }
        if (width <= 0 || height <= 0) {
            return new GuiLoadingGeometry(0, 0, 0, 0, 0, 0);
        }
        if (mode == GuiLoadingAppearance.Mode.TILE) {
            double tileHeight = (double) tileSize * imageHeight / imageWidth;
            return new GuiLoadingGeometry(width, height, 0, 0, (double) width / tileSize, height / tileHeight);
        }
        if (mode == GuiLoadingAppearance.Mode.COVER) {
            double scale = Math.max((double) width / imageWidth, (double) height / imageHeight);
            double visibleU = width / (imageWidth * scale);
            double visibleV = height / (imageHeight * scale);
            return new GuiLoadingGeometry(width, height, (1 - visibleU) / 2, (1 - visibleV) / 2, (1 + visibleU) / 2,
                    (1 + visibleV) / 2);
        }
        return new GuiLoadingGeometry(width, height, 0, 0, 1, 1);
    }
}

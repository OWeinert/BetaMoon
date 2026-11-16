package betamoon.gui.hud;

import betamoon.gui.framework.GuiGeometry.Rect;
import betamoon.gui.graphics.GraphicsTransform;

/**
 * Bounded geometry over a native body; transforms never compose across owners.
 */
public final class GuiHudPartTransform {
    public final boolean anchored;
    public final double x;
    public final double y;
    public final double anchorX;
    public final double anchorY;
    public final double scale;

    public GuiHudPartTransform(boolean anchored, double x, double y, double anchorX, double anchorY, double scale) {
        if (!range(x, -16384, 16384) || !range(y, -16384, 16384) || !range(anchorX, 0, 1) || !range(anchorY, 0, 1)
                || !range(scale, 0.25, 4)) {
            throw new IllegalArgumentException(
                    "HUD modifier requires bounded finite offsets/anchors and scale 0.25..4");
        }
        this.anchored = anchored;
        this.x = x;
        this.y = y;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.scale = scale;
    }

    public GraphicsTransform resolve(Rect body, int width, int height) {
        double left = anchored ? Math.round((width - body.getWidth() * scale) * anchorX + x) : body.getLeft() + x;
        double top = anchored ? Math.round((height - body.getHeight() * scale) * anchorY + y) : body.getTop() + y;
        return new GraphicsTransform(left - body.getLeft() * scale, top - body.getTop() * scale, scale, scale);
    }

    public static Rect map(Rect bounds, GraphicsTransform transform) {
        return new Rect((int) Math.floor(transform.mapX(bounds.getLeft())),
                (int) Math.floor(transform.mapY(bounds.getTop())), (int) Math.ceil(transform.mapX(bounds.getRight())),
                (int) Math.ceil(transform.mapY(bounds.getBottom())));
    }

    private static boolean range(double value, double minimum, double maximum) {
        return Double.isFinite(value) && value >= minimum && value <= maximum;
    }
}

package betamoon.gui.loading;

import betamoon.gui.loading.GuiLoadingAppearance.Mode;

final class GuiLoadingGeometryTest {
    static void run() {
        check(Mode.STRETCH, 800, 600, 256, 128, 32, 0, 0, 1, 1);
        check(Mode.COVER, 800, 600, 256, 128, 32, 1.0 / 6, 0, 5.0 / 6, 1);
        check(Mode.COVER, 800, 200, 256, 128, 32, 0, 0.25, 1, 0.75);
        check(Mode.COVER, 128, 256, 128, 256, 32, 0, 0, 1, 1);
        check(Mode.TILE, 800, 600, 256, 128, 32, 0, 0, 25, 37.5);
        check(Mode.TILE, 800, 600, 32, 32, 32, 0, 0, 25, 18.75);
        check(Mode.TILE, Integer.MAX_VALUE, Integer.MAX_VALUE, 65536, 1, 8, 0, 0, Integer.MAX_VALUE / 8.0,
                Integer.MAX_VALUE / (8.0 / 65536));
        GuiLoadingGeometry empty = GuiLoadingGeometry.background(Mode.COVER, 0, 500, 128, 256, 32);
        require(empty.bounds.isEmpty() && empty.maxU == 0 && empty.maxV == 0,
                "Zero viewport retained drawable or nonfinite geometry");
        try {
            GuiLoadingGeometry.background(Mode.TILE, 800, 600, 0, 32, 32);
            throw new AssertionError("Invalid image dimensions were accepted");
        } catch (IllegalArgumentException expected) {
            // Invalid preparation metadata must never reach division or rendering.
        }
    }

    private static void check(Mode mode, int width, int height, int imageWidth, int imageHeight, int tileSize,
            double minU, double minV, double maxU, double maxV) {
        GuiLoadingGeometry geometry = GuiLoadingGeometry.background(mode, width, height, imageWidth, imageHeight,
                tileSize);
        require(geometry.bounds.getWidth() == width && geometry.bounds.getHeight() == height,
                "Loading background changed viewport coverage");
        close(geometry.minU, minU);
        close(geometry.minV, minV);
        close(geometry.maxU, maxU);
        close(geometry.maxV, maxV);
    }

    private static void close(float actual, double expected) {
        require(Float.isFinite(actual) && Math.abs(actual - expected) <= Math.max(0.00001, Math.abs(expected) * 1E-7),
                "Unexpected loading UV: " + actual + " instead of " + expected);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

package betamoon.assets;

/** Headless description of a whole native image or one cell of a vanilla atlas. */
public final class NativeTextureSource {
    private final AssetPath path;
    private final String atlas;
    private final int index;

    private NativeTextureSource(AssetPath path, String atlas, int index) {
        this.path = path;
        this.atlas = atlas;
        this.index = index;
    }

    static NativeTextureSource image(AssetPath path) {
        return new NativeTextureSource(path, null, -1);
    }

    static NativeTextureSource cell(String atlas, int index) {
        if ((!"terrain".equals(atlas) && !"items".equals(atlas)) || index < 0 || index > 255) {
            throw new IllegalArgumentException("Invalid native atlas cell: " + atlas + "/" + index);
        }
        return new NativeTextureSource(AssetPath.parse("terrain".equals(atlas) ? "terrain.png" : "gui/items.png"),
                atlas, index);
    }

    public AssetPath getPath() {
        return path;
    }

    public String getResource() {
        return "/" + path;
    }

    public boolean isCell() {
        return atlas != null;
    }

    public String getAtlas() {
        return atlas;
    }

    public int getIndex() {
        return index;
    }

    public int getColumn() {
        return index % 16;
    }

    public int getRow() {
        return index / 16;
    }

    public void validateDimensions(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Native image dimensions must be positive");
        }
        if (isCell() && (width != height || width % 16 != 0)) {
            throw new IllegalArgumentException("Native " + atlas + " atlas must contain 16 columns and 16 rows"
                    + " of square cells; got " + width + " x " + height);
        }
    }

    public double mapU(double local, int width) {
        return isCell() ? map(local, getColumn(), width) : local;
    }

    public double mapV(double local, int height) {
        return isCell() ? map(local, getRow(), height) : local;
    }

    private double map(double local, int cell, int pixels) {
        if (!Double.isFinite(local) || local < -0.000001 || local > 1.000001) {
            throw new IllegalArgumentException("Native atlas cell UVs must remain within 0..1; use a whole-image"
                    + " texture for atlas-wide UVs");
        }
        double inset = 0.01 / pixels;
        return cell / 16.0 + inset + Math.max(0, Math.min(1, local)) * (1.0 / 16 - 2 * inset);
    }
}

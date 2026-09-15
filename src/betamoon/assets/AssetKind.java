package betamoon.assets;

/**
 * Asset categories have independent key spaces and stable texture-pack
 * directories.
 */
public enum AssetKind {
    TEXTURE("textures"), MODEL("models"), ANIMATION("animations"), MATERIAL("materials"), SOUND("sounds");

    private final String directory;

    AssetKind(String directory) {
        this.directory = directory;
    }

    public String getDirectory() {
        return directory;
    }
}

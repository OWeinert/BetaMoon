package betamoon.resources;

public enum EnumTexAtlas {
    BLOCKS(0, "/terrain.png"),
    ITEMS(1, "/gui/items.png");

    private final int atlasId;
    private final String atlasPath;

    EnumTexAtlas(int atlasId, String atlasPath) {
        this.atlasId = atlasId;
        this.atlasPath = atlasPath;
    }

    public int getAtlasId() {
        return atlasId;
    }

    public String getAtlasPath() {
        return atlasPath;
    }
}

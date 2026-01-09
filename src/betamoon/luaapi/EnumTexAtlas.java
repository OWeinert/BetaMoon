package betamoon.luaapi;

public enum EnumTexAtlas {
    BLOCKS(0, "/terrain.png"),
    ITEMS(1, "/gui/items.png");

    private final int atlasId;
    private final String atlasPath;

    EnumTexAtlas(int atlasId, String atlasPath) {
        this.atlasId = atlasId;
        this.atlasPath = atlasPath;
    }

    int getAtlasId() {
        return atlasId;
    }

    String getAtlasPath() {
        return atlasPath;
    }
}

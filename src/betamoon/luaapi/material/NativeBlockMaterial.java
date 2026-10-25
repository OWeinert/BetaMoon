package betamoon.luaapi.material;

import net.minecraft.src.Material;

/** Native material with all behavior fixed at construction. */
final class NativeBlockMaterial extends Material {
    private final BlockMaterialDefinition definition;

    NativeBlockMaterial(BlockMaterialDefinition definition) {
        super(definition.color);
        this.definition = definition;
    }

    @Override
    public boolean getIsLiquid() {
        return "liquid".equals(definition.kind);
    }

    @Override
    public boolean isSolid() {
        return "solid".equals(definition.kind);
    }

    @Override
    public boolean getIsSolid() {
        return isSolid();
    }

    @Override
    public boolean getCanBlockGrass() {
        return definition.blocksGrass;
    }

    @Override
    public boolean getBurning() {
        return definition.burning;
    }

    @Override
    public boolean getIsGroundCover() {
        return definition.groundCover;
    }

    @Override
    public boolean getIsTranslucent() {
        return isSolid() && !definition.translucent;
    }

    @Override
    public boolean getIsHarvestable() {
        return definition.harvestable;
    }

    @Override
    public int getMaterialMobility() {
        return definition.mobility;
    }
}

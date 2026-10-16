package betamoon.multiblock;

import net.minecraft.src.NBTTagCompound;

/**
 * Persistent restoration record. Losing ownership never grants permission to
 * restore.
 */
final class MultiblockOwnedBlock {
    final MultiblockPosition position;
    final int originalId;
    final int originalMetadata;
    final int placedId;
    final int placedMetadata;
    boolean written;
    boolean restoring;

    MultiblockOwnedBlock(MultiblockPosition position, int originalId, int originalMetadata, int placedId,
            int placedMetadata) {
        this.position = position;
        this.originalId = originalId;
        this.originalMetadata = originalMetadata;
        this.placedId = placedId;
        this.placedMetadata = placedMetadata;
    }

    MultiblockOwnedBlock(NBTTagCompound tag) {
        this(new MultiblockPosition(tag.getInteger("X"), tag.getInteger("Y"), tag.getInteger("Z")),
                tag.getInteger("OriginalId"), tag.getInteger("OriginalMetadata"), tag.getInteger("PlacedId"),
                tag.getInteger("PlacedMetadata"));
        written = tag.getBoolean("Written");
        restoring = tag.getBoolean("Restoring");
    }

    boolean valid() {
        return MultiblockMatchJob.inBounds(position.x, position.y, position.z) && originalId >= 0 && originalId < 256
                && placedId > 0 && placedId < 256 && originalMetadata >= 0 && originalMetadata < 16
                && placedMetadata >= 0 && placedMetadata < 16;
    }

    NBTTagCompound save() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("X", position.x);
        tag.setInteger("Y", position.y);
        tag.setInteger("Z", position.z);
        tag.setInteger("OriginalId", originalId);
        tag.setInteger("OriginalMetadata", originalMetadata);
        tag.setInteger("PlacedId", placedId);
        tag.setInteger("PlacedMetadata", placedMetadata);
        tag.setBoolean("Written", written);
        tag.setBoolean("Restoring", restoring);
        return tag;
    }
}

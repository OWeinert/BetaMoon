package betamoon.teleport;

import betamoon.dimension.DimensionIdentity;
import betamoon.dimension.DimensionKey;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.NBTTagCompound;

/**
 * A standing position (Y is the feet), independent of native player eye-height
 * offsets.
 */
public final class TeleportLocation {
    public final DimensionKey dimension;
    public final double x;
    public final double y;
    public final double z;
    public final float yaw;
    public final float pitch;

    public TeleportLocation(DimensionKey dimension, double x, double y, double z, float yaw, float pitch) {
        if (dimension == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x) >= 30000000 || Math.abs(z) >= 30000000 || y < -64 || y > 4096 || !Float.isFinite(yaw)
                || !Float.isFinite(pitch) || pitch < -90 || pitch > 90) {
            throw new IllegalArgumentException("out_of_bounds");
        }
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = (yaw % 360 + 360) % 360;
        this.pitch = pitch;
    }

    public static TeleportLocation capture(EntityPlayer player) {
        return new TeleportLocation(DimensionIdentity.key(player.worldObj), player.posX, player.boundingBox.minY,
                player.posZ, player.rotationYaw, player.rotationPitch);
    }

    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Dimension", dimension.toString());
        tag.setDouble("X", x);
        tag.setDouble("Y", y);
        tag.setDouble("Z", z);
        tag.setFloat("Yaw", yaw);
        tag.setFloat("Pitch", pitch);
        return tag;
    }

    public static TeleportLocation read(NBTTagCompound tag) throws IOException {
        try {
            return new TeleportLocation(DimensionKey.parse(tag.getString("Dimension")), tag.getDouble("X"),
                    tag.getDouble("Y"), tag.getDouble("Z"), tag.getFloat("Yaw"), tag.getFloat("Pitch"));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid saved teleport location", exception);
        }
    }

    public Map<String, Object> describe() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("dimensionKey", dimension.toString());
        result.put("x", x);
        result.put("y", y);
        result.put("z", z);
        result.put("yaw", yaw);
        result.put("pitch", pitch);
        return result;
    }

    public static float normalizeYaw(double yaw) {
        if (!Double.isFinite(yaw)) {
            throw new IllegalArgumentException("Yaw must be finite");
        }
        return (float) ((yaw % 360 + 360) % 360);
    }
}

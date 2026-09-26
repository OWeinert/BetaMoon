package betamoon.worldgen.structure;

import betamoon.worldgen.BlockPosition;
import java.util.Locale;
import java.util.Random;

/** One horizontal mirror followed by a quarter-turn around the template origin. */
public final class StructureTransform {
    public enum Rotation {
        NONE,
        CLOCKWISE_90,
        CLOCKWISE_180,
        COUNTERCLOCKWISE_90
    }

    public enum Mirror {
        NONE,
        LEFT_RIGHT,
        FRONT_BACK
    }

    public final Rotation rotation;
    public final Mirror mirror;

    public StructureTransform(Rotation rotation, Mirror mirror) {
        this.rotation = rotation;
        this.mirror = mirror;
    }

    public BlockPosition apply(int x, int y, int z) {
        int transformedX = mirror == Mirror.FRONT_BACK ? -x : x;
        int transformedZ = mirror == Mirror.LEFT_RIGHT ? -z : z;
        switch (rotation) {
            case CLOCKWISE_90:
                return new BlockPosition(-transformedZ, y, transformedX);
            case CLOCKWISE_180:
                return new BlockPosition(-transformedX, y, -transformedZ);
            case COUNTERCLOCKWISE_90:
                return new BlockPosition(transformedZ, y, -transformedX);
            default:
                return new BlockPosition(transformedX, y, transformedZ);
        }
    }

    public boolean isIdentity() {
        return rotation == Rotation.NONE && mirror == Mirror.NONE;
    }

    public static StructureTransform select(String requestedRotation, String requestedMirror,
            String defaultRotation, String defaultMirror, Random random) {
        return new StructureTransform(rotation(requestedRotation == null ? defaultRotation : requestedRotation,
                random), mirror(requestedMirror == null ? defaultMirror : requestedMirror, random));
    }

    public static Rotation rotation(String value, Random random) {
        String name = value == null ? "none" : value.trim().toLowerCase(Locale.ROOT);
        if (name.equals("random") || name.equals("random_horizontal")) {
            return Rotation.values()[random.nextInt(Rotation.values().length)];
        }
        if (name.equals("none") || name.equals("0")) {
            return Rotation.NONE;
        }
        if (name.equals("clockwise_90") || name.equals("90")) {
            return Rotation.CLOCKWISE_90;
        }
        if (name.equals("clockwise_180") || name.equals("180")) {
            return Rotation.CLOCKWISE_180;
        }
        if (name.equals("counterclockwise_90") || name.equals("270")) {
            return Rotation.COUNTERCLOCKWISE_90;
        }
        throw new IllegalArgumentException("Unknown structure rotation: " + value);
    }

    public static Mirror mirror(String value, Random random) {
        String name = value == null ? "none" : value.trim().toLowerCase(Locale.ROOT);
        if (name.equals("random")) {
            return Mirror.values()[random.nextInt(Mirror.values().length)];
        }
        if (name.equals("none")) {
            return Mirror.NONE;
        }
        if (name.equals("left_right")) {
            return Mirror.LEFT_RIGHT;
        }
        if (name.equals("front_back")) {
            return Mirror.FRONT_BACK;
        }
        throw new IllegalArgumentException("Unknown structure mirror: " + value);
    }
}

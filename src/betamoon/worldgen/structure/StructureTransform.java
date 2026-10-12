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

    public enum Direction {
        NORTH(0, -1),
        EAST(1, 0),
        SOUTH(0, 1),
        WEST(-1, 0);

        public final int x;
        public final int z;

        Direction(int x, int z) {
            this.x = x;
            this.z = z;
        }

        public Direction opposite() {
            return values()[(ordinal() + 2) % values().length];
        }

        public static Direction parse(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("Unknown connector facing: " + value);
            }
        }
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

    public Direction apply(Direction direction) {
        BlockPosition vector = apply(direction.x, 0, direction.z);
        for (Direction candidate : Direction.values()) {
            if (candidate.x == vector.x && candidate.z == vector.z) {
                return candidate;
            }
        }
        throw new IllegalStateException("Structure transform produced a non-cardinal connector direction");
    }

    public float applyYaw(float yaw) {
        double radians = Math.toRadians(yaw);
        double x = -Math.sin(radians);
        double z = Math.cos(radians);
        if (mirror == Mirror.FRONT_BACK) {
            x = -x;
        } else if (mirror == Mirror.LEFT_RIGHT) {
            z = -z;
        }
        double transformedX;
        double transformedZ;
        switch (rotation) {
            case CLOCKWISE_90:
                transformedX = -z;
                transformedZ = x;
                break;
            case CLOCKWISE_180:
                transformedX = -x;
                transformedZ = -z;
                break;
            case COUNTERCLOCKWISE_90:
                transformedX = z;
                transformedZ = -x;
                break;
            default:
                transformedX = x;
                transformedZ = z;
        }
        return (float) Math.toDegrees(Math.atan2(-transformedX, transformedZ));
    }

    public boolean isIdentity() {
        return rotation == Rotation.NONE && mirror == Mirror.NONE;
    }

    /** Returns the transform that applies {@code inner} first and this transform second. */
    public StructureTransform compose(StructureTransform inner) {
        BlockPosition innerX = inner.apply(1, 0, 0);
        BlockPosition innerZ = inner.apply(0, 0, 1);
        BlockPosition composedX = apply(innerX.x, 0, innerX.z);
        BlockPosition composedZ = apply(innerZ.x, 0, innerZ.z);
        for (Rotation candidateRotation : Rotation.values()) {
            for (Mirror candidateMirror : Mirror.values()) {
                StructureTransform candidate = new StructureTransform(candidateRotation, candidateMirror);
                BlockPosition candidateX = candidate.apply(1, 0, 0);
                BlockPosition candidateZ = candidate.apply(0, 0, 1);
                if (candidateX.x == composedX.x && candidateX.z == composedX.z
                        && candidateZ.x == composedZ.x && candidateZ.z == composedZ.z) {
                    return candidate;
                }
            }
        }
        throw new IllegalStateException("Horizontal structure transforms did not compose");
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

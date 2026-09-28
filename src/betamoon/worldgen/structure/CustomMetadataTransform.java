package betamoon.worldgen.structure;

import java.util.Arrays;

/** Validated metadata lookup tables for one custom directional block. */
public final class CustomMetadataTransform {
    private final int[] clockwise;
    private final int[] leftRight;
    private final int[] frontBack;

    public CustomMetadataTransform(int[] clockwise, int[] leftRight, int[] frontBack) {
        this.clockwise = clockwise.clone();
        this.leftRight = leftRight.clone();
        this.frontBack = frontBack.clone();
    }

    public int transform(int metadata, StructureTransform transform) {
        int result = metadata;
        if (transform.mirror == StructureTransform.Mirror.LEFT_RIGHT) {
            result = leftRight[result];
        } else if (transform.mirror == StructureTransform.Mirror.FRONT_BACK) {
            result = frontBack[result];
        }
        int turns = transform.rotation == StructureTransform.Rotation.CLOCKWISE_90 ? 1
                : transform.rotation == StructureTransform.Rotation.CLOCKWISE_180 ? 2
                        : transform.rotation == StructureTransform.Rotation.COUNTERCLOCKWISE_90 ? 3 : 0;
        while (turns-- > 0) {
            result = clockwise[result];
        }
        return result;
    }

    String generationSignature() {
        return Arrays.toString(clockwise) + Arrays.toString(leftRight) + Arrays.toString(frontBack);
    }
}

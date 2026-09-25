package betamoon.worldgen.structure;

import betamoon.worldgen.BlockSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Fixed-order processor settings compiled with a local structure feature. */
public final class StructureProcessors {
    public final boolean includeAir;
    public final Map<Integer, Integer> replacements;
    public final double decay;
    public final BlockSet allowedExisting;
    public final String tileCollision;
    public final String unknownMetadata;
    public final Map<Integer, CustomMetadataTransform> metadataTransforms;

    public StructureProcessors(boolean includeAir, Map<Integer, Integer> replacements, double decay,
            BlockSet allowedExisting, String tileCollision, String unknownMetadata) {
        this(includeAir, replacements, decay, allowedExisting, tileCollision, unknownMetadata,
                Collections.<Integer, CustomMetadataTransform>emptyMap());
    }

    public StructureProcessors(boolean includeAir, Map<Integer, Integer> replacements, double decay,
            BlockSet allowedExisting, String tileCollision, String unknownMetadata,
            Map<Integer, CustomMetadataTransform> metadataTransforms) {
        this.includeAir = includeAir;
        this.replacements = Collections.unmodifiableMap(new LinkedHashMap<Integer, Integer>(replacements));
        this.decay = decay;
        this.allowedExisting = allowedExisting;
        this.tileCollision = tileCollision;
        this.unknownMetadata = unknownMetadata;
        this.metadataTransforms = Collections.unmodifiableMap(
                new LinkedHashMap<Integer, CustomMetadataTransform>(metadataTransforms));
    }
}

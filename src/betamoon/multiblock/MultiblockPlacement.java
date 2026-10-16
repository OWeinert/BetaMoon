package betamoon.multiblock;

import betamoon.multiblock.MultiblockDefinition.Cell;
import betamoon.multiblock.MultiblockDefinition.Variant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A mathematical placement, canonicalized by world-space semantics rather than
 * its authored anchor.
 */
public final class MultiblockPlacement {
    private static final Map<MultiblockDefinition, List<Template>> TEMPLATES = new WeakHashMap<>();
    public final MultiblockDefinition definition;
    public final MultiblockPosition anchor;
    public final int facing;
    public final boolean mirror;
    public final Variant variant;
    public final String key;
    public final List<MultiblockPosition> positions;
    public final int minX;
    public final int minY;
    public final int minZ;
    public final int maxX;
    public final int maxY;
    public final int maxZ;

    public MultiblockPlacement(MultiblockDefinition definition, MultiblockPosition anchor, int facing, boolean mirror,
            Variant variant) {
        Template observed = template(definition, variant, facing, mirror);
        Template canonical = observed;
        for (Template candidate : templates(definition)) {
            if (candidate.variant.repeats == variant.repeats && candidate.signature.equals(observed.signature)) {
                canonical = candidate;
                break;
            }
        }
        anchor = new MultiblockPosition(anchor.x + observed.minX - canonical.minX,
                anchor.y + observed.minY - canonical.minY, anchor.z + observed.minZ - canonical.minZ);
        this.definition = definition;
        this.anchor = anchor;
        this.facing = canonical.facing;
        this.mirror = canonical.mirror;
        this.variant = canonical.variant;
        positions = Collections.unmodifiableList(new AbstractList<MultiblockPosition>() {
            public MultiblockPosition get(int index) {
                return point(MultiblockPlacement.this.variant.cells.get(index));
            }

            public int size() {
                return MultiblockPlacement.this.variant.cells.size();
            }
        });
        minX = anchor.x + canonical.minX;
        minY = anchor.y + canonical.minY;
        minZ = anchor.z + canonical.minZ;
        maxX = anchor.x + canonical.maxX;
        maxY = anchor.y + canonical.maxY;
        maxZ = anchor.z + canonical.maxZ;
        key = definition.key + "#" + canonical.variant.repeats + "#" + canonical.signature + "#" + minX + ":" + minY
                + ":" + minZ;
    }

    private static List<Template> templates(MultiblockDefinition definition) {
        return TEMPLATES.computeIfAbsent(definition, value -> {
            List<Template> result = new ArrayList<>();
            List<Integer> facings = definition.management != null
                    ? definition.management.facings
                    : definition.fixedFacing == null
                            ? Arrays.asList(0, 1, 2, 3)
                            : Collections.singletonList(definition.fixedFacing);
            for (Variant variant : definition.variants) {
                for (int facing : facings) {
                    for (int reflection = 0; reflection < (definition.mirror ? 2 : 1); reflection++) {
                        result.add(new Template(definition, variant, facing, reflection == 1));
                    }
                }
            }
            return result;
        });
    }

    private static Template template(MultiblockDefinition definition, Variant variant, int facing, boolean mirror) {
        for (Template template : templates(definition)) {
            if (template.variant == variant && template.facing == facing && template.mirror == mirror) {
                return template;
            }
        }
        throw new IllegalArgumentException("Placement transform is not permitted by discovery");
    }

    private static final class Template {
        final Variant variant;
        final int facing;
        final boolean mirror;
        final int minX;
        final int minY;
        final int minZ;
        final int maxX;
        final int maxY;
        final int maxZ;
        final String signature;

        Template(MultiblockDefinition definition, Variant variant, int facing, boolean mirror) {
            this.variant = variant;
            this.facing = facing;
            this.mirror = mirror;
            int lowX = Integer.MAX_VALUE;
            int lowY = Integer.MAX_VALUE;
            int lowZ = Integer.MAX_VALUE;
            int highX = Integer.MIN_VALUE;
            int highY = Integer.MIN_VALUE;
            int highZ = Integer.MIN_VALUE;
            for (Cell cell : variant.cells) {
                lowX = Math.min(lowX, MultiblockOrientation.x(cell.x, cell.z, facing, mirror));
                lowY = Math.min(lowY, cell.y);
                lowZ = Math.min(lowZ, MultiblockOrientation.z(cell.x, cell.z, facing, mirror));
                highX = Math.max(highX, MultiblockOrientation.x(cell.x, cell.z, facing, mirror));
                highY = Math.max(highY, cell.y);
                highZ = Math.max(highZ, MultiblockOrientation.z(cell.x, cell.z, facing, mirror));
            }
            maxX = highX;
            maxY = highY;
            maxZ = highZ;
            minX = lowX;
            minY = lowY;
            minZ = lowZ;
            List<String> parts = new ArrayList<>();
            for (Cell cell : variant.cells) {
                StringBuilder role = new StringBuilder()
                        .append(MultiblockOrientation.x(cell.x, cell.z, facing, mirror) - minX).append(':')
                        .append(cell.y - minY).append(':')
                        .append(MultiblockOrientation.z(cell.x, cell.z, facing, mirror) - minZ).append(':')
                        .append(cell.rule.signature(facing, mirror));
                for (Map.Entry<String, String> capture : definition.captures.entrySet()) {
                    if (capture.getValue().contains(cell.symbol)) {
                        role.append("|capture:").append(capture.getKey());
                    }
                }
                for (int index = 0; index < definition.constraints.size(); index++) {
                    if (definition.constraints.get(index).cells.contains(cell.symbol)) {
                        role.append("|constraint:").append(index);
                    }
                }
                if (definition.management != null) {
                    if (definition.management.targets.contains(cell.symbol)) {
                        role.append("|target");
                    }
                    if (definition.management.fillCells.contains(cell.symbol)) {
                        role.append("|fill");
                    }
                }
                parts.add(role.toString());
            }
            Collections.sort(parts);
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                for (String part : parts) {
                    digest.update(part.getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) 0);
                }
                StringBuilder encoded = new StringBuilder();
                for (byte value : digest.digest()) {
                    encoded.append(Character.forDigit((value >>> 4) & 15, 16));
                    encoded.append(Character.forDigit(value & 15, 16));
                }
                signature = encoded.toString();
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException(impossible);
            }
        }
    }

    public MultiblockPosition point(Cell cell) {
        return new MultiblockPosition(anchor.x + MultiblockOrientation.x(cell.x, cell.z, facing, mirror),
                anchor.y + cell.y, anchor.z + MultiblockOrientation.z(cell.x, cell.z, facing, mirror));
    }

    public String order() {
        return String.format("%08d:%08d:%08d:%d:%d:%d", anchor.x + 32000000, anchor.y, anchor.z + 32000000, facing,
                mirror ? 1 : 0, -variant.repeats);
    }
}

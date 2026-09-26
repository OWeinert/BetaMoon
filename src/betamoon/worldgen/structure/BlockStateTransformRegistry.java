package betamoon.worldgen.structure;

import java.util.Locale;
import net.minecraft.src.Block;

/** Horizontal metadata transforms for Beta's directional vanilla block families. */
public final class BlockStateTransformRegistry {
    private BlockStateTransformRegistry() {
    }

    public static int transform(int blockId, int metadata, StructureTransform transform, String unknownPolicy) {
        return transform(blockId, metadata, transform, unknownPolicy, null);
    }

    public static int transform(int blockId, int metadata, StructureTransform transform, String unknownPolicy,
            CustomMetadataTransform custom) {
        if (transform.isIdentity()) {
            return metadata;
        }
        if (custom != null) {
            return custom.transform(metadata, transform);
        }
        Block block = blockId >= 0 && blockId < Block.blocksList.length ? Block.blocksList[blockId] : null;
        String type = block == null ? "" : block.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (type.contains("stairs")) {
            return replace(metadata, 3, transformCode(metadata & 3, transform, 3, 0, 2, 1));
        }
        if (type.contains("door")) {
            return (metadata & 8) != 0 ? metadata
                    : replace(metadata, 3, transformCode(metadata & 3, transform, 3, 0, 1, 2));
        }
        if (type.contains("rail")) {
            return transformRail(blockId, metadata, transform);
        }
        if (type.contains("torch")) {
            int facing = metadata & 7;
            return facing >= 1 && facing <= 4
                    ? replace(metadata, 7, transformCode(facing, transform, 4, 1, 3, 2)) : metadata;
        }
        if (type.contains("ladder") || type.contains("furnace") || type.contains("dispenser")) {
            return transformSide(metadata, transform);
        }
        if (type.contains("sign")) {
            return isStandingSign(blockId) ? transformStandingSign(metadata, transform)
                    : transformSide(metadata, transform);
        }
        if (type.contains("pumpkin") || type.contains("bed")) {
            return replace(metadata, 3, transformCode(metadata & 3, transform, 2, 3, 0, 1));
        }
        if (type.contains("button")) {
            int facing = metadata & 7;
            return facing >= 1 && facing <= 4
                    ? replace(metadata, 7, transformCode(facing, transform, 4, 1, 3, 2)) : metadata;
        }
        if (type.contains("lever")) {
            return transformLever(metadata, transform);
        }
        if (metadata == 0 || "preserve".equals(unknownPolicy)) {
            return metadata;
        }
        throw new IllegalArgumentException("Block " + blockId + " (" + type
                + ") has metadata but no horizontal transform adapter");
    }

    private static int transformSide(int metadata, StructureTransform transform) {
        int facing = metadata & 7;
        if (facing < 2 || facing > 5) {
            return metadata;
        }
        return replace(metadata, 7, transformCode(facing, transform, 2, 5, 3, 4));
    }

    private static int transformLever(int metadata, StructureTransform transform) {
        int powered = metadata & 8;
        int facing = metadata & 7;
        if (facing >= 1 && facing <= 4) {
            return powered | transformCode(facing, transform, 4, 1, 3, 2);
        }
        if ((turns(transform.rotation) & 1) != 0) {
            if (facing == 5) {
                facing = 6;
            } else if (facing == 6) {
                facing = 5;
            } else if (facing == 0) {
                facing = 7;
            } else if (facing == 7) {
                facing = 0;
            }
        }
        return powered | facing;
    }

    private static int transformRail(int blockId, int metadata, StructureTransform transform) {
        boolean poweredRail = namedBlock(blockId, "railPowered", "goldenRail");
        int powered = poweredRail ? metadata & 8 : 0;
        int shape = poweredRail ? metadata & 7 : metadata;
        int directions;
        switch (shape) {
            case 0: directions = bit(0) | bit(2); break;
            case 1: directions = bit(1) | bit(3); break;
            case 2: directions = bit(1); break;
            case 3: directions = bit(3); break;
            case 4: directions = bit(0); break;
            case 5: directions = bit(2); break;
            case 6: directions = bit(1) | bit(2); break;
            case 7: directions = bit(2) | bit(3); break;
            case 8: directions = bit(3) | bit(0); break;
            default: directions = bit(0) | bit(1); break;
        }
        int transformed = 0;
        for (int direction = 0; direction < 4; direction++) {
            if ((directions & bit(direction)) != 0) {
                transformed |= bit(transformDirection(direction, transform));
            }
        }
        int next;
        if (shape <= 1) {
            next = transformed == (bit(0) | bit(2)) ? 0 : 1;
        } else if (shape <= 5) {
            next = transformed == bit(1) ? 2 : transformed == bit(3) ? 3
                    : transformed == bit(0) ? 4 : 5;
        } else {
            next = transformed == (bit(1) | bit(2)) ? 6
                    : transformed == (bit(2) | bit(3)) ? 7
                            : transformed == (bit(3) | bit(0)) ? 8 : 9;
        }
        return powered | next;
    }

    /** Codes are supplied in north, east, south, west order. */
    private static int transformCode(int value, StructureTransform transform, int north, int east, int south,
            int west) {
        int direction = value == north ? 0 : value == east ? 1 : value == south ? 2 : value == west ? 3 : -1;
        if (direction < 0) {
            return value;
        }
        int transformed = transformDirection(direction, transform);
        return transformed == 0 ? north : transformed == 1 ? east : transformed == 2 ? south : west;
    }

    private static int transformDirection(int direction, StructureTransform transform) {
        int result = direction;
        if (transform.mirror == StructureTransform.Mirror.FRONT_BACK) {
            result = result == 1 ? 3 : result == 3 ? 1 : result;
        } else if (transform.mirror == StructureTransform.Mirror.LEFT_RIGHT) {
            result = result == 0 ? 2 : result == 2 ? 0 : result;
        }
        return (result + turns(transform.rotation)) & 3;
    }

    private static int transformStandingSign(int metadata, StructureTransform transform) {
        int facing = metadata & 15;
        if (transform.mirror == StructureTransform.Mirror.FRONT_BACK) {
            facing = Math.floorMod(-facing, 16);
        } else if (transform.mirror == StructureTransform.Mirror.LEFT_RIGHT) {
            facing = Math.floorMod(8 - facing, 16);
        }
        return Math.floorMod(facing + turns(transform.rotation) * 4, 16);
    }

    private static boolean isStandingSign(int blockId) {
        return namedBlock(blockId, "signPost");
    }

    private static boolean namedBlock(int blockId, String... fields) {
        for (String field : fields) {
            try {
                Block block = (Block) Block.class.getField(field).get(null);
                if (block != null && block.blockID == blockId) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private static int turns(StructureTransform.Rotation rotation) {
        return rotation == StructureTransform.Rotation.CLOCKWISE_90 ? 1
                : rotation == StructureTransform.Rotation.CLOCKWISE_180 ? 2
                        : rotation == StructureTransform.Rotation.COUNTERCLOCKWISE_90 ? 3 : 0;
    }

    private static int replace(int metadata, int mask, int value) {
        return metadata & ~mask | value & mask;
    }

    private static int bit(int direction) {
        return 1 << direction;
    }
}

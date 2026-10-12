package betamoon.worldgen;

import betamoon.worldgen.structure.StructureFeature;
import betamoon.worldgen.structure.StructureTransform;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.NBTTagList;

/** Saved deterministic piece graph and chunk completion state for one regional start. */
final class RegionalStructurePlan {
    final WorldGenKey definitionKey;
    final String dimension;
    final int regionX;
    final int regionZ;
    final int startChunkX;
    final int startChunkZ;
    final long seed;
    final List<Piece> pieces;
    final String placementSignature;
    final Set<Long> completedChunks = new LinkedHashSet<Long>();

    RegionalStructurePlan(WorldGenKey definitionKey, String dimension, int regionX, int regionZ,
            int startChunkX, int startChunkZ, long seed, List<Piece> pieces) {
        this(definitionKey, dimension, regionX, regionZ, startChunkX, startChunkZ, seed, pieces, "");
    }

    RegionalStructurePlan(WorldGenKey definitionKey, String dimension, int regionX, int regionZ,
            int startChunkX, int startChunkZ, long seed, List<Piece> pieces, String placementSignature) {
        this.definitionKey = definitionKey;
        this.dimension = dimension;
        this.regionX = regionX;
        this.regionZ = regionZ;
        this.startChunkX = startChunkX;
        this.startChunkZ = startChunkZ;
        this.seed = seed;
        this.pieces = Collections.unmodifiableList(new ArrayList<Piece>(pieces));
        this.placementSignature = placementSignature == null ? "" : placementSignature;
    }

    String id() {
        return definitionKey + "@" + dimension + "@" + regionX + "," + regionZ;
    }

    boolean intersectsChunk(int chunkX, int chunkZ) {
        for (Piece piece : pieces) {
            if (piece.bounds.intersectsChunk(chunkX, chunkZ)) {
                return true;
            }
        }
        return false;
    }

    boolean isComplete(int chunkX, int chunkZ) {
        return completedChunks.contains(chunkKey(chunkX, chunkZ));
    }

    void complete(int chunkX, int chunkZ) {
        completedChunks.add(chunkKey(chunkX, chunkZ));
    }

    NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Definition", definitionKey.toString());
        tag.setString("Dimension", dimension);
        tag.setInteger("RegionX", regionX);
        tag.setInteger("RegionZ", regionZ);
        tag.setInteger("StartChunkX", startChunkX);
        tag.setInteger("StartChunkZ", startChunkZ);
        tag.setLong("Seed", seed);
        tag.setString("PlacementSignature", placementSignature);
        NBTTagList savedPieces = new NBTTagList();
        for (Piece piece : pieces) {
            savedPieces.setTag(piece.write());
        }
        tag.setTag("Pieces", savedPieces);
        NBTTagList completed = new NBTTagList();
        for (Long value : completedChunks) {
            NBTTagCompound chunk = new NBTTagCompound();
            chunk.setInteger("X", (int) (value.longValue() >> 32));
            chunk.setInteger("Z", (int) value.longValue());
            completed.setTag(chunk);
        }
        tag.setTag("Completed", completed);
        return tag;
    }

    static RegionalStructurePlan read(NBTTagCompound tag) {
        try {
            WorldGenKey key = WorldGenKey.parse(tag.getString("Definition"), WorldGenKind.STRUCTURE);
            String dimension = tag.getString("Dimension");
            NBTTagList savedPieces = tag.getTagList("Pieces");
            if (savedPieces.tagCount() < 1 || savedPieces.tagCount() > WorldGenLimits.MAX_REGIONAL_PIECES) {
                return null;
            }
            List<Piece> pieces = new ArrayList<Piece>();
            int terrainChanges = 0;
            for (int index = 0; index < savedPieces.tagCount(); index++) {
                Piece piece = Piece.read((NBTTagCompound) savedPieces.tagAt(index));
                if (piece == null) {
                    return null;
                }
                pieces.add(piece);
                terrainChanges += piece.terrainChanges.size();
                if (terrainChanges > WorldGenLimits.MAX_REGIONAL_TERRAIN_CHANGES) {
                    return null;
                }
            }
            RegionalStructurePlan result = new RegionalStructurePlan(key, dimension, tag.getInteger("RegionX"),
                    tag.getInteger("RegionZ"), tag.getInteger("StartChunkX"), tag.getInteger("StartChunkZ"),
                    tag.getLong("Seed"), pieces, tag.getString("PlacementSignature"));
            NBTTagList completed = tag.getTagList("Completed");
            for (int index = 0; index < completed.tagCount() && index < 8192; index++) {
                NBTTagCompound chunk = (NBTTagCompound) completed.tagAt(index);
                result.completedChunks.add(chunkKey(chunk.getInteger("X"), chunk.getInteger("Z")));
            }
            return result;
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    static final class Piece {
        final WorldGenKey featureKey;
        final BlockPosition origin;
        final StructureTransform transform;
        final long seed;
        final int depth;
        final StructureFeature.Bounds bounds;
        final String generationSignature;
        final List<TerrainChange> terrainChanges;
        final List<StructureFeature.ConformOffset> conformOffsets;

        Piece(WorldGenKey featureKey, BlockPosition origin, StructureTransform transform, long seed, int depth,
                StructureFeature.Bounds bounds, String generationSignature) {
            this(featureKey, origin, transform, seed, depth, bounds, generationSignature,
                    Collections.<TerrainChange>emptyList(), Collections.<StructureFeature.ConformOffset>emptyList());
        }

        Piece(WorldGenKey featureKey, BlockPosition origin, StructureTransform transform, long seed, int depth,
                StructureFeature.Bounds bounds, String generationSignature, List<TerrainChange> terrainChanges) {
            this(featureKey, origin, transform, seed, depth, bounds, generationSignature, terrainChanges,
                    Collections.<StructureFeature.ConformOffset>emptyList());
        }

        Piece(WorldGenKey featureKey, BlockPosition origin, StructureTransform transform, long seed, int depth,
                StructureFeature.Bounds bounds, String generationSignature, List<TerrainChange> terrainChanges,
                List<StructureFeature.ConformOffset> conformOffsets) {
            this.featureKey = featureKey;
            this.origin = origin;
            this.transform = transform;
            this.seed = seed;
            this.depth = depth;
            this.bounds = bounds;
            this.generationSignature = generationSignature;
            this.terrainChanges = Collections.unmodifiableList(new ArrayList<TerrainChange>(terrainChanges));
            this.conformOffsets = Collections.unmodifiableList(
                    new ArrayList<StructureFeature.ConformOffset>(conformOffsets));
        }

        private NBTTagCompound write() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("Feature", featureKey.toString());
            tag.setInteger("X", origin.x);
            tag.setInteger("Y", origin.y);
            tag.setInteger("Z", origin.z);
            tag.setString("Rotation", transform.rotation.name());
            tag.setString("Mirror", transform.mirror.name());
            tag.setLong("Seed", seed);
            tag.setInteger("Depth", depth);
            tag.setInteger("MinX", bounds.min.x);
            tag.setInteger("MinY", bounds.min.y);
            tag.setInteger("MinZ", bounds.min.z);
            tag.setInteger("MaxX", bounds.max.x);
            tag.setInteger("MaxY", bounds.max.y);
            tag.setInteger("MaxZ", bounds.max.z);
            tag.setString("Signature", generationSignature);
            NBTTagList terrain = new NBTTagList();
            for (TerrainChange change : terrainChanges) {
                terrain.setTag(change.write());
            }
            tag.setTag("Terrain", terrain);
            NBTTagList conform = new NBTTagList();
            for (StructureFeature.ConformOffset offset : conformOffsets) {
                NBTTagCompound saved = new NBTTagCompound();
                saved.setInteger("X", offset.x);
                saved.setInteger("Z", offset.z);
                saved.setInteger("Y", offset.y);
                conform.setTag(saved);
            }
            tag.setTag("Conform", conform);
            return tag;
        }

        private static Piece read(NBTTagCompound tag) {
            try {
                WorldGenKey feature = WorldGenKey.parse(tag.getString("Feature"), WorldGenKind.STRUCTURE);
                BlockPosition origin = new BlockPosition(tag.getInteger("X"), tag.getInteger("Y"),
                        tag.getInteger("Z"));
                StructureTransform transform = new StructureTransform(
                        StructureTransform.Rotation.valueOf(tag.getString("Rotation")),
                        StructureTransform.Mirror.valueOf(tag.getString("Mirror")));
                StructureFeature.Bounds bounds = new StructureFeature.Bounds(
                        new BlockPosition(tag.getInteger("MinX"), tag.getInteger("MinY"), tag.getInteger("MinZ")),
                        new BlockPosition(tag.getInteger("MaxX"), tag.getInteger("MaxY"), tag.getInteger("MaxZ")));
                int depth = tag.getInteger("Depth");
                String signature = tag.getString("Signature");
                NBTTagList savedTerrain = tag.getTagList("Terrain");
                List<TerrainChange> terrain = new ArrayList<TerrainChange>();
                for (int index = 0; index < savedTerrain.tagCount()
                        && index < WorldGenLimits.MAX_BLOCK_CHANGES_PER_FEATURE; index++) {
                    TerrainChange change = TerrainChange.read((NBTTagCompound) savedTerrain.tagAt(index));
                    if (change == null) {
                        return null;
                    }
                    terrain.add(change);
                }
                NBTTagList savedConform = tag.getTagList("Conform");
                List<StructureFeature.ConformOffset> conform = new ArrayList<StructureFeature.ConformOffset>();
                for (int index = 0; index < savedConform.tagCount() && index < 1024; index++) {
                    NBTTagCompound saved = (NBTTagCompound) savedConform.tagAt(index);
                    int x = saved.getInteger("X");
                    int y = saved.getInteger("Y");
                    int z = saved.getInteger("Z");
                    if (Math.abs(x) > WorldGenLimits.MAX_FEATURE_RADIUS
                            || Math.abs(z) > WorldGenLimits.MAX_FEATURE_RADIUS || Math.abs(y) > 16) {
                        return null;
                    }
                    conform.add(new StructureFeature.ConformOffset(x, z, y));
                }
                if (origin.y < 0 || origin.y > 127 || depth < 0 || depth > WorldGenLimits.MAX_REGIONAL_DEPTH) {
                    return null;
                }
                if (!signature.matches("[0-9a-f]{64}")) {
                    return null;
                }
                if (bounds.min.x < -30000000 || bounds.max.x > 30000000 || bounds.min.z < -30000000
                        || bounds.max.z > 30000000 || bounds.min.y < 0 || bounds.max.y > 127
                        || bounds.min.x > bounds.max.x || bounds.min.y > bounds.max.y
                        || bounds.min.z > bounds.max.z
                        || bounds.max.x - bounds.min.x > WorldGenLimits.MAX_FEATURE_RADIUS * 2
                        || bounds.max.z - bounds.min.z > WorldGenLimits.MAX_FEATURE_RADIUS * 2) {
                    return null;
                }
                return new Piece(feature, origin, transform, tag.getLong("Seed"), depth, bounds, signature, terrain,
                        conform);
            } catch (RuntimeException error) {
                return null;
            }
        }

        static final class TerrainChange {
            final BlockPosition position;
            final int blockId;
            final int metadata;
            final int expectedBlockId;
            final int expectedMetadata;

            TerrainChange(BlockPosition position, int blockId, int metadata) {
                this(position, blockId, metadata, -1, -1);
            }

            TerrainChange(BlockPosition position, int blockId, int metadata, int expectedBlockId,
                    int expectedMetadata) {
                this.position = position;
                this.blockId = blockId;
                this.metadata = metadata;
                this.expectedBlockId = expectedBlockId;
                this.expectedMetadata = expectedMetadata;
            }

            private NBTTagCompound write() {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setInteger("X", position.x);
                tag.setInteger("Y", position.y);
                tag.setInteger("Z", position.z);
                tag.setInteger("Block", blockId);
                tag.setInteger("Metadata", metadata);
                tag.setInteger("ExpectedBlock", expectedBlockId);
                tag.setInteger("ExpectedMetadata", expectedMetadata);
                return tag;
            }

            private static TerrainChange read(NBTTagCompound tag) {
                int x = tag.getInteger("X");
                int y = tag.getInteger("Y");
                int z = tag.getInteger("Z");
                int block = tag.getInteger("Block");
                int metadata = tag.getInteger("Metadata");
                int expectedBlock = tag.getInteger("ExpectedBlock");
                int expectedMetadata = tag.getInteger("ExpectedMetadata");
                if (x < -30000000 || x > 30000000 || z < -30000000 || z > 30000000 || y < 0 || y > 127
                        || block < 0 || block > 255 || metadata < 0 || metadata > 15) {
                    return null;
                }
                if (expectedBlock < -1 || expectedBlock > 255 || expectedMetadata < -1 || expectedMetadata > 15) {
                    return null;
                }
                return new TerrainChange(new BlockPosition(x, y, z), block, metadata, expectedBlock,
                        expectedMetadata);
            }
        }
    }
}

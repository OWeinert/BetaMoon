package betamoon.world.explosion;

import betamoon.entity.EntityDamageEvents;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.src.AxisAlignedBB;
import net.minecraft.src.Block;
import net.minecraft.src.Entity;
import net.minecraft.src.MathHelper;
import net.minecraft.src.Vec3D;
import net.minecraft.src.World;

/** Beta 1.7.3-compatible calculation with independently selectable effects. */
final class ExplosionExecutor {
    private static final int RAY_GRID = 16;
    private static final float RAY_STEP = 0.3F;
    private static final float RAY_DECAY = RAY_STEP * 12.0F / 16.0F;
    private static final int MAX_AFFECTED_BLOCKS = 32768;
    private static final int MAX_ENTITY_CANDIDATES = 1024;
    private static final int MAX_RESULT_BLOCKS = 4096;

    private final World world;
    private final ExplosionRequest request;
    private final Random fireRandom;

    ExplosionExecutor(World world, ExplosionRequest request) {
        this(world, request, new Random());
    }

    ExplosionExecutor(World world, ExplosionRequest request, Random fireRandom) {
        this.world = world;
        this.request = request;
        this.fireRandom = fireRandom;
    }

    ExplosionResult execute() {
        List<ExplosionPosition> blocks = calculateBlocks();
        List<EntityImpact> impacts = calculateEntityImpacts();
        MutableCounts counts = new MutableCounts();

        applyEntityEffects(impacts, counts);
        applyPresentationAndBlocks(blocks, counts);
        applyFire(blocks, counts);

        int resultSize = request.includeAffectedBlocks ? Math.min(MAX_RESULT_BLOCKS, blocks.size()) : 0;
        List<ExplosionPosition> resultBlocks = request.includeAffectedBlocks
                ? new ArrayList<ExplosionPosition>(blocks.subList(0, resultSize))
                : Collections.<ExplosionPosition>emptyList();
        return new ExplosionResult(request, blocks.size(), counts.blocksDestroyed, counts.entitiesDamaged,
                counts.entitiesKnockedBack, counts.firesPlaced, resultBlocks,
                request.includeAffectedBlocks && blocks.size() > MAX_RESULT_BLOCKS);
    }

    private List<ExplosionPosition> calculateBlocks() {
        Set<ExplosionPosition> affected = new HashSet<ExplosionPosition>();
        for (int sampleX = 0; sampleX < RAY_GRID; sampleX++) {
            for (int sampleY = 0; sampleY < RAY_GRID; sampleY++) {
                for (int sampleZ = 0; sampleZ < RAY_GRID; sampleZ++) {
                    if (!boundary(sampleX, sampleY, sampleZ)) {
                        continue;
                    }
                    traceRay(sampleX, sampleY, sampleZ, affected);
                }
            }
        }
        List<ExplosionPosition> result = new ArrayList<ExplosionPosition>(affected);
        Collections.sort(result);
        return result;
    }

    private void traceRay(int sampleX, int sampleY, int sampleZ, Set<ExplosionPosition> affected) {
        double dx = (double) sampleX / (RAY_GRID - 1) * 2.0D - 1.0D;
        double dy = (double) sampleY / (RAY_GRID - 1) * 2.0D - 1.0D;
        double dz = (double) sampleZ / (RAY_GRID - 1) * 2.0D - 1.0D;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= length;
        dy /= length;
        dz /= length;

        float power = request.strength * (0.7F + world.rand.nextFloat() * 0.6F);
        double x = request.x;
        double y = request.y;
        double z = request.z;
        while (power > 0.0F) {
            int blockX = MathHelper.floor_double(x);
            int blockY = MathHelper.floor_double(y);
            int blockZ = MathHelper.floor_double(z);
            int id = world.getBlockId(blockX, blockY, blockZ);
            if (id > 0) {
                float resistance = BlockExplosionResistance.at(
                        world, blockX, blockY, blockZ, request.x, request.y, request.z, request.source);
                power -= (resistance + 0.3F) * RAY_STEP;
            }
            if (power > 0.0F && affected.add(new ExplosionPosition(blockX, blockY, blockZ))
                    && affected.size() > MAX_AFFECTED_BLOCKS) {
                throw new IllegalStateException("Explosion exceeds the affected-block limit of "
                        + MAX_AFFECTED_BLOCKS);
            }
            x += dx * RAY_STEP;
            y += dy * RAY_STEP;
            z += dz * RAY_STEP;
            power -= RAY_DECAY;
        }
    }

    private List<EntityImpact> calculateEntityImpacts() {
        if (!request.damageEntities && !request.knockback) {
            return Collections.emptyList();
        }
        double radius = request.strength * 2.0D;
        int minX = MathHelper.floor_double(request.x - radius - 1.0D);
        int maxX = MathHelper.floor_double(request.x + radius + 1.0D);
        int minY = MathHelper.floor_double(request.y - radius - 1.0D);
        int maxY = MathHelper.floor_double(request.y + radius + 1.0D);
        int minZ = MathHelper.floor_double(request.z - radius - 1.0D);
        int maxZ = MathHelper.floor_double(request.z + radius + 1.0D);
        List<?> candidates = world.getEntitiesWithinAABBExcludingEntity(request.source,
                AxisAlignedBB.getBoundingBoxFromPool(minX, minY, minZ, maxX, maxY, maxZ));
        if (candidates.size() > MAX_ENTITY_CANDIDATES) {
            throw new IllegalStateException("Explosion exceeds the entity candidate limit of "
                    + MAX_ENTITY_CANDIDATES);
        }

        List<EntityImpact> impacts = new ArrayList<EntityImpact>();
        Vec3D center = Vec3D.createVector(request.x, request.y, request.z);
        for (Object value : candidates) {
            if (!(value instanceof Entity) || value == request.source) {
                continue;
            }
            Entity entity = (Entity) value;
            double normalizedDistance = entity.getDistance(request.x, request.y, request.z) / radius;
            if (normalizedDistance > 1.0D) {
                continue;
            }
            double dx = entity.posX - request.x;
            double dy = entity.posY - request.y;
            double dz = entity.posZ - request.z;
            double directionLength = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double exposure = clampExposure(world.func_675_a(center, entity.boundingBox));
            double impact = (1.0D - normalizedDistance) * exposure;
            int damage = (int) ((impact * impact + impact) / 2.0D * 8.0D * radius + 1.0D);
            if (directionLength > 1.0E-7D) {
                dx /= directionLength;
                dy /= directionLength;
                dz /= directionLength;
            } else {
                dx = 0.0D;
                dy = 0.0D;
                dz = 0.0D;
            }
            impacts.add(new EntityImpact(entity, damage, dx * impact, dy * impact, dz * impact));
        }
        return impacts;
    }

    private void applyEntityEffects(List<EntityImpact> impacts, MutableCounts counts) {
        for (EntityImpact impact : impacts) {
            if (request.damageEntities && EntityDamageEvents.withOrigin(request.source, "explosion",
                    () -> Boolean.valueOf(impact.entity.attackEntityFrom(request.source, impact.damage)))) {
                counts.entitiesDamaged++;
            }
            if (request.knockback && (impact.motionX != 0.0D || impact.motionY != 0.0D || impact.motionZ != 0.0D)) {
                impact.entity.motionX += impact.motionX;
                impact.entity.motionY += impact.motionY;
                impact.entity.motionZ += impact.motionZ;
                counts.entitiesKnockedBack++;
            }
        }
    }

    private void applyFire(List<ExplosionPosition> blocks, MutableCounts counts) {
        if (!request.fire) {
            return;
        }
        for (ExplosionPosition position : blocks) {
            int below = world.getBlockId(position.x, position.y - 1, position.z);
            boolean supported = below > 0 && below < Block.opaqueCubeLookup.length
                    && Block.opaqueCubeLookup[below];
            if (world.getBlockId(position.x, position.y, position.z) == 0 && supported
                    && fireRandom.nextInt(3) == 0
                    && world.setBlockWithNotify(position.x, position.y, position.z, Block.fire.blockID)) {
                counts.firesPlaced++;
            }
        }
    }

    private void applyPresentationAndBlocks(List<ExplosionPosition> blocks, MutableCounts counts) {
        if (request.sound) {
            world.playSoundEffect(request.x, request.y, request.z, "random.explode", 4.0F,
                    (1.0F + (world.rand.nextFloat() - world.rand.nextFloat()) * 0.2F) * 0.7F);
        }
        for (ExplosionPosition position : blocks) {
            if (request.particles) {
                spawnParticles(position);
            }
            if (request.blockMode == ExplosionRequest.BlockMode.DESTROY) {
                destroyBlock(position, counts);
            }
        }
    }

    private void spawnParticles(ExplosionPosition position) {
        double x = position.x + world.rand.nextFloat();
        double y = position.y + world.rand.nextFloat();
        double z = position.z + world.rand.nextFloat();
        double dx = x - request.x;
        double dy = y - request.y;
        double dz = z - request.z;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length > 1.0E-7D) {
            dx /= length;
            dy /= length;
            dz /= length;
        } else {
            dx = 0.0D;
            dy = 0.0D;
            dz = 0.0D;
        }
        double speed = 0.5D / (length / request.strength + 0.1D);
        speed *= world.rand.nextFloat() * world.rand.nextFloat() + 0.3F;
        dx *= speed;
        dy *= speed;
        dz *= speed;
        world.spawnParticle("explode", (x + request.x) / 2.0D, (y + request.y) / 2.0D,
                (z + request.z) / 2.0D, dx, dy, dz);
        world.spawnParticle("smoke", x, y, z, dx, dy, dz);
    }

    private void destroyBlock(ExplosionPosition position, MutableCounts counts) {
        int id = world.getBlockId(position.x, position.y, position.z);
        if (id <= 0 || id >= Block.blocksList.length || Block.blocksList[id] == null) {
            return;
        }
        Block block = Block.blocksList[id];
        int metadata = world.getBlockMetadata(position.x, position.y, position.z);
        if (request.drops) {
            block.dropBlockAsItemWithChance(
                    world, position.x, position.y, position.z, metadata, request.dropChance);
        }
        if (world.setBlockWithNotify(position.x, position.y, position.z, 0)) {
            counts.blocksDestroyed++;
        }
        block.onBlockDestroyedByExplosion(world, position.x, position.y, position.z);
    }

    private static boolean boundary(int x, int y, int z) {
        return x == 0 || x == RAY_GRID - 1 || y == 0 || y == RAY_GRID - 1 || z == 0 || z == RAY_GRID - 1;
    }

    private static double clampExposure(float value) {
        if (Float.isNaN(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static final class EntityImpact {
        private final Entity entity;
        private final int damage;
        private final double motionX;
        private final double motionY;
        private final double motionZ;

        private EntityImpact(Entity entity, int damage, double motionX, double motionY, double motionZ) {
            this.entity = entity;
            this.damage = damage;
            this.motionX = motionX;
            this.motionY = motionY;
            this.motionZ = motionZ;
        }
    }

    private static final class MutableCounts {
        private int blocksDestroyed;
        private int entitiesDamaged;
        private int entitiesKnockedBack;
        private int firesPlaced;
    }
}

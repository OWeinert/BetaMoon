package betamoon.fluid;

import betamoon.content.ContentKey;
import betamoon.content.ContentType;
import betamoon.fluid.contact.FluidContactResolver;
import betamoon.fluid.contact.FluidContactRuntime;
import betamoon.fluid.contact.FluidContactSnapshot;
import betamoon.instrumentation.hooks.fluid.FluidContactCallbacks;
import betamoon.instrumentation.hooks.fluid.FluidEcologyCallbacks;
import net.minecraft.src.AxisAlignedBB;
import net.minecraft.src.Block;
import net.minecraft.src.Chunk;
import net.minecraft.src.Entity;
import net.minecraft.src.IChunkProvider;
import net.minecraft.src.IProgressUpdate;
import net.minecraft.src.Material;
import net.minecraft.src.MaterialLiquid;
import net.minecraft.src.MapColor;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.World;
import net.minecraft.src.WorldProvider;

/** Contact coverage, eye depth, identity, and bounded sampling checks. */
public final class FluidContactTest {
    private FluidContactTest() {
    }

    public static void main(String[] arguments) {
        FluidRegistry registry = FluidRegistry.instance();
        FluidRegistration first = registry.publish(definition("first", 216, 217));
        FluidRegistration second = registry.publish(definition("second", 218, 219));
        TestWorld world = new TestWorld();
        world.first = first;
        world.second = second;
        world.firstMetadata = 7;
        require(first.definition().carrier().material() != second.definition().carrier().material()
                && !registry.isSameFluid(first.definition().stillBlockId(), second.definition().stillBlockId()),
                "Nearby custom liquids keep separate material and fluid identities");

        AxisAlignedBB ankle = AxisAlignedBB.getBoundingBox(0.1D, 0.05D, 0.1D, 0.9D, 0.5D, 0.9D);
        FluidContactSnapshot shallow = FluidContactResolver.resolve(world, ankle, 0.5D, 0.2D, 0.5D);
        require(shallow.dominantBody().fluid() == first, "Shallow contact retains registered identity");
        require(shallow.eyeFluid() == null, "Eye above a shallow surface is not immersed");
        require(shallow.cellsRead() <= 64 && !shallow.approximate(), "Ordinary actor uses exact bounded sampling");

        FluidContactSnapshot immersed = FluidContactResolver.resolve(world, ankle, 0.5D, 0.1D, 0.5D);
        require(immersed.eyeFluid() == first, "Eye below the surface selects fluid identity");

        world.firstMetadata = 0;
        AxisAlignedBB mixed = AxisAlignedBB.getBoundingBox(0.1D, 0.0D, 0.1D, 1.9D, 0.8D, 0.9D);
        FluidContactSnapshot mixedContact = FluidContactResolver.resolve(world, mixed, 1.5D, 0.5D, 0.5D);
        require(mixedContact.bodyContacts().size() == 2, "Different fluids are aggregated separately");
        require(mixedContact.eyeFluid() == second, "Eye identity is independent of body dominance");
        require(mixedContact.dominantBody().fluid() == first,
                "Equal body coverage resolves by lowest occupied coordinate");

        AxisAlignedBB giant = AxisAlignedBB.getBoundingBox(0.0D, 0.0D, 0.0D, 20.0D, 20.0D, 20.0D);
        FluidContactSnapshot bounded = FluidContactResolver.resolve(world, giant, 0.5D, 0.5D, 0.5D);
        require(bounded.approximate() && bounded.cellsRead() <= 64,
                "Oversized actors sample at most 64 cells including the eye");

        world.firstMetadata = 0;
        TestEntity actor = new TestEntity(world);
        actor.setPosition(0.5D, 0.0D, 0.5D);
        for (int tick = 1; tick <= 5; tick++) {
            actor.ticksExisted = tick;
            FluidContactRuntime.finishEntityTick(actor, 0, false);
        }
        require(actor.damage == 0, "Contact damage waits for the declared interval");
        actor.ticksExisted = 6;
        FluidContactRuntime.finishEntityTick(actor, 0, false);
        require(actor.damage == 2, "Touching one fluid applies one interval damage event");
        actor.setPosition(3.5D, 0.0D, 0.5D);
        actor.ticksExisted = 7;
        FluidContactRuntime.finishEntityTick(actor, 0, false);
        actor.setPosition(0.5D, 0.0D, 0.5D);
        actor.ticksExisted = 8;
        FluidContactRuntime.finishEntityTick(actor, 0, false);
        require(actor.damage == 2, "Leaving and reentering resets the interval timer");

        TestEntity neutralMover = new TestEntity(world);
        neutralMover.setPosition(1.5D, 0.0D, 0.5D);
        neutralMover.ticksExisted = 1;
        require(!FluidContactRuntime.movement(neutralMover, FluidEntityPolicy.Movement.WATER)
                && !FluidContactRuntime.shouldDrown(neutralMover) && !FluidContactRuntime.isWet(neutralMover, false),
                "A liquid material does not force movement, drowning, or wetness");
        FluidContactRuntime.tickWithoutNativeUpdate(neutralMover);
        require(neutralMover.fire == 600, "Manual actors receive declared ignition without native entity ticks");

        TestEntity mixedActor = new TestEntity(world);
        mixedActor.setPosition(1.0D, 0.0D, 0.5D);
        mixedActor.fire = 20;
        mixedActor.ticksExisted = 1;
        FluidContactRuntime.finishEntityTick(mixedActor, 20, false);
        require(mixedActor.fire == 600, "Mixed fluid ignition deterministically follows extinguishing");

        try {
            new FluidEntityPolicy(FluidEntityPolicy.Movement.NONE, 0.0F, false, false, false, false, 1, 201);
            throw new AssertionError("Unbounded contact interval was accepted");
        } catch (IllegalArgumentException expected) {
            // Validation runs before registration.
        }
        ecology(world, first);
        itemLavaPolicy(world, second);
        System.out.println("Fluid contact checks passed.");
    }

    private static void itemLavaPolicy(TestWorld world, FluidRegistration registration) {
        require(world.getBlockMaterial(1, 0, 0) != Material.lava,
                "The custom liquid retains its own native material identity");
        require(FluidContactCallbacks.itemLavaMaterial(world, 1, 0, 0) == Material.rock,
                "A neutral custom fluid must not trigger native lava item buoyancy");
        require(FluidContactCallbacks.itemLavaMaterial(world, 2, 0, 0) == Material.air,
                "Unregistered cells retain their native material");
        FluidDefinition base = registration.definition();
        FluidRegistry.instance().publish(new FluidDefinition(base.key(), base.owner(), base.preset(), base.carrier(),
                base.flow(), base.appearance(), base.displacement(),
                new FluidEntityPolicy(FluidEntityPolicy.Movement.LAVA, 0.0F, false, false, false, false, 0),
                base.camera(), base.ecology(), base.flowingBlockId(), base.stillBlockId(), -1, false));
        require(FluidContactCallbacks.itemLavaMaterial(world, 1, 0, 0) == Material.lava,
                "Explicit lava movement enables native item buoyancy without changing material identity");
    }

    private static void ecology(TestWorld world, FluidRegistration registration) {
        world.firstMetadata = 0;
        require(FluidEcologyCallbacks.farmland(world, 0, 0, 0) == Material.rock,
                "A custom liquid must not silently hydrate farmland");
        require(FluidEcologyCallbacks.pathBlock(world, 0, 0, 0) == Block.obsidian.blockID,
                "Custom-fluid pathfinding defaults to blocked");
        FluidDefinition base = registration.definition();
        FluidRegistry.instance()
                .publish(new FluidDefinition(base.key(), base.owner(), base.preset(), base.carrier(), base.flow(),
                        base.appearance(), base.displacement(), base.entity(), base.camera(),
                        new FluidEcologyPolicy(true, true, true, false, true, true, "water"), base.flowingBlockId(),
                        base.stillBlockId(), -1, false));
        require(FluidEcologyCallbacks.farmland(world, 0, 0, 0) == Material.water,
                "Explicit hydration policy follows stable fluid identity");
        require(FluidEcologyCallbacks.reeds(world, 0, 0, 0) == Material.water,
                "Explicit reed policy is independent of carrier");
        require(FluidEcologyCallbacks.falling(world, 0, 0, 0) == 2,
                "Falling blocks consult their dedicated fluid trait");
        require(FluidEcologyCallbacks.pathBlock(world, 0, 0, 0) == Block.waterStill.blockID,
                "Pathfinding uses the explicit avoidance category");
        require(FluidEcologyCallbacks.boat(world, AxisAlignedBB.getBoundingBox(0.1, 0.1, 0.1, 0.9, 0.5, 0.9),
                Material.water), "Boats float in an explicitly enabled custom fluid");
        require(world.getBlockId(0, 0, 0) == base.stillBlockId(),
                "Synthetic path materials must never change world blocks");
    }

    private static FluidDefinition definition(String name, int flowing, int still) {
        FluidCarrier carrier = new FluidCarrier.Custom("contact_test:material/" + name, "liquid",
                new MaterialLiquid("first".equals(name) ? MapColor.waterColor : MapColor.tntColor));
        return new FluidDefinition(ContentKey.of("contact_test", ContentType.FLUID, name), "contact_test.lua",
                "neutral", carrier, FluidFlowPolicy.neutral(), new FluidAppearance(1, 2, 0xFFFFFF, 1, 0, 3),
                FluidDisplacementPolicy.DROP,
                new FluidEntityPolicy(FluidEntityPolicy.Movement.NONE, 0.0F, false, "first".equals(name),
                        "first".equals(name), "second".equals(name), "first".equals(name) ? 2 : 0, 5),
                flowing, still, -1, false);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class TestWorld extends World {
        private FluidRegistration first;
        private FluidRegistration second;
        private int firstMetadata;

        private TestWorld() {
            super(null, "fluid_contact_test", new WorldProvider() {
            }, 123L);
        }

        @Override
        protected IChunkProvider getChunkProvider() {
            return new IChunkProvider() {
                public boolean chunkExists(int x, int z) {
                    return true;
                }

                public Chunk provideChunk(int x, int z) {
                    return null;
                }

                public Chunk prepareChunk(int x, int z) {
                    return null;
                }

                public void populate(IChunkProvider provider, int x, int z) {
                }

                public boolean saveChunks(boolean force, IProgressUpdate progress) {
                    return true;
                }

                public boolean unload100OldestChunks() {
                    return false;
                }

                public boolean canSave() {
                    return false;
                }

                public String makeString() {
                    return "FluidContactTest";
                }
            };
        }

        @Override
        public boolean blockExists(int x, int y, int z) {
            return y >= 0 && y < 128;
        }

        @Override
        public int getBlockId(int x, int y, int z) {
            if (y == 0 && z == 0 && x == 0) {
                return first.definition().stillBlockId();
            }
            if (y == 0 && z == 0 && x == 1) {
                return second.definition().stillBlockId();
            }
            return 0;
        }

        @Override
        public int getBlockMetadata(int x, int y, int z) {
            return x == 0 ? firstMetadata : 0;
        }

        @Override
        public Material getBlockMaterial(int x, int y, int z) {
            int id = getBlockId(x, y, z);
            if (first != null && first.roleOf(id) != null) {
                return first.definition().carrier().material();
            }
            if (second != null && second.roleOf(id) != null) {
                return second.definition().carrier().material();
            }
            return Material.air;
        }

        @Override
        public boolean canBlockBeRainedOn(int x, int y, int z) {
            return false;
        }
    }

    private static final class TestEntity extends Entity {
        private int damage;

        private TestEntity(World world) {
            super(world);
        }

        @Override
        protected void entityInit() {
        }

        @Override
        protected void readEntityFromNBT(NBTTagCompound compound) {
        }

        @Override
        protected void writeEntityToNBT(NBTTagCompound compound) {
        }

        @Override
        public boolean attackEntityFrom(Entity attacker, int amount) {
            damage += amount;
            return true;
        }
    }
}

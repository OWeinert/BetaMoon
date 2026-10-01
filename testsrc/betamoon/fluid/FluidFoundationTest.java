package betamoon.fluid;

import betamoon.content.ContentKey;
import betamoon.content.ContentType;
import net.minecraft.src.Block;

/** Verifies metadata, registry identity and reload-safe structural contracts. */
public final class FluidFoundationTest {
    private FluidFoundationTest() {
    }

    public static void main(String[] arguments) {
        verifyMetadata();
        verifyPolicies();
        verifyRegistryIdentity();
        verifyVanillaReferences();
        System.out.println("Fluid foundation checks passed.");
    }

    private static void verifyMetadata() {
        require(FluidState.of(0).isSource(), "Metadata zero is a source");
        require(!FluidState.of(7).isFalling(), "Metadata seven is horizontal flow");
        require(FluidState.of(8).isFalling(), "Metadata eight starts falling states");
        require(FluidState.of(15).level() == 0, "Falling states have effective level zero");
        require(FluidState.fallingFrom(7) == 15, "Horizontal levels preserve their bits when falling");
        require(close(FluidState.of(0).surfaceHeight(), 8.0F / 9.0F), "Source height matches vanilla");
        expect(IllegalArgumentException.class, () -> FluidState.of(16));
    }

    private static void verifyPolicies() {
        FluidFlowPolicy water = FluidFlowPolicy.water();
        require(water.tickRate() == 5 && water.levelStep() == 1, "Water parity defaults are retained");
        require(water.sourceCreation() == FluidFlowPolicy.SourceCreation.TWO_ADJACENT,
                "Water parity creates sources from two adjacent sources");

        FluidFlowPolicy lava = FluidFlowPolicy.lava();
        require(lava.tickRate() == 30 && lava.levelStep() == 2, "Lava parity defaults are retained");
        require(lava.levelStep(true) == 1, "Lava parity uses its explicit Nether level-step override");
        require(close(lava.retreatDelayChance(), 0.75F), "Lava parity retains its delayed retreat chance");
        expect(IllegalArgumentException.class,
                () -> new FluidFlowPolicy(0, 1, 4, FluidFlowPolicy.SourceCreation.NEVER, 0.0F));
        expect(IllegalArgumentException.class,
                () -> new FluidDefinition(ContentKey.parseForType("test:invalid_bucket", ContentType.FLUID),
                        "test.lua", "neutral", FluidCarrier.WATER, FluidFlowPolicy.neutral(),
                        new FluidAppearance(1, 2, 0xFFFFFF, 1, 0, 3), FluidDisplacementPolicy.DROP,
                        232, 233, 255, false));
    }

    private static void verifyRegistryIdentity() {
        FluidRegistry registry = FluidRegistry.instance();
        FluidDefinition first = definition("test:acid", 220, 224, FluidFlowPolicy.neutral());
        FluidDefinition second = definition("test:brine", 221, 229, FluidFlowPolicy.water());
        FluidRegistration acid = registry.publish(first);
        FluidRegistration brine = registry.publish(second);

        require(registry.get(first.key()) == acid, "Fluid lookup preserves the stable registration");
        require(registry.getByBlockId(220) == acid && registry.getByBlockId(224) == acid,
                "Non-adjacent paired block IDs resolve to the same fluid");
        require(!registry.isSameFluid(220, 221), "A shared water material does not merge fluid identities");
        require(acid.roleOf(220) == FluidBlockRole.FLOWING && acid.roleOf(224) == FluidBlockRole.STILL,
                "Block roles are explicit rather than inferred from adjacency");

        FluidDefinition replacement = definition("test:acid", 220, 224,
                new FluidFlowPolicy(9, 1, 3, FluidFlowPolicy.SourceCreation.NEVER, 0.25F));
        require(registry.publish(replacement) == acid, "Reload updates the stable registration slot");
        require(acid.definition().flow().tickRate() == 9, "Reloadable policy is replaced atomically");
        expect(IllegalStateException.class,
                () -> registry.publish(definition("test:acid", 222, 224, FluidFlowPolicy.neutral())));
        expect(IllegalStateException.class,
                () -> registry.publish(definition("test:collision", 220, 230, FluidFlowPolicy.neutral())));
    }

    private static void verifyVanillaReferences() {
        FluidRegistry registry = FluidRegistry.instance();
        registry.registerVanilla();
        FluidRegistration water = registry.get(ContentKey.parseForType("minecraft:water", ContentType.FLUID));
        FluidRegistration lava = registry.get(ContentKey.parseForType("minecraft:lava", ContentType.FLUID));
        require(water != null && water.definition().isVanilla(), "Vanilla water has a read-only registry identity");
        require(lava != null && lava.definition().isVanilla(), "Vanilla lava has a read-only registry identity");
        require(registry.getByBlockId(Block.waterMoving.blockID) == water, "Vanilla flowing water resolves by ID");
        require(registry.getByBlockId(Block.lavaStill.blockID) == lava, "Vanilla still lava resolves by ID");
    }

    private static FluidDefinition definition(String key, int flowing, int still, FluidFlowPolicy flow) {
        return new FluidDefinition(ContentKey.parseForType(key, ContentType.FLUID), "test.lua", "neutral",
                FluidCarrier.WATER, flow, new FluidAppearance(1, 2, 0x88AAFF, 1, 0, 3), FluidDisplacementPolicy.DROP,
                flowing, still, -1, false);
    }

    private static void expect(Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable error) {
            if (type.isInstance(error)) {
                return;
            }
            throw new AssertionError("Expected " + type.getName() + ", got " + error, error);
        }
        throw new AssertionError("Expected " + type.getName());
    }

    private static boolean close(float left, float right) {
        return Math.abs(left - right) < 0.0001F;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

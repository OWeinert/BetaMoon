package betamoon.instrumentation.hooks.fluid;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.CallRedirectHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.api.ValueBinding;

/**
 * Narrow environment consumers; fluid identity remains separate from native
 * carrier material.
 */
public final class FluidEcologyHook implements HookModule {
    private static final String CALLBACKS = "betamoon/instrumentation/hooks/fluid/FluidEcologyCallbacks";
    private static final ClassRef WORLD = new ClassRef("net/minecraft/src/World");
    private final boolean dedicatedServer;

    public FluidEcologyHook(boolean dedicatedServer) {
        this.dedicatedServer = dedicatedServer;
    }

    @Override
    public String getId() {
        return "betamoon:fluid_ecology";
    }

    @Override
    public void register(HookRegistrar registrar) {
        registrar.register(AroundHookDefinition
                .builder(getId() + ":falling",
                        new MethodRef(new ClassRef("net/minecraft/src/BlockSand"), "canFallBelow",
                                "(Lnet/minecraft/src/World;III)Z"))
                .capture(HandlerRef.of(CALLBACKS, "falling", "(Lnet/minecraft/src/World;III)I"),
                        ValueBinding.argument(0), ValueBinding.argument(1), ValueBinding.argument(2),
                        ValueBinding.argument(3))
                .skipWhenCapturedNonZero().onReturn(HandlerRef.of(CALLBACKS, "fallingResult", "(IZ)Z"),
                        ValueBinding.capturedValue(), ValueBinding.returnValue())
                .build());
        material(registrar, "farmland", new MethodRef(new ClassRef("net/minecraft/src/BlockFarmland"), "isWaterNearby",
                "(Lnet/minecraft/src/World;III)Z"));
        material(registrar, "reeds", new MethodRef(new ClassRef("net/minecraft/src/Block"), "canPlaceBlockAt",
                "(Lnet/minecraft/src/World;III)Z").implementedBy(new ClassRef("net/minecraft/src/BlockReed")));
        material(registrar, "habitat", new MethodRef(new ClassRef("net/minecraft/src/SpawnerAnimals"),
                "performSpawning", "(Lnet/minecraft/src/World;ZZ)I"));
        ClassRef spawner = new ClassRef("net/minecraft/src/SpawnerAnimals");
        registrar.register(AroundHookDefinition
                .builder(getId() + ":habitat_position",
                        new MethodRef(spawner, dedicatedServer ? "func_21167_a" : "canCreatureTypeSpawnAtLocation",
                                "(Lnet/minecraft/src/EnumCreatureType;Lnet/minecraft/src/World;III)Z"))
                .capture(HandlerRef.of(CALLBACKS, "pass", "()I"))
                .onReturn(
                        HandlerRef.of(CALLBACKS, "habitatResult",
                                "(Lnet/minecraft/src/EnumCreatureType;Lnet/minecraft/src/World;IIIZ)Z"),
                        ValueBinding.argument(0), ValueBinding.argument(1), ValueBinding.argument(2),
                        ValueBinding.argument(3), ValueBinding.argument(4), ValueBinding.returnValue())
                .build());
        registrar.register(new CallRedirectHookDefinition(getId() + ":pathfinding",
                new MethodRef(new ClassRef("net/minecraft/src/Pathfinder"), "getVerticalOffset",
                        "(Lnet/minecraft/src/Entity;IIILnet/minecraft/src/PathPoint;)I"),
                new MethodRef(new ClassRef("net/minecraft/src/IBlockAccess"), "getBlockId", "(III)I"),
                HandlerRef.of(CALLBACKS, "pathBlock", "(Lnet/minecraft/src/IBlockAccess;III)I")));
        registrar.register(AroundHookDefinition
                .builder(getId() + ":sponge",
                        new MethodRef(new ClassRef("net/minecraft/src/Block"), "onBlockAdded",
                                "(Lnet/minecraft/src/World;III)V")
                                .implementedBy(new ClassRef("net/minecraft/src/BlockSponge")))
                .capture(HandlerRef.of(CALLBACKS, "pass", "()I"))
                .onReturn(HandlerRef.of(CALLBACKS, "sponge", "(Lnet/minecraft/src/World;III)V"),
                        ValueBinding.argument(0), ValueBinding.argument(1), ValueBinding.argument(2),
                        ValueBinding.argument(3))
                .build());
        registrar.register(new CallRedirectHookDefinition(getId() + ":boat",
                new MethodRef(new ClassRef("net/minecraft/src/Entity"), "onUpdate", "()V")
                        .implementedBy(new ClassRef("net/minecraft/src/EntityBoat")),
                new MethodRef(WORLD, "isAABBInMaterial",
                        "(Lnet/minecraft/src/AxisAlignedBB;Lnet/minecraft/src/Material;)Z"),
                HandlerRef.of(CALLBACKS, "boat",
                        "(Lnet/minecraft/src/World;Lnet/minecraft/src/AxisAlignedBB;Lnet/minecraft/src/Material;)Z")));
    }

    private void material(HookRegistrar registrar, String purpose, MethodRef target) {
        registrar.register(new CallRedirectHookDefinition(getId() + ":" + purpose, target,
                new MethodRef(new ClassRef("net/minecraft/src/IBlockAccess"), "getBlockMaterial",
                        "(III)Lnet/minecraft/src/Material;").implementedBy(WORLD),
                HandlerRef.of(CALLBACKS, purpose, "(Lnet/minecraft/src/World;III)Lnet/minecraft/src/Material;"))
                .allCallsInTarget());
    }
}

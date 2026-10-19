package betamoon.instrumentation.hooks.worldgen;

import betamoon.instrumentation.api.CallRedirectHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.MethodRef;

/** Category gates restricted to native overworld population call sites. */
public final class WorldPopulationHook implements HookModule {
    private static final String CALLBACKS = "betamoon/instrumentation/hooks/worldgen/WorldPopulationCallbacks";

    @Override
    public String getId() {
        return "betamoon:world_population";
    }

    @Override
    public void register(HookRegistrar registrar) {
        registrar.register(new CallRedirectHookDefinition(getId() + ":animals",
                new MethodRef(new ClassRef("net/minecraft/src/World"), "tick", "()V"),
                new MethodRef(new ClassRef("net/minecraft/src/SpawnerAnimals"), "performSpawning",
                        "(Lnet/minecraft/src/World;ZZ)I"),
                HandlerRef.of(CALLBACKS, "spawnAnimals", "(Lnet/minecraft/src/World;ZZ)I")).staticInvocation());
        MethodRef target = new MethodRef(new ClassRef("net/minecraft/src/IChunkProvider"), "populate",
                "(Lnet/minecraft/src/IChunkProvider;II)V")
                .implementedBy(new ClassRef("net/minecraft/src/ChunkProviderGenerate"));
        registrar.register(new CallRedirectHookDefinition(getId() + ":WorldGenLakes", target,
                new MethodRef(new ClassRef("net/minecraft/src/WorldGenerator"), "generate",
                        "(Lnet/minecraft/src/World;Ljava/util/Random;III)Z")
                        .implementedBy(new ClassRef("net/minecraft/src/WorldGenLakes")),
                HandlerRef.of(CALLBACKS, "generate",
                        "(Lnet/minecraft/src/WorldGenLakes;" + "Lnet/minecraft/src/World;Ljava/util/Random;III)Z"))
                .allCallsInTarget());
        registrar.register(new CallRedirectHookDefinition(getId() + ":WorldGenDungeons", target,
                new MethodRef(new ClassRef("net/minecraft/src/WorldGenerator"), "generate",
                        "(Lnet/minecraft/src/World;Ljava/util/Random;III)Z")
                        .implementedBy(new ClassRef("net/minecraft/src/WorldGenDungeons")),
                HandlerRef.of(CALLBACKS, "generate",
                        "(Lnet/minecraft/src/WorldGenDungeons;" + "Lnet/minecraft/src/World;Ljava/util/Random;III)Z"))
                .allCallsInTarget());
        registrar.register(new CallRedirectHookDefinition(getId() + ":WorldGenClay", target,
                new MethodRef(new ClassRef("net/minecraft/src/WorldGenerator"), "generate",
                        "(Lnet/minecraft/src/World;Ljava/util/Random;III)Z")
                        .implementedBy(new ClassRef("net/minecraft/src/WorldGenClay")),
                HandlerRef.of(CALLBACKS, "generate",
                        "(Lnet/minecraft/src/WorldGenClay;" + "Lnet/minecraft/src/World;Ljava/util/Random;III)Z"))
                .allCallsInTarget());
        registrar.register(new CallRedirectHookDefinition(getId() + ":WorldGenMinable", target,
                new MethodRef(new ClassRef("net/minecraft/src/WorldGenerator"), "generate",
                        "(Lnet/minecraft/src/World;Ljava/util/Random;III)Z")
                        .implementedBy(new ClassRef("net/minecraft/src/WorldGenMinable")),
                HandlerRef.of(CALLBACKS, "generate",
                        "(Lnet/minecraft/src/WorldGenMinable;" + "Lnet/minecraft/src/World;Ljava/util/Random;III)Z"))
                .allCallsInTarget());
        registrar.register(new CallRedirectHookDefinition(getId() + ":WorldGenerator", target,
                new MethodRef(new ClassRef("net/minecraft/src/WorldGenerator"), "generate",
                        "(Lnet/minecraft/src/World;Ljava/util/Random;III)Z")
                        .implementedBy(new ClassRef("net/minecraft/src/WorldGenerator")),
                HandlerRef.of(CALLBACKS, "generate",
                        "(Lnet/minecraft/src/WorldGenerator;" + "Lnet/minecraft/src/World;Ljava/util/Random;III)Z"))
                .allCallsInTarget());
        registrar.register(new CallRedirectHookDefinition(getId() + ":WorldGenFlowers", target,
                new MethodRef(new ClassRef("net/minecraft/src/WorldGenerator"), "generate",
                        "(Lnet/minecraft/src/World;Ljava/util/Random;III)Z")
                        .implementedBy(new ClassRef("net/minecraft/src/WorldGenFlowers")),
                HandlerRef.of(CALLBACKS, "generate",
                        "(Lnet/minecraft/src/WorldGenFlowers;" + "Lnet/minecraft/src/World;Ljava/util/Random;III)Z"))
                .allCallsInTarget());
        registrar.register(new CallRedirectHookDefinition(getId() + ":WorldGenTallGrass", target,
                new MethodRef(new ClassRef("net/minecraft/src/WorldGenerator"), "generate",
                        "(Lnet/minecraft/src/World;Ljava/util/Random;III)Z")
                        .implementedBy(new ClassRef("net/minecraft/src/WorldGenTallGrass")),
                HandlerRef.of(CALLBACKS, "generate",
                        "(Lnet/minecraft/src/WorldGenTallGrass;" + "Lnet/minecraft/src/World;Ljava/util/Random;III)Z"))
                .allCallsInTarget());
        registrar.register(new CallRedirectHookDefinition(getId() + ":WorldGenDeadBush", target,
                new MethodRef(new ClassRef("net/minecraft/src/WorldGenerator"), "generate",
                        "(Lnet/minecraft/src/World;Ljava/util/Random;III)Z")
                        .implementedBy(new ClassRef("net/minecraft/src/WorldGenDeadBush")),
                HandlerRef.of(CALLBACKS, "generate",
                        "(Lnet/minecraft/src/WorldGenDeadBush;" + "Lnet/minecraft/src/World;Ljava/util/Random;III)Z"))
                .allCallsInTarget());
        registrar.register(new CallRedirectHookDefinition(getId() + ":WorldGenReed", target,
                new MethodRef(new ClassRef("net/minecraft/src/WorldGenerator"), "generate",
                        "(Lnet/minecraft/src/World;Ljava/util/Random;III)Z")
                        .implementedBy(new ClassRef("net/minecraft/src/WorldGenReed")),
                HandlerRef.of(CALLBACKS, "generate",
                        "(Lnet/minecraft/src/WorldGenReed;" + "Lnet/minecraft/src/World;Ljava/util/Random;III)Z"))
                .allCallsInTarget());
        registrar.register(new CallRedirectHookDefinition(getId() + ":WorldGenPumpkin", target,
                new MethodRef(new ClassRef("net/minecraft/src/WorldGenerator"), "generate",
                        "(Lnet/minecraft/src/World;Ljava/util/Random;III)Z")
                        .implementedBy(new ClassRef("net/minecraft/src/WorldGenPumpkin")),
                HandlerRef.of(CALLBACKS, "generate",
                        "(Lnet/minecraft/src/WorldGenPumpkin;" + "Lnet/minecraft/src/World;Ljava/util/Random;III)Z"))
                .allCallsInTarget());
        registrar.register(new CallRedirectHookDefinition(getId() + ":WorldGenCactus", target,
                new MethodRef(new ClassRef("net/minecraft/src/WorldGenerator"), "generate",
                        "(Lnet/minecraft/src/World;Ljava/util/Random;III)Z")
                        .implementedBy(new ClassRef("net/minecraft/src/WorldGenCactus")),
                HandlerRef.of(CALLBACKS, "generate",
                        "(Lnet/minecraft/src/WorldGenCactus;" + "Lnet/minecraft/src/World;Ljava/util/Random;III)Z"))
                .allCallsInTarget());
        registrar.register(new CallRedirectHookDefinition(getId() + ":WorldGenLiquids", target,
                new MethodRef(new ClassRef("net/minecraft/src/WorldGenerator"), "generate",
                        "(Lnet/minecraft/src/World;Ljava/util/Random;III)Z")
                        .implementedBy(new ClassRef("net/minecraft/src/WorldGenLiquids")),
                HandlerRef.of(CALLBACKS, "generate",
                        "(Lnet/minecraft/src/WorldGenLiquids;" + "Lnet/minecraft/src/World;Ljava/util/Random;III)Z"))
                .allCallsInTarget());
        registrar.register(new CallRedirectHookDefinition(getId() + ":snow", target,
                new MethodRef(new ClassRef("net/minecraft/src/World"), "setBlockWithNotify", "(IIII)Z"),
                HandlerRef.of(CALLBACKS, "snow", "(Lnet/minecraft/src/World;IIII)Z")).allCallsInTarget());
    }
}

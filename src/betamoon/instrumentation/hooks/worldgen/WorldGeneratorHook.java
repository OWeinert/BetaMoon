package betamoon.instrumentation.hooks.worldgen;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.FieldRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.api.ValueBinding;

/**
 * Integrated-world provider selection before spawn generation; no-profile calls
 * retain native identity.
 */
public final class WorldGeneratorHook implements HookModule {
    public static final String ID = "betamoon:world_generator";
    private static final String CALLBACKS = "betamoon/instrumentation/hooks/worldgen/WorldGeneratorCallbacks";
    private static final ClassRef WORLD = new ClassRef("net/minecraft/src/World");
    private static final ClassRef PROVIDER = new ClassRef("net/minecraft/src/WorldProvider");

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void register(HookRegistrar registrar) {
        registrar
                .register(
                        AroundHookDefinition
                                .builder(ID + ":prepare",
                                        new MethodRef(WORLD, "getChunkProvider",
                                                "()Lnet/minecraft/src/IChunkProvider;"))
                                .capture(
                                        HandlerRef.of(CALLBACKS, "prepare",
                                                "(Lnet/minecraft/src/World;" + "Lnet/minecraft/src/ISaveHandler;)I"),
                                        ValueBinding.thisValue(),
                                        ValueBinding.instanceField(
                                                new FieldRef(WORLD, "saveHandler", "Lnet/minecraft/src/ISaveHandler;")))
                                .onReturn(HandlerRef.of(CALLBACKS, "completed", "(Lnet/minecraft/src/World;)V"),
                                        ValueBinding.thisValue())
                                .build());
        registrar
                .register(
                        AroundHookDefinition
                                .builder(ID + ":provider",
                                        new MethodRef(PROVIDER, "getChunkProvider",
                                                "()Lnet/minecraft/src/IChunkProvider;"))
                                .capture(HandlerRef.of(CALLBACKS, "entering", "()I"))
                                .onReturn(HandlerRef.of(CALLBACKS, "select", "(Lnet/minecraft/src/WorldProvider;"
                                        + "Lnet/minecraft/src/IChunkProvider;)Lnet/minecraft/src/IChunkProvider;"),
                                        ValueBinding.thisValue(), ValueBinding.returnValue())
                                .build());
        registrar.register(
                AroundHookDefinition.builder(ID + ":spawn", new MethodRef(WORLD, "getInitialSpawnLocation", "()V"))
                        .capture(HandlerRef.of(CALLBACKS, "initialSpawn", "(Lnet/minecraft/src/World;)I"),
                                ValueBinding.thisValue())
                        .skipWhenCapturedNonZero()
                        .onReturn(HandlerRef.of(CALLBACKS, "spawnSelected", "(Lnet/minecraft/src/World;)V"),
                                ValueBinding.thisValue())
                        .build());
        registrar.register(
                AroundHookDefinition.builder(ID + ":spawn_recheck", new MethodRef(WORLD, "setSpawnLocation", "()V"))
                        .capture(HandlerRef.of(CALLBACKS, "spawnRecheck", "(Lnet/minecraft/src/World;)I"),
                                ValueBinding.thisValue())
                        .skipWhenCapturedNonZero().onReturn(HandlerRef.of(CALLBACKS, "completedSpawnRecheck", "()V"))
                        .build());
        registrar.register(AroundHookDefinition
                .builder(ID + ":chunk_light",
                        new MethodRef(new ClassRef("net/minecraft/src/Chunk"), "onChunkLoad", "()V"))
                .capture(HandlerRef.of(CALLBACKS, "entering", "()I"))
                .onReturn(HandlerRef.of(CALLBACKS, "chunkLoaded", "(Lnet/minecraft/src/Chunk;)V"),
                        ValueBinding.thisValue())
                .build());
    }
}

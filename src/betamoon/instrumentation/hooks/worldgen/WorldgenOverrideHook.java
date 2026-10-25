package betamoon.instrumentation.hooks.worldgen;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.CallRedirectHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.FieldRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.api.ValueBinding;

/** Native raw and cave seams for saved biome/region override packages. */
public final class WorldgenOverrideHook implements HookModule {
    private static final ClassRef PROVIDER = new ClassRef("net/minecraft/src/ChunkProviderGenerate");
    private static final String CALLBACKS = "betamoon/instrumentation/hooks/worldgen/WorldgenOverrideCallbacks";

    @Override
    public String getId() {
        return "betamoon:worldgen_override";
    }

    @Override
    public void register(HookRegistrar registrar) {
        registrar.register(AroundHookDefinition.builder(getId() + ":terrain",
                new MethodRef(PROVIDER, "generateTerrain", "(II[B[Lnet/minecraft/src/BiomeGenBase;[D)V"))
                .capture(HandlerRef.of(CALLBACKS, "entering", "()I"))
                .onReturn(HandlerRef.of(CALLBACKS, "afterTerrain",
                        "(Lnet/minecraft/src/World;II[B[Lnet/minecraft/src/BiomeGenBase;)V"),
                        ValueBinding.instanceField(new FieldRef(PROVIDER, "worldObj", "Lnet/minecraft/src/World;")),
                        ValueBinding.argument(0), ValueBinding.argument(1), ValueBinding.argument(2),
                        ValueBinding.argument(3))
                .build());
        registrar.register(new CallRedirectHookDefinition(getId() + ":caves",
                new MethodRef(new ClassRef("net/minecraft/src/IChunkProvider"), "provideChunk",
                        "(II)Lnet/minecraft/src/Chunk;").implementedBy(PROVIDER),
                new MethodRef(new ClassRef("net/minecraft/src/MapGenBase"), "func_867_a",
                        "(Lnet/minecraft/src/IChunkProvider;Lnet/minecraft/src/World;II[B)V"),
                HandlerRef.of(CALLBACKS, "carve", "(Lnet/minecraft/src/MapGenBase;"
                        + "Lnet/minecraft/src/IChunkProvider;Lnet/minecraft/src/World;II[B)V")));
    }
}

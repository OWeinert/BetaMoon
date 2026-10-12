package betamoon.instrumentation.hooks.worldgen;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.FieldRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.api.ValueBinding;

/** Appends compiled surface rules at the native overworld surface boundary. */
public final class WorldgenSurfaceHook implements HookModule {
    public static final String ID = "betamoon:worldgen_surface";
    private static final ClassRef PROVIDER = new ClassRef("net/minecraft/src/ChunkProviderGenerate");
    private static final String CALLBACKS =
            "betamoon/instrumentation/hooks/worldgen/WorldgenSurfaceCallbacks";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void register(HookRegistrar registrar) {
        registrar.register(AroundHookDefinition.builder(ID,
                new MethodRef(PROVIDER, "replaceBlocksForBiome", "(II[B[Lnet/minecraft/src/BiomeGenBase;)V"))
                .capture(HandlerRef.of(CALLBACKS, "entering", "()I"))
                .onReturn(HandlerRef.of(CALLBACKS, "after",
                        "(Lnet/minecraft/src/World;II[B[Lnet/minecraft/src/BiomeGenBase;)V"),
                        ValueBinding.instanceField(new FieldRef(PROVIDER, "worldObj", "Lnet/minecraft/src/World;")),
                        ValueBinding.argument(0), ValueBinding.argument(1), ValueBinding.argument(2),
                        ValueBinding.argument(3))
                .build());
    }
}

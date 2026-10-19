package betamoon.instrumentation.hooks.worldgen;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.api.ValueBinding;

/**
 * Sky tint and render-scoped color multiplication; fog composes at the existing
 * fluid boundary.
 */
public final class AtmosphereHook implements HookModule {
    private static final String RUNTIME = "betamoon/client/atmosphere/AtmosphereRuntime";

    @Override
    public String getId() {
        return "betamoon:atmosphere";
    }

    @Override
    public void register(HookRegistrar registrar) {
        registrar.register(AroundHookDefinition
                .builder(getId() + ":sky",
                        new MethodRef(new ClassRef("net/minecraft/src/World"), "func_4079_a",
                                "(Lnet/minecraft/src/Entity;F)Lnet/minecraft/src/Vec3D;"))
                .capture(HandlerRef.of("betamoon/instrumentation/hooks/worldgen/WorldGeneratorCallbacks", "entering",
                        "()I"))
                .onReturn(
                        HandlerRef.of(RUNTIME, "sky",
                                "(Lnet/minecraft/src/World;Lnet/minecraft/src/Vec3D;)Lnet/minecraft/src/Vec3D;"),
                        ValueBinding.thisValue(), ValueBinding.returnValue())
                .build());
        registrar
                .register(
                        AroundHookDefinition
                                .builder(getId() + ":light_tone",
                                        new MethodRef(new ClassRef("net/minecraft/src/EntityRenderer"), "renderWorld",
                                                "(FJ)V"))
                                .capture(HandlerRef.of(RUNTIME, "beginWorld", "()I"))
                                .onReturn(HandlerRef.of(RUNTIME, "endWorld", "(I)V"), ValueBinding.capturedValue())
                                .cleanupOnException().build());
    }
}

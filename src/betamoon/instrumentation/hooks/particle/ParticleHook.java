package betamoon.instrumentation.hooks.particle;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.api.ValueBinding;

/**
 * Adds custom presentation alongside the native lists without changing vanilla
 * dispatch.
 */
public final class ParticleHook implements HookModule {
    public static final String ID = "betamoon:particles";
    private static final String CALLBACKS = "betamoon/instrumentation/hooks/particle/ParticleCallbacks";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void register(HookRegistrar registrar) {
        ClassRef effects = new ClassRef("net/minecraft/src/EffectRenderer");
        registrar.register(AroundHookDefinition.builder(ID + ":tick", new MethodRef(effects, "updateEffects", "()V"))
                .capture(HandlerRef.of(CALLBACKS, "pass", "()I")).onReturn(HandlerRef.of(CALLBACKS, "tick", "()V"))
                .build());
        registrar.register(AroundHookDefinition
                .builder(ID + ":clear", new MethodRef(effects, "clearEffects", "(Lnet/minecraft/src/World;)V"))
                .capture(HandlerRef.of(CALLBACKS, "pass", "()I"))
                .onReturn(HandlerRef.of(CALLBACKS, "clear", "(Lnet/minecraft/src/World;)V"), ValueBinding.argument(0))
                .build());
        registrar.register(AroundHookDefinition
                .builder(ID + ":render", new MethodRef(effects, "renderParticles", "(Lnet/minecraft/src/Entity;F)V"))
                .capture(HandlerRef.of(CALLBACKS, "pass", "()I"))
                .onReturn(HandlerRef.of(CALLBACKS, "render", "(Lnet/minecraft/src/Entity;F)V"),
                        ValueBinding.argument(0), ValueBinding.argument(1))
                .build());
        registrar.register(AroundHookDefinition
                .builder(ID + ":spawn",
                        new MethodRef(new ClassRef("net/minecraft/src/IWorldAccess"), "spawnParticle",
                                "(Ljava/lang/String;DDDDDD)V")
                                .implementedBy(new ClassRef("net/minecraft/src/RenderGlobal")))
                .capture(HandlerRef.of(CALLBACKS, "spawn", "(Ljava/lang/String;DDDDDD)I"), ValueBinding.argument(0),
                        ValueBinding.argument(1), ValueBinding.argument(2), ValueBinding.argument(3),
                        ValueBinding.argument(4), ValueBinding.argument(5), ValueBinding.argument(6))
                .onReturn(HandlerRef.of(CALLBACKS, "afterSpawn", "()V")).skipWhenCapturedNonZero().build());
    }
}

package betamoon.instrumentation.hooks.audio;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.api.ValueBinding;

/** Routes only BetaMoon's step-sound marker through the asset audio backend. */
public final class StepSoundHook implements HookModule {
    public static final String ID = "betamoon:step_sound";
    private static final String CALLBACKS = "betamoon/instrumentation/hooks/audio/StepSoundCallbacks";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void register(HookRegistrar registrar) {
        registrar.register(AroundHookDefinition.builder(ID + ":play",
                new MethodRef(new ClassRef("net/minecraft/src/SoundManager"), "playSound",
                        "(Ljava/lang/String;FFFFF)V"))
                .capture(HandlerRef.of(CALLBACKS, "play", "(Ljava/lang/String;FFFFF)I"),
                        ValueBinding.argument(0), ValueBinding.argument(1), ValueBinding.argument(2),
                        ValueBinding.argument(3), ValueBinding.argument(4), ValueBinding.argument(5))
                .onReturn(HandlerRef.of(CALLBACKS, "afterPlay", "()V"))
                .skipWhenCapturedNonZero().build());
    }
}

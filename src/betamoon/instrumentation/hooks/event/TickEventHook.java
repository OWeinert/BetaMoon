package betamoon.instrumentation.hooks.event;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.CallRedirectHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.api.ValueBinding;

/**
 * Uses the actual logical client loop and its pause-guarded virtual world
 * update.
 */
public final class TickEventHook implements HookModule {
    public static final String ID = "betamoon:tick_events";
    private static final String CALLBACKS = "betamoon/instrumentation/hooks/event/TickEventCallbacks";

    public String getId() {
        return ID;
    }

    public void register(HookRegistrar registrar) {
        MethodRef tick = new MethodRef(new ClassRef("net/minecraft/client/Minecraft"), "runTick", "()V");
        registrar.register(AroundHookDefinition.builder(ID + ":client", tick)
                .capture(HandlerRef.of(CALLBACKS, "begin", "(Lnet/minecraft/client/Minecraft;)I"),
                        ValueBinding.thisValue())
                .onReturn(HandlerRef.of(CALLBACKS, "completed", "(Lnet/minecraft/client/Minecraft;)V"),
                        ValueBinding.thisValue())
                .build());
        registrar.register(new CallRedirectHookDefinition(ID + ":world", tick,
                new MethodRef(new ClassRef("net/minecraft/src/World"), "tick", "()V"),
                HandlerRef.of(CALLBACKS, "advanceWorld", "(Lnet/minecraft/src/World;)V")));
    }
}

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
 * Preflight before native selection marks itself selected or starts loading a
 * world.
 */
public final class WorldJoinHook implements HookModule {
    private static final String CALLBACKS = "betamoon/instrumentation/hooks/worldgen/WorldJoinCallbacks";

    @Override
    public String getId() {
        return "betamoon:world_join";
    }

    @Override
    public void register(HookRegistrar registrar) {
        ClassRef selection = new ClassRef("net/minecraft/src/GuiSelectWorld");
        ClassRef screen = new ClassRef("net/minecraft/src/GuiScreen");
        registrar.register(AroundHookDefinition
                .builder(getId() + ":preflight", new MethodRef(selection, "selectWorld", "(I)V"))
                .capture(HandlerRef.of(CALLBACKS, "preflight",
                        "(Lnet/minecraft/client/Minecraft;Lnet/minecraft/src/GuiSelectWorld;Ljava/util/List;I)I"),
                        ValueBinding.instanceField(new FieldRef(screen, "mc", "Lnet/minecraft/client/Minecraft;")),
                        ValueBinding.thisValue(),
                        ValueBinding.instanceField(new FieldRef(selection, "saveList", "Ljava/util/List;")),
                        ValueBinding.argument(0))
                .skipWhenCapturedNonZero().onReturn(HandlerRef.of(CALLBACKS, "worldJoinFinished", "()V")).build());
        registrar.register(AroundHookDefinition
                .builder(getId() + ":session",
                        new MethodRef(new ClassRef("net/minecraft/client/Minecraft"), "startWorld",
                                "(Ljava/lang/String;Ljava/lang/String;J)V"))
                .capture(HandlerRef.of(CALLBACKS, "startingWorldLoad", "()I"))
                .onReturn(HandlerRef.of(CALLBACKS, "worldJoinFinished", "()V")).build());
    }
}

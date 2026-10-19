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
 * Replaces only the native creation form, retaining its world-selection parent.
 */
public final class WorldCreationHook implements HookModule {
    @Override
    public String getId() {
        return "betamoon:world_creation";
    }

    @Override
    public void register(HookRegistrar registrar) {
        ClassRef creation = new ClassRef("net/minecraft/src/GuiCreateWorld");
        ClassRef screen = new ClassRef("net/minecraft/src/GuiScreen");
        registrar.register(AroundHookDefinition
                .builder(getId() + ":screen", new MethodRef(screen, "initGui", "()V").implementedBy(creation))
                .capture(
                        HandlerRef.of("betamoon/instrumentation/hooks/worldgen/WorldCreationCallbacks", "open",
                                "(Lnet/minecraft/client/Minecraft;Lnet/minecraft/src/GuiScreen;)I"),
                        ValueBinding.instanceField(new FieldRef(screen, "mc", "Lnet/minecraft/client/Minecraft;")),
                        ValueBinding.instanceField(
                                new FieldRef(creation, "field_22131_a", "Lnet/minecraft/src/GuiScreen;")))
                .skipWhenCapturedNonZero().onReturn(HandlerRef
                        .of("betamoon/instrumentation/hooks/worldgen/WorldCreationCallbacks", "completed", "()V"))
                .build());
    }
}

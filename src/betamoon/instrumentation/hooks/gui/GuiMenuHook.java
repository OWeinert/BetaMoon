package betamoon.instrumentation.hooks.gui;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.FieldRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.api.ValueBinding;

/**
 * Menu lifecycle and native button paint; input uses the existing
 * client-control hooks.
 */
public final class GuiMenuHook implements HookModule {
    private static final String CALLBACKS = "betamoon/instrumentation/hooks/gui/GuiMenuCallbacks";

    public String getId() {
        return "betamoon:gui_menu";
    }

    public void register(HookRegistrar registrar) {
        ClassRef gui = new ClassRef("net/minecraft/src/GuiScreen");
        registrar.register(AroundHookDefinition
                .builder(getId() + ":screen_change",
                        new MethodRef(new ClassRef("net/minecraft/client/Minecraft"), "displayGuiScreen",
                                "(Lnet/minecraft/src/GuiScreen;)V"))
                .capture(
                        HandlerRef.of(CALLBACKS, "changing",
                                "(Lnet/minecraft/client/Minecraft;Lnet/minecraft/src/GuiScreen;)I"),
                        ValueBinding.thisValue(), ValueBinding.argument(0))
                .onReturn(HandlerRef.of(CALLBACKS, "afterChange", "()V")).build());
        registrar.register(AroundHookDefinition
                .builder(getId() + ":initialize",
                        new MethodRef(gui, "setWorldAndResolution", "(Lnet/minecraft/client/Minecraft;II)V"))
                .capture(HandlerRef.of(CALLBACKS, "begin", "()I"))
                .onReturn(
                        HandlerRef.of(CALLBACKS, "initialized",
                                "(Lnet/minecraft/src/GuiScreen;Lnet/minecraft/client/Minecraft;Ljava/util/List;)V"),
                        ValueBinding.thisValue(), ValueBinding.argument(0),
                        ValueBinding.instanceField(new FieldRef(gui, "controlList", "Ljava/util/List;")))
                .build());
        registrar.register(AroundHookDefinition.builder(getId() + ":paint", new MethodRef(gui, "drawScreen", "(IIF)V"))
                .capture(HandlerRef.of(CALLBACKS, "beforePaint", "(Lnet/minecraft/src/GuiScreen;)I"),
                        ValueBinding.thisValue())
                .skipWhenCapturedNonZero()
                .onReturn(HandlerRef.of(CALLBACKS, "paint", "(Lnet/minecraft/src/GuiScreen;IIF)V"),
                        ValueBinding.thisValue(), ValueBinding.argument(0), ValueBinding.argument(1),
                        ValueBinding.argument(2))
                .build());
        registrar.register(AroundHookDefinition.builder(getId() + ":close", new MethodRef(gui, "onGuiClosed", "()V"))
                .capture(HandlerRef.of(CALLBACKS, "begin", "()I"))
                .onReturn(HandlerRef.of(CALLBACKS, "closed", "(Lnet/minecraft/src/GuiScreen;)V"),
                        ValueBinding.thisValue())
                .build());
    }
}

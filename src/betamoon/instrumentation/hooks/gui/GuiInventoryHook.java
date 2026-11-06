package betamoon.instrumentation.hooks.gui;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.CallRedirectHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.api.ValueBinding;

/** Presentation-only hooks for the exact native player inventory adapter. */
public final class GuiInventoryHook implements HookModule {
    private static final String CALLBACKS = "betamoon/instrumentation/hooks/gui/GuiInventoryCallbacks";

    public String getId() {
        return "betamoon:gui_inventory";
    }

    public void register(HookRegistrar registrar) {
        ClassRef gui = new ClassRef("net/minecraft/src/GuiScreen");
        ClassRef container = new ClassRef("net/minecraft/src/GuiContainer");
        ClassRef inventory = new ClassRef("net/minecraft/src/GuiInventory");
        MethodRef draw = new MethodRef(gui, "drawScreen", "(IIF)V").implementedBy(container);
        registrar.register(AroundHookDefinition.builder(getId() + ":draw", draw)
                .capture(HandlerRef.of(CALLBACKS, "beforeDraw", "(Lnet/minecraft/src/GuiContainer;IIF)I"),
                        ValueBinding.thisValue(), ValueBinding.argument(0), ValueBinding.argument(1),
                        ValueBinding.argument(2))
                .onReturn(HandlerRef.of(CALLBACKS, "afterDraw", "(Lnet/minecraft/src/GuiContainer;)V"),
                        ValueBinding.thisValue())
                .cleanupOnException(HandlerRef.of(CALLBACKS, "failedDraw", "(Lnet/minecraft/src/GuiContainer;)V"))
                .build());
        layer(registrar, container, inventory, "drawGuiContainerBackgroundLayer", "(F)V", "background");
        layer(registrar, container, inventory, "drawGuiContainerForegroundLayer", "()V", "foreground");
        registrar
                .register(AroundHookDefinition
                        .builder(getId() + ":close", new MethodRef(gui, "onGuiClosed", "()V").implementedBy(container))
                        .capture(HandlerRef.of(CALLBACKS, "closing", "(Lnet/minecraft/src/GuiContainer;)I"),
                                ValueBinding.thisValue())
                        .onReturn(HandlerRef.of(CALLBACKS, "afterLayer", "()V")).build());
        registrar.register(new CallRedirectHookDefinition(getId() + ":cursor", draw,
                new MethodRef(new ClassRef("net/minecraft/src/InventoryPlayer"), "getItemStack",
                        "()Lnet/minecraft/src/ItemStack;"),
                HandlerRef.of(CALLBACKS, "cursor",
                        "(Lnet/minecraft/src/InventoryPlayer;)Lnet/minecraft/src/ItemStack;"))
                .allCallsInTarget());
        registrar.register(AroundHookDefinition
                .builder(getId() + ":hover",
                        new MethodRef(container, "getIsMouseOverSlot", "(Lnet/minecraft/src/Slot;II)Z"))
                .capture(HandlerRef.of(CALLBACKS, "beforeHover", "(Lnet/minecraft/src/GuiContainer;)I"),
                        ValueBinding.thisValue())
                .skipWhenCapturedNonZero().onReturn(HandlerRef.of(CALLBACKS, "afterHover", "(ZI)Z"),
                        ValueBinding.returnValue(), ValueBinding.capturedValue())
                .build());
    }

    private void layer(HookRegistrar registrar, ClassRef container, ClassRef inventory, String method,
            String descriptor, String callback) {
        registrar.register(AroundHookDefinition
                .builder(getId() + ":" + callback,
                        new MethodRef(container, method, descriptor).implementedBy(inventory))
                .capture(HandlerRef.of(CALLBACKS, callback, "(Lnet/minecraft/src/GuiInventory;)I"),
                        ValueBinding.thisValue())
                .skipWhenCapturedNonZero().onReturn(HandlerRef.of(CALLBACKS, "afterLayer", "()V")).build());
    }
}

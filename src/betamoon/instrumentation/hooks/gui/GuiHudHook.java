package betamoon.instrumentation.hooks.gui;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.CallRedirectHookDefinition;
import betamoon.instrumentation.api.CallRedirectShape;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.FieldReadRedirectHookDefinition;
import betamoon.instrumentation.api.FieldRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.api.ValueBinding;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;

/**
 * Exact semantic native HUD shapes, plus a client-wide worldless-capable render
 * boundary.
 */
public final class GuiHudHook implements HookModule {
    private static final String CALLBACKS = "betamoon/instrumentation/hooks/gui/GuiHudCallbacks";
    public static final String TEXTURES = "betamoon:gui_hud:textures";

    public String getId() {
        return "betamoon:gui_hud";
    }

    public void register(HookRegistrar registrar) {
        ClassRef hud = new ClassRef("net/minecraft/src/GuiIngame");
        ClassRef gui = new ClassRef("net/minecraft/src/Gui");
        ClassRef renderer = new ClassRef("net/minecraft/src/EntityRenderer");
        ClassRef player = new ClassRef("net/minecraft/src/EntityPlayerSP");
        FieldRef client = new FieldRef(hud, "mc", "Lnet/minecraft/client/Minecraft;");
        MethodRef render = new MethodRef(hud, "renderGameOverlay", "(FZII)V");
        registrar.register(AroundHookDefinition.builder(getId() + ":native_frame", render)
                .capture(
                        HandlerRef.of(CALLBACKS, "beforeNative",
                                "(Lnet/minecraft/src/GuiIngame;Lnet/minecraft/client/Minecraft;FII)I"),
                        ValueBinding.thisValue(), ValueBinding.instanceField(client), ValueBinding.argument(0),
                        ValueBinding.argument(2), ValueBinding.argument(3))
                .onReturn(HandlerRef.of(CALLBACKS, "afterNative", "()V"))
                .cleanupOnException(HandlerRef.of(CALLBACKS, "nativeFailed", "()V")).build());
        registrar.register(new CallRedirectHookDefinition(TEXTURES, render,
                new MethodRef(gui, "drawTexturedModalRect", "(IIIIII)V"),
                HandlerRef.of(CALLBACKS, "texture", "(Lnet/minecraft/src/GuiIngame;IIIIII)V")).withInvocationOwner(hud)
                .allCallsInTarget().withShapes(atlasShapes()));
        registrar.register(new CallRedirectHookDefinition(getId() + ":armor", render,
                new MethodRef(player, "getPlayerArmorValue", "()I"),
                HandlerRef.of(CALLBACKS, "armor", "(Lnet/minecraft/src/EntityPlayerSP;)I"))
                .withInvocationOwner(player));
        registrar.register(AroundHookDefinition
                .builder(getId() + ":hotbar_items", new MethodRef(hud, "renderInventorySlot", "(IIIF)V"))
                .capture(HandlerRef.of(CALLBACKS, "beforeHotbar", "()I")).skipWhenCapturedNonZero()
                .onReturn(HandlerRef.of(CALLBACKS, "afterHotbar", "()V")).build());
        registrar.register(new FieldReadRedirectHookDefinition(getId() + ":chat", render,
                new FieldRef(hud, "chatMessageList", "Ljava/util/List;"),
                HandlerRef.of(CALLBACKS, "chat", "(Ljava/util/List;)Ljava/util/List;")).filterReads(4));
        registrar.register(AroundHookDefinition
                .builder(getId() + ":client_frame", new MethodRef(renderer, "updateCameraAndRender", "(F)V"))
                .capture(HandlerRef.of(CALLBACKS, "beginClientFrame", "()I"))
                .onReturn(HandlerRef.of(CALLBACKS, "clientFrame", "(Lnet/minecraft/client/Minecraft;F)V"),
                        ValueBinding.instanceField(new FieldRef(renderer, "mc", "Lnet/minecraft/client/Minecraft;")),
                        ValueBinding.argument(0))
                .build());
    }

    /**
     * Every native UV/dimension tail is checked together; unrelated atlas shapes
     * disable only this redirect.
     */
    private static CallRedirectShape[] atlasShapes() {
        List<CallRedirectShape> shapes = new ArrayList<>();
        shapes.add(rectangle(0, 0, 182, 22));
        shapes.add(rectangle(0, 22, 24, 22));
        shapes.add(rectangle(0, 0, 16, 16));
        for (int u : new int[]{34, 25, 16}) {
            shapes.add(rectangle(u, 9, 9, 9));
        }
        shapes.add(new CallRedirectShape(1, CallRedirectShape.integer(16), CallRedirectShape.localInteger(),
                CallRedirectShape.integer(9), CallRedirectShape.opcode(Opcodes.IMUL),
                CallRedirectShape.opcode(Opcodes.IADD), CallRedirectShape.integer(0), CallRedirectShape.integer(9),
                CallRedirectShape.integer(9)));
        for (int u : new int[]{70, 79, 52, 61}) {
            shapes.add(rectangle(u, 0, 9, 9));
        }
        shapes.add(rectangle(16, 18, 9, 9));
        shapes.add(rectangle(25, 18, 9, 9));
        return shapes.toArray(new CallRedirectShape[0]);
    }

    private static CallRedirectShape rectangle(int u, int v, int width, int height) {
        return new CallRedirectShape(1, CallRedirectShape.integer(u), CallRedirectShape.integer(v),
                CallRedirectShape.integer(width), CallRedirectShape.integer(height));
    }
}

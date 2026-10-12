package betamoon.instrumentation.hooks.control;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.CallRedirectHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.api.ValueBinding;

/**
 * Verified client seams for the input, controller, targeting, and camera stack.
 */
public final class ClientControlHook implements HookModule {
    public static final String ID = "betamoon:client_control";
    private static final String CALLBACKS = "betamoon/instrumentation/hooks/control/ClientControlCallbacks";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void register(HookRegistrar registrar) {
        registerInputTick(registrar);
        registerQueuedInput(registrar);
        registerGuiConsumption(registrar);
        registerKeyForwarding(registrar);
        registerMovementIntent(registrar);
        registerLookApplication(registrar);
        registerActions(registrar);
        registerTargeting(registrar);
        registerCamera(registrar);
    }

    private static void registerGuiConsumption(HookRegistrar registrar) {
        ClassRef gui = new ClassRef("net/minecraft/src/GuiScreen");
        registrar.register(AroundHookDefinition
                .builder(ID + ":gui_keyboard_consume", new MethodRef(gui, "handleKeyboardInput", "()V"))
                .capture(HandlerRef.of(CALLBACKS, "beforeGuiKeyboardInput", "(Lnet/minecraft/src/GuiScreen;)I"),
                        ValueBinding.thisValue())
                .onReturn(HandlerRef.of(CALLBACKS, "afterGuiKeyboardInput", "(Lnet/minecraft/src/GuiScreen;I)V"),
                        ValueBinding.thisValue(), ValueBinding.capturedValue())
                .skipWhenCapturedNonZero().build());
        registrar.register(AroundHookDefinition
                .builder(ID + ":gui_mouse_consume", new MethodRef(gui, "handleMouseInput", "()V"))
                .capture(HandlerRef.of(CALLBACKS, "beforeGuiMouseInput", "(Lnet/minecraft/src/GuiScreen;)I"),
                        ValueBinding.thisValue())
                .onReturn(HandlerRef.of(CALLBACKS, "afterGuiMouseInput", "(Lnet/minecraft/src/GuiScreen;I)V"),
                        ValueBinding.thisValue(), ValueBinding.capturedValue())
                .skipWhenCapturedNonZero().build());
    }

    private static void registerQueuedInput(HookRegistrar registrar) {
        ClassRef minecraft = new ClassRef("net/minecraft/client/Minecraft");
        MethodRef runTick = new MethodRef(minecraft, "runTick", "()V");
        ClassRef gui = new ClassRef("net/minecraft/src/GuiScreen");
        MethodRef guiInput = new MethodRef(gui, "handleInput", "()V");
        registerStaticRedirect(registrar, ID + ":keyboard_next", runTick, "org/lwjgl/input/Keyboard", "next", "()Z",
                "nextKeyboardEvent", "()Z", false);
        registerStaticRedirect(registrar, ID + ":mouse_next", runTick, "org/lwjgl/input/Mouse", "next", "()Z",
                "nextMouseEvent", "()Z", false);
        registerStaticRedirect(registrar, ID + ":keyboard_state", runTick, "org/lwjgl/input/Keyboard",
                "getEventKeyState", "()Z", "keyboardEventState", "()Z", true);
        registerStaticRedirect(registrar, ID + ":mouse_state", runTick, "org/lwjgl/input/Mouse", "getEventButtonState",
                "()Z", "mouseEventButtonState", "()Z", true);
        registerStaticRedirect(registrar, ID + ":mouse_wheel", runTick, "org/lwjgl/input/Mouse", "getEventDWheel",
                "()I", "mouseEventWheel", "()I", false);

        registerStaticRedirect(registrar, ID + ":gui_keyboard_next", guiInput, "org/lwjgl/input/Keyboard", "next",
                "()Z", "nextKeyboardEvent", "()Z", false);
        registerStaticRedirect(registrar, ID + ":gui_mouse_next", guiInput, "org/lwjgl/input/Mouse", "next", "()Z",
                "nextMouseEvent", "()Z", false);
        registerStaticRedirect(registrar, ID + ":gui_keyboard_state", new MethodRef(gui, "handleKeyboardInput", "()V"),
                "org/lwjgl/input/Keyboard", "getEventKeyState", "()Z", "keyboardEventState", "()Z", true);
        registerStaticRedirect(registrar, ID + ":gui_mouse_state", new MethodRef(gui, "handleMouseInput", "()V"),
                "org/lwjgl/input/Mouse", "getEventButtonState", "()Z", "mouseEventButtonState", "()Z", true);
    }

    private static void registerStaticRedirect(HookRegistrar registrar, String id, MethodRef target,
            String invocationOwner, String invocationName, String invocationDescriptor, String callbackName,
            String callbackDescriptor, boolean allCalls) {
        CallRedirectHookDefinition definition = new CallRedirectHookDefinition(id, target,
                new MethodRef(new ClassRef(invocationOwner), invocationName, invocationDescriptor),
                HandlerRef.of(CALLBACKS, callbackName, callbackDescriptor)).staticInvocation();
        registrar.register(allCalls ? definition.allCallsInTarget() : definition);
    }

    private static void registerInputTick(HookRegistrar registrar) {
        registrar.register(AroundHookDefinition
                .builder(ID + ":input_tick",
                        new MethodRef(new ClassRef("net/minecraft/client/Minecraft"), "runTick", "()V"))
                .capture(HandlerRef.of(CALLBACKS, "beginInputTick", "(Lnet/minecraft/client/Minecraft;)I"),
                        ValueBinding.thisValue())
                .onReturn(HandlerRef.of(CALLBACKS, "finishInputTick", "(Lnet/minecraft/client/Minecraft;)V"),
                        ValueBinding.thisValue())
                .build());
    }

    private static void registerKeyForwarding(HookRegistrar registrar) {
        registrar
                .register(AroundHookDefinition
                        .builder(ID + ":movement_key",
                                new MethodRef(new ClassRef("net/minecraft/src/EntityPlayerSP"), "handleKeyPress",
                                        "(IZ)V"))
                        .capture(
                                HandlerRef.of(CALLBACKS, "beforeMovementKey",
                                        "(Lnet/minecraft/src/EntityPlayerSP;IZ)I"),
                                ValueBinding.thisValue(), ValueBinding.argument(0), ValueBinding.argument(1))
                        .onReturn(
                                HandlerRef.of(CALLBACKS, "afterMovementKey",
                                        "(Lnet/minecraft/src/EntityPlayerSP;IZI)V"),
                                ValueBinding.thisValue(), ValueBinding.argument(0), ValueBinding.argument(1),
                                ValueBinding.capturedValue())
                        .skipWhenCapturedNonZero().build());
    }

    private static void registerMovementIntent(HookRegistrar registrar) {
        MethodRef updateMovement = new MethodRef(new ClassRef("net/minecraft/src/MovementInput"),
                "updatePlayerMoveState", "(Lnet/minecraft/src/EntityPlayer;)V")
                .implementedBy(new ClassRef("net/minecraft/src/MovementInputFromOptions"));
        registrar
                .register(AroundHookDefinition.builder(ID + ":movement_intent", updateMovement)
                        .capture(HandlerRef.of(CALLBACKS, "enterMovementIntent", "()I"))
                        .onReturn(HandlerRef.of(CALLBACKS, "finishMovementIntent",
                                "(Lnet/minecraft/src/MovementInputFromOptions;Lnet/minecraft/src/EntityPlayer;)V"),
                                ValueBinding.thisValue(), ValueBinding.argument(0))
                        .build());
    }

    private static void registerLookApplication(HookRegistrar registrar) {
        ClassRef player = new ClassRef("net/minecraft/src/EntityPlayerSP");
        registrar
                .register(new CallRedirectHookDefinition(ID + ":look",
                        new MethodRef(new ClassRef("net/minecraft/src/EntityRenderer"), "updateCameraAndRender",
                                "(F)V"),
                        new MethodRef(new ClassRef("net/minecraft/src/Entity"), "func_346_d", "(FF)V"),
                        HandlerRef.of(CALLBACKS, "applyLook", "(Lnet/minecraft/src/EntityPlayerSP;FF)V"))
                        .withInvocationOwner(player));
    }

    private static void registerActions(HookRegistrar registrar) {
        ClassRef minecraft = new ClassRef("net/minecraft/client/Minecraft");
        registrar.register(AroundHookDefinition.builder(ID + ":action", new MethodRef(minecraft, "clickMouse", "(I)V"))
                .capture(HandlerRef.of(CALLBACKS, "beforeAction", "(Lnet/minecraft/client/Minecraft;I)I"),
                        ValueBinding.thisValue(), ValueBinding.argument(0))
                .onReturn(HandlerRef.of(CALLBACKS, "afterAction", "(Lnet/minecraft/client/Minecraft;II)V"),
                        ValueBinding.thisValue(), ValueBinding.argument(0), ValueBinding.capturedValue())
                .skipWhenCapturedNonZero().build());
        registrar.register(AroundHookDefinition
                .builder(ID + ":held_breaking", new MethodRef(minecraft, "func_6254_a", "(IZ)V"))
                .capture(HandlerRef.of(CALLBACKS, "beforeHeldBreaking", "(Lnet/minecraft/client/Minecraft;IZ)I"),
                        ValueBinding.thisValue(), ValueBinding.argument(0), ValueBinding.argument(1))
                .onReturn(HandlerRef.of(CALLBACKS, "afterHeldBreaking", "(Lnet/minecraft/client/Minecraft;IZI)V"),
                        ValueBinding.thisValue(), ValueBinding.argument(0), ValueBinding.argument(1),
                        ValueBinding.capturedValue())
                .skipWhenCapturedNonZero().build());
    }

    private static void registerTargeting(HookRegistrar registrar) {
        registrar
                .register(
                        AroundHookDefinition
                                .builder(ID + ":targeting",
                                        new MethodRef(new ClassRef("net/minecraft/src/EntityRenderer"), "getMouseOver",
                                                "(F)V"))
                                .capture(
                                        HandlerRef.of(CALLBACKS, "beforeTargeting",
                                                "(Lnet/minecraft/src/EntityRenderer;F)I"),
                                        ValueBinding.thisValue(), ValueBinding.argument(0))
                                .onReturn(
                                        HandlerRef.of(CALLBACKS, "afterTargeting",
                                                "(Lnet/minecraft/src/EntityRenderer;FI)V"),
                                        ValueBinding.thisValue(), ValueBinding.argument(0),
                                        ValueBinding.capturedValue())
                                .skipWhenCapturedNonZero().build());
    }

    private static void registerCamera(HookRegistrar registrar) {
        ClassRef renderer = new ClassRef("net/minecraft/src/EntityRenderer");
        registrar.register(AroundHookDefinition
                .builder(ID + ":camera_orientation", new MethodRef(renderer, "orientCamera", "(F)V"))
                .capture(HandlerRef.of(CALLBACKS, "beforeCamera", "(Lnet/minecraft/src/EntityRenderer;F)I"),
                        ValueBinding.thisValue(), ValueBinding.argument(0))
                .onReturn(HandlerRef.of(CALLBACKS, "afterCamera", "(Lnet/minecraft/src/EntityRenderer;FI)V"),
                        ValueBinding.thisValue(), ValueBinding.argument(0), ValueBinding.capturedValue())
                .skipWhenCapturedNonZero().build());
        registrar.register(AroundHookDefinition
                .builder(ID + ":camera_projection", new MethodRef(renderer, "setupCameraTransform", "(FI)V"))
                .capture(HandlerRef.of(CALLBACKS, "beforeProjection", "(Lnet/minecraft/src/EntityRenderer;FI)I"),
                        ValueBinding.thisValue(), ValueBinding.argument(0), ValueBinding.argument(1))
                .onReturn(HandlerRef.of(CALLBACKS, "afterProjection", "(Lnet/minecraft/src/EntityRenderer;FII)V"),
                        ValueBinding.thisValue(), ValueBinding.argument(0), ValueBinding.argument(1),
                        ValueBinding.capturedValue())
                .build());
    }
}

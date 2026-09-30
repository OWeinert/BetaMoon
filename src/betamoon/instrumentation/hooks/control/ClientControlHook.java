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
        registerKeyForwarding(registrar);
        registerMovementIntent(registrar);
        registerLookApplication(registrar);
        registerActions(registrar);
        registerTargeting(registrar);
        registerCamera(registrar);
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

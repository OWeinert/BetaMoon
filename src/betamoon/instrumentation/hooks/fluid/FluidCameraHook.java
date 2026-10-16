package betamoon.instrumentation.hooks.fluid;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.CallRedirectHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.FieldRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.api.ValueBinding;

/** Client eye-fluid fog, overlay, and air-display seams. */
public final class FluidCameraHook implements HookModule {
    public static final String ID = "betamoon:fluid_camera";
    private static final String CALLBACKS = "betamoon/instrumentation/hooks/fluid/FluidCameraCallbacks";
    private static final ClassRef ENTITY = new ClassRef("net/minecraft/src/Entity");
    private static final ClassRef LIVING = new ClassRef("net/minecraft/src/EntityLiving");
    private static final ClassRef PLAYER = new ClassRef("net/minecraft/src/EntityPlayerSP");

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void register(HookRegistrar registrar) {
        registrar.register(AroundHookDefinition.builder(ID + ":wet", new MethodRef(ENTITY, "isWet", "()Z"))
                .capture(HandlerRef.of("betamoon/instrumentation/hooks/fluid/FluidContactCallbacks", "pass", "()I"))
                .onReturn(HandlerRef.of("betamoon/instrumentation/hooks/fluid/FluidContactCallbacks", "wet",
                        "(Lnet/minecraft/src/Entity;Z)Z"), ValueBinding.thisValue(), ValueBinding.returnValue())
                .build());
        ClassRef renderer = new ClassRef("net/minecraft/src/EntityRenderer");
        for (String method : new String[]{"updateFogColor", "setupFog"}) {
            MethodRef target = new MethodRef(renderer, method, method.equals("updateFogColor") ? "(F)V" : "(IF)V");
            registrar.register(new CallRedirectHookDefinition(ID + ":" + method + "_material", target,
                    new MethodRef(ENTITY, "isInsideOfMaterial", "(Lnet/minecraft/src/Material;)Z"),
                    HandlerRef.of(CALLBACKS, "nativeCameraMaterial",
                            "(Lnet/minecraft/src/EntityLiving;Lnet/minecraft/src/Material;)Z"))
                    .withInvocationOwner(LIVING).allCallsInTarget());
            registrar.register(AroundHookDefinition.builder(ID + ":" + method, target)
                    .capture(HandlerRef.of(CALLBACKS, "pass", "()I"))
                    .onReturn(HandlerRef.of(CALLBACKS, method.equals("updateFogColor") ? "fogColor" : "fog", "(Z)V"),
                            ValueBinding.instanceField(new FieldRef(renderer, "cloudFog", "Z")))
                    .build());
        }

        ClassRef itemRenderer = new ClassRef("net/minecraft/src/ItemRenderer");
        MethodRef overlay = new MethodRef(itemRenderer, "renderOverlays", "(F)V");
        registrar.register(new CallRedirectHookDefinition(ID + ":overlay_visibility", overlay,
                new MethodRef(ENTITY, "isInsideOfMaterial", "(Lnet/minecraft/src/Material;)Z"),
                HandlerRef.of(CALLBACKS, "overlayVisible",
                        "(Lnet/minecraft/src/EntityPlayerSP;Lnet/minecraft/src/Material;)Z"))
                .withInvocationOwner(PLAYER));
        registrar.register(new CallRedirectHookDefinition(ID + ":overlay_texture", overlay,
                new MethodRef(new ClassRef("net/minecraft/src/RenderEngine"), "getTexture", "(Ljava/lang/String;)I"),
                HandlerRef.of(CALLBACKS, "overlayTexture", "(Lnet/minecraft/src/RenderEngine;Ljava/lang/String;)I"))
                .allCallsInTarget());

        registrar.register(new CallRedirectHookDefinition(ID + ":air_hud",
                new MethodRef(new ClassRef("net/minecraft/src/GuiIngame"), "renderGameOverlay", "(FZII)V"),
                new MethodRef(ENTITY, "isInsideOfMaterial", "(Lnet/minecraft/src/Material;)Z"),
                HandlerRef.of(CALLBACKS, "showAirHud",
                        "(Lnet/minecraft/src/EntityPlayerSP;Lnet/minecraft/src/Material;)Z"))
                .withInvocationOwner(PLAYER));
    }
}

package betamoon.instrumentation.hooks.model;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.api.ValueBinding;

/**
 * Preserves native drawing for every item without an explicit model appearance.
 */
public final class ModelRenderHook implements HookModule {
    public static final String ID = "betamoon:model_render";
    private static final String CALLBACKS = "betamoon/instrumentation/hooks/model/ModelRenderCallbacks";

    public String getId() {
        return ID;
    }

    public void register(HookRegistrar registrar) {
        for (String operation : new String[]{"loaded", "unloaded"}) {
            registrar.register(AroundHookDefinition
                    .builder(ID + ":chunk_" + operation,
                            new MethodRef(new ClassRef("net/minecraft/src/Chunk"),
                                    operation.equals("loaded") ? "onChunkLoad" : "onChunkUnload", "()V"))
                    .capture(HandlerRef.of(CALLBACKS, "entering", "()I"))
                    .onReturn(HandlerRef.of(CALLBACKS, operation.equals("loaded") ? "chunkLoaded" : "chunkUnloaded",
                            "(Lnet/minecraft/src/Chunk;)V"), ValueBinding.thisValue())
                    .build());
        }
        for (String method : new String[]{"setBlockID", "setBlockIDWithMetadata"}) {
            registrar.register(AroundHookDefinition
                    .builder(ID + ":" + method,
                            new MethodRef(new ClassRef("net/minecraft/src/Chunk"), method,
                                    method.equals("setBlockID") ? "(IIII)Z" : "(IIIII)Z"))
                    .capture(HandlerRef.of(CALLBACKS, "entering", "()I"))
                    .onReturn(HandlerRef.of(CALLBACKS, "chunkChanged", "(Lnet/minecraft/src/Chunk;IIIZ)V"),
                            ValueBinding.thisValue(), ValueBinding.argument(0), ValueBinding.argument(1),
                            ValueBinding.argument(2), ValueBinding.returnValue())
                    .build());
        }
        registrar.register(AroundHookDefinition
                .builder(ID + ":world",
                        new MethodRef(new ClassRef("net/minecraft/src/RenderGlobal"), "renderEntities",
                                "(Lnet/minecraft/src/Vec3D;Lnet/minecraft/src/ICamera;F)V"))
                .capture(HandlerRef.of(CALLBACKS, "entering", "()I"))
                .onReturn(
                        HandlerRef.of(CALLBACKS, "worldModels",
                                "(Lnet/minecraft/src/RenderGlobal;Lnet/minecraft/src/Vec3D;F)V"),
                        ValueBinding.thisValue(), ValueBinding.argument(0), ValueBinding.argument(2))
                .build());
        registrar.register(AroundHookDefinition
                .builder(ID + ":held",
                        new MethodRef(new ClassRef("net/minecraft/src/ItemRenderer"), "renderItem",
                                "(Lnet/minecraft/src/EntityLiving;Lnet/minecraft/src/ItemStack;)V"))
                .capture(
                        HandlerRef.of(CALLBACKS, "held",
                                "(Lnet/minecraft/src/EntityLiving;Lnet/minecraft/src/ItemStack;)I"),
                        ValueBinding.argument(0), ValueBinding.argument(1))
                .onReturn(HandlerRef.of(CALLBACKS, "complete", "()V")).skipWhenCapturedNonZero().build());
        registrar.register(AroundHookDefinition
                .builder(ID + ":gui",
                        new MethodRef(new ClassRef("net/minecraft/src/RenderItem"), "drawItemIntoGui",
                                "(Lnet/minecraft/src/FontRenderer;Lnet/minecraft/src/RenderEngine;IIIII)V"))
                .capture(HandlerRef.of(CALLBACKS, "gui", "(IIII)I"), ValueBinding.argument(2), ValueBinding.argument(3),
                        ValueBinding.argument(5), ValueBinding.argument(6))
                .onReturn(HandlerRef.of(CALLBACKS, "complete", "()V")).skipWhenCapturedNonZero().build());
        registrar.register(AroundHookDefinition
                .builder(ID + ":ground",
                        new MethodRef(new ClassRef("net/minecraft/src/RenderItem"), "doRenderItem",
                                "(Lnet/minecraft/src/EntityItem;DDDFF)V"))
                .capture(HandlerRef.of(CALLBACKS, "ground", "(Lnet/minecraft/src/EntityItem;DDDFF)I"),
                        ValueBinding.argument(0), ValueBinding.argument(1), ValueBinding.argument(2),
                        ValueBinding.argument(3), ValueBinding.argument(4), ValueBinding.argument(5))
                .onReturn(HandlerRef.of(CALLBACKS, "complete", "()V")).skipWhenCapturedNonZero().build());
        registrar.register(AroundHookDefinition
                .builder(ID + ":frame",
                        new MethodRef(new ClassRef("net/minecraft/src/EntityRenderer"), "updateCameraAndRender",
                                "(F)V"))
                .capture(HandlerRef.of(CALLBACKS, "frame", "(F)I"), ValueBinding.argument(0))
                .onReturn(HandlerRef.of(CALLBACKS, "complete", "()V")).build());
    }
}

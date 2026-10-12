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

/** Native entity seams for registered-fluid contact policies. */
public final class FluidContactHook implements HookModule {
    public static final String ID = "betamoon:fluid_contact";
    private static final String CALLBACKS = "betamoon/instrumentation/hooks/fluid/FluidContactCallbacks";
    private static final ClassRef ENTITY = new ClassRef("net/minecraft/src/Entity");

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void register(HookRegistrar registrar) {
        movement(registrar, "handleWaterMovement", "water");
        movement(registrar, "handleLavaMovement", "lava");
        ClassRef item = new ClassRef("net/minecraft/src/EntityItem");
        registrar.register(AroundHookDefinition.builder(ID + ":item_water_movement",
                new MethodRef(ENTITY, "handleWaterMovement", "()Z").implementedBy(item))
                .capture(HandlerRef.of(CALLBACKS, "customItemBody", "(Lnet/minecraft/src/EntityItem;)I"),
                        ValueBinding.thisValue())
                .onReturn(HandlerRef.of(CALLBACKS, "itemWaterMovement", "(Lnet/minecraft/src/EntityItem;ZI)Z"),
                        ValueBinding.thisValue(), ValueBinding.returnValue(), ValueBinding.capturedValue())
                .skipWhenCapturedNonZero().build());
        registrar.register(AroundHookDefinition.builder(ID + ":eye_material",
                new MethodRef(ENTITY, "isInsideOfMaterial", "(Lnet/minecraft/src/Material;)Z"))
                .capture(HandlerRef.of(CALLBACKS, "customEye", "(Lnet/minecraft/src/Entity;)I"),
                        ValueBinding.thisValue())
                .onReturn(HandlerRef.of(CALLBACKS, "eyeMaterial",
                                "(Lnet/minecraft/src/Entity;Lnet/minecraft/src/Material;ZI)Z"),
                        ValueBinding.thisValue(), ValueBinding.argument(0), ValueBinding.returnValue(),
                        ValueBinding.capturedValue())
                .skipWhenCapturedNonZero().build());
        registrar.register(AroundHookDefinition.builder(ID + ":entity_tick",
                new MethodRef(ENTITY, "onEntityUpdate", "()V"))
                .capture(HandlerRef.of(CALLBACKS, "beforeEntityTick", "(Lnet/minecraft/src/Entity;)I"),
                        ValueBinding.thisValue())
                .onReturn(HandlerRef.of(CALLBACKS, "afterEntityTick", "(Lnet/minecraft/src/Entity;IZ)V"),
                        ValueBinding.thisValue(), ValueBinding.capturedValue(),
                        ValueBinding.instanceField(new FieldRef(ENTITY, "isImmuneToFire", "Z"))).build());
        registrar.register(AroundHookDefinition.builder(ID + ":native_lava_damage",
                new MethodRef(ENTITY, "setOnFireFromLava", "()V"))
                .capture(HandlerRef.of(CALLBACKS, "skipNativeLavaDamage", "(Lnet/minecraft/src/Entity;)I"),
                        ValueBinding.thisValue())
                .onReturn(HandlerRef.of(CALLBACKS, "complete", "()V"))
                .skipWhenCapturedNonZero().build());

        ClassRef living = new ClassRef("net/minecraft/src/EntityLiving");
        registrar.register(AroundHookDefinition.builder(ID + ":living_tick",
                new MethodRef(ENTITY, "onEntityUpdate", "()V").implementedBy(living))
                .capture(HandlerRef.of(CALLBACKS, "beforeLivingTick", "(Lnet/minecraft/src/EntityLiving;)I"),
                        ValueBinding.thisValue())
                .onReturn(HandlerRef.of(CALLBACKS, "afterLivingTick", "(Lnet/minecraft/src/EntityLiving;IZ)V"),
                        ValueBinding.thisValue(), ValueBinding.capturedValue(),
                        ValueBinding.instanceField(new FieldRef(ENTITY, "isImmuneToFire", "Z"))).build());
        registrar.register(new CallRedirectHookDefinition(ID + ":breathing",
                new MethodRef(ENTITY, "onEntityUpdate", "()V").implementedBy(living),
                new MethodRef(ENTITY, "isInsideOfMaterial", "(Lnet/minecraft/src/Material;)Z"),
                HandlerRef.of(CALLBACKS, "drowning",
                        "(Lnet/minecraft/src/EntityLiving;Lnet/minecraft/src/Material;)Z"))
                .withInvocationOwner(living));
    }

    private static void movement(HookRegistrar registrar, String method, String name) {
        registrar.register(AroundHookDefinition.builder(ID + ":" + name + "_movement",
                new MethodRef(ENTITY, method, "()Z"))
                .capture(HandlerRef.of(CALLBACKS, "customBody", "(Lnet/minecraft/src/Entity;)I"),
                        ValueBinding.thisValue())
                .onReturn(HandlerRef.of(CALLBACKS, name + "Movement", "(Lnet/minecraft/src/Entity;ZI)Z"),
                        ValueBinding.thisValue(), ValueBinding.returnValue(), ValueBinding.capturedValue())
                .skipWhenCapturedNonZero().build());
    }
}

package betamoon.instrumentation.hooks.entity;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.CallRedirectHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.FieldReadRedirectHookDefinition;
import betamoon.instrumentation.api.FieldRef;
import betamoon.instrumentation.api.FieldValueTransformHookDefinition;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.api.ValueBinding;
import org.objectweb.asm.Opcodes;

/**
 * Native living integration; all runtime types stay unloaded during agent
 * planning.
 */
public final class EntityMotionHook implements HookModule {
    public static final String ID = "betamoon:entity_motion";
    public static final String[] CORE_IDS = {ID + ":living", ID + ":intent", ID + ":heading", ID + ":gravity",
            ID + ":drag_y", ID + ":drag_x", ID + ":drag_z", ID + ":acceleration", ID + ":jump", ID + ":fall_tracking",
            ID + ":fall_context", ID + ":fall_damage", ID + ":edge_guard", ID + ":collision"};
    public static final String[] PLAYER_IDS = {ID + ":player", ID + ":player_input", ID + ":body_adjustment"};
    private static final String CALLBACKS = "betamoon/instrumentation/hooks/entity/EntityMotionCallbacks";
    private static final ClassRef ENTITY = new ClassRef("net/minecraft/src/Entity");
    private static final ClassRef LIVING = new ClassRef("net/minecraft/src/EntityLiving");
    private static final ClassRef PLAYER = new ClassRef("net/minecraft/src/EntityPlayerSP");
    private static final ClassRef INPUT = new ClassRef("net/minecraft/src/MovementInput");

    public String getId() {
        return ID;
    }

    public void register(HookRegistrar registrar) {
        MethodRef update = new MethodRef(LIVING, "onLivingUpdate", "()V");
        registrar.register(AroundHookDefinition.builder(ID + ":living", update)
                .capture(handler("beginLiving", "(Lnet/minecraft/src/EntityLiving;)I"), ValueBinding.thisValue())
                .onReturn(handler("finishLiving", "(Lnet/minecraft/src/EntityLiving;I)V"), ValueBinding.thisValue(),
                        ValueBinding.capturedValue())
                .cleanupOnException(handler("abortLiving", "(Lnet/minecraft/src/EntityLiving;I)V")).build());
        registrar.register(new FieldReadRedirectHookDefinition(ID + ":intent", update,
                new FieldRef(LIVING, "isJumping", "Z"), handler("jumpIntent", "(Lnet/minecraft/src/EntityLiving;)Z")));
        MethodRef heading = new MethodRef(LIVING, "moveEntityWithHeading", "(FF)V");
        registrar.register(AroundHookDefinition.builder(ID + ":heading", heading)
                .capture(handler("beforeHeading", "(Lnet/minecraft/src/EntityLiving;)I"), ValueBinding.thisValue())
                .onReturn(handler("afterHeading", "(Lnet/minecraft/src/EntityLiving;)V"), ValueBinding.thisValue())
                .skipWhenCapturedNonZero().build());
        value(registrar, "gravity", heading, "motionY", "D", Opcodes.DSUB, 3, "gravity");
        value(registrar, "drag_y", heading, "motionY", "D", Opcodes.DMUL, 3, "dragY");
        value(registrar, "drag_x", heading, "motionX", "D", Opcodes.DMUL, 3, "dragX");
        value(registrar, "drag_z", heading, "motionZ", "D", Opcodes.DMUL, 3, "dragZ");
        registrar.register(new CallRedirectHookDefinition(ID + ":acceleration", heading,
                new MethodRef(ENTITY, "moveFlying", "(FFF)V"),
                handler("accelerate", "(Lnet/minecraft/src/EntityLiving;FFF)V")).withInvocationOwner(LIVING)
                .allCallsInTarget());
        registrar.register(new CallRedirectHookDefinition(ID + ":collision", heading,
                new MethodRef(ENTITY, "moveEntity", "(DDD)V"),
                handler("collide", "(Lnet/minecraft/src/EntityLiving;DDD)V")).withInvocationOwner(LIVING)
                .allCallsInTarget());
        value(registrar, "jump", new MethodRef(LIVING, "jump", "()V"), "motionY", "D", -1, 1, "jumpVelocity");
        MethodRef fallState = new MethodRef(ENTITY, "updateFallState", "(DZ)V");
        registrar.register(AroundHookDefinition.builder(ID + ":fall_tracking", fallState)
                .capture(handler("beforeFallTracking", "(Lnet/minecraft/src/Entity;)I"), ValueBinding.thisValue())
                .onReturn(handler("afterFallTracking", "(Lnet/minecraft/src/Entity;)V"), ValueBinding.thisValue())
                .skipWhenCapturedNonZero().build());
        MethodRef fall = new MethodRef(ENTITY, "fall", "(F)V").implementedBy(LIVING);
        registrar.register(AroundHookDefinition.builder(ID + ":fall_context", fall)
                .capture(handler("beginFall", "(Lnet/minecraft/src/EntityLiving;)I"), ValueBinding.thisValue())
                .onReturn(handler("finishFall", "(Lnet/minecraft/src/EntityLiving;I)V"), ValueBinding.thisValue(),
                        ValueBinding.capturedValue())
                .cleanupOnException().build());
        registrar.register(new CallRedirectHookDefinition(ID + ":fall_damage", fall,
                new MethodRef(new ClassRef("java/lang/Math"), "ceil", "(D)D"), handler("fallDamage", "(D)D"))
                .staticInvocation());
        registrar.register(new CallRedirectHookDefinition(ID + ":edge_guard",
                new MethodRef(ENTITY, "moveEntity", "(DDD)V"), new MethodRef(ENTITY, "isSneaking", "()Z"),
                handler("edgeGuard", "(Lnet/minecraft/src/Entity;)Z")));

        MethodRef playerUpdate = new MethodRef(LIVING, "onLivingUpdate", "()V").implementedBy(PLAYER);
        registrar.register(AroundHookDefinition.builder(ID + ":player", playerUpdate)
                .capture(handler("beginPlayer", "(Lnet/minecraft/src/EntityPlayerSP;)I"), ValueBinding.thisValue())
                .onReturn(handler("finishPlayer", "(Lnet/minecraft/src/EntityPlayerSP;I)V"), ValueBinding.thisValue(),
                        ValueBinding.capturedValue())
                .cleanupOnException(handler("abortPlayer", "(Lnet/minecraft/src/EntityPlayerSP;I)V")).build());
        registrar.register(new CallRedirectHookDefinition(ID + ":player_input", playerUpdate,
                new MethodRef(INPUT, "updatePlayerMoveState", "(Lnet/minecraft/src/EntityPlayer;)V"),
                handler("playerInput", "(Lnet/minecraft/src/MovementInput;Lnet/minecraft/src/EntityPlayer;)V")));
        registrar.register(new FieldReadRedirectHookDefinition(ID + ":body_adjustment", playerUpdate,
                new FieldRef(INPUT, "sneak", "Z"), handler("bodyAdjustment", "(Lnet/minecraft/src/MovementInput;)Z")));
    }

    private static void value(HookRegistrar registrar, String id, MethodRef target, String field, String descriptor,
            int arithmetic, int matches, String callback) {
        registrar.register(
                new FieldValueTransformHookDefinition(ID + ":" + id, target, new FieldRef(ENTITY, field, descriptor),
                        LIVING, handler(callback, "(Lnet/minecraft/src/EntityLiving;" + descriptor + ")" + descriptor),
                        arithmetic, matches));
    }

    private static HandlerRef handler(String name, String descriptor) {
        return HandlerRef.of(CALLBACKS, name, descriptor);
    }
}

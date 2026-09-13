package betamoon.instrumentation.hooks.item;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.api.ValueBinding;

/**
 * Preserves denied-use semantics and provides a genuine entity interaction
 * interception.
 */
public final class ItemInteractionHook implements HookModule {
    private static final String OWNER = "betamoon/instrumentation/hooks/item/ItemInteractionCallbacks";
    private static final ClassRef CONTROLLER = new ClassRef("net/minecraft/src/PlayerController");

    @Override
    public String getId() {
        return "betamoon:item_interactions";
    }

    @Override
    public void register(HookRegistrar registrar) {
        registrar.register(AroundHookDefinition.builder(getId() + ":block", new MethodRef(CONTROLLER, "sendPlaceBlock",
                "(Lnet/minecraft/src/EntityPlayer;Lnet/minecraft/src/World;Lnet/minecraft/src/ItemStack;IIII)Z"))
                .capture(HandlerRef.of(OWNER, "begin",
                        "(Lnet/minecraft/src/EntityPlayer;Lnet/minecraft/src/World;Lnet/minecraft/src/ItemStack;IIII)I"),
                        ValueBinding.argument(0), ValueBinding.argument(1), ValueBinding.argument(2),
                        ValueBinding.argument(3), ValueBinding.argument(4), ValueBinding.argument(5),
                        ValueBinding.argument(6))
                .onReturn(HandlerRef.of(OWNER, "complete", "(ZI)Z"), ValueBinding.returnValue(),
                        ValueBinding.capturedValue())
                .skipWhenCapturedNonZero().priority(100).build());

        registrar.register(AroundHookDefinition
                .builder(getId() + ":fallback", new MethodRef(CONTROLLER, "sendUseItem",
                        "(Lnet/minecraft/src/EntityPlayer;Lnet/minecraft/src/World;Lnet/minecraft/src/ItemStack;)Z"))
                .capture(
                        HandlerRef.of(OWNER, "fallback",
                                "(Lnet/minecraft/src/EntityPlayer;Lnet/minecraft/src/World;)I"),
                        ValueBinding.argument(0), ValueBinding.argument(1))
                .onReturn(HandlerRef.of(OWNER, "fallbackComplete", "(ZI)Z"), ValueBinding.returnValue(),
                        ValueBinding.capturedValue())
                .skipWhenCapturedNonZero().build());

        registrar.register(AroundHookDefinition
                .builder(getId() + ":entity",
                        new MethodRef(CONTROLLER, "interactWithEntity",
                                "(Lnet/minecraft/src/EntityPlayer;Lnet/minecraft/src/Entity;)V"))
                .capture(
                        HandlerRef.of(OWNER, "entity", "(Lnet/minecraft/src/EntityPlayer;Lnet/minecraft/src/Entity;)I"),
                        ValueBinding.argument(0), ValueBinding.argument(1))
                .onReturn(HandlerRef.of(OWNER, "entityComplete", "()V")).skipWhenCapturedNonZero().build());
    }

}

package betamoon.instrumentation.hooks.entity;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.FieldRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.api.ValueBinding;

/** Adds typed entities to the native tracker and replaces their vanilla spawn selection. */
public final class EntityTrackingHook implements HookModule {
    public static final String ID = "betamoon:entity_tracking";
    private static final String PREFIX = "net/minecraft/src/";
    private static final String CALLBACKS =
            "betamoon/instrumentation/hooks/entity/EntityTrackingCallbacks";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void register(HookRegistrar registrar) {
        registrar.register(AroundHookDefinition.builder(ID + ":register",
                new MethodRef(new ClassRef(PREFIX + "EntityTracker"), "trackEntity",
                        "(L" + PREFIX + "Entity;)V"))
                .capture(HandlerRef.of(CALLBACKS, "track", "(Ljava/lang/Object;L" + PREFIX + "Entity;)I"),
                        ValueBinding.thisValue(), ValueBinding.argument(0))
                .onReturn(HandlerRef.of(CALLBACKS, "tracked", "(I)V"), ValueBinding.capturedValue())
                .skipWhenCapturedNonZero().build());

        ClassRef entry = new ClassRef(PREFIX + "EntityTrackerEntry");
        registrar.register(AroundHookDefinition.builder(ID + ":spawn",
                new MethodRef(entry, "getSpawnPacket", "()L" + PREFIX + "Packet;"))
                .capture(HandlerRef.of(CALLBACKS, "custom", "(L" + PREFIX + "Entity;)I"),
                        ValueBinding.instanceField(new FieldRef(entry, "trackedEntity",
                                "L" + PREFIX + "Entity;")))
                .onReturn(HandlerRef.of(CALLBACKS, "spawn",
                                "(L" + PREFIX + "Packet;L" + PREFIX + "Entity;I)L" + PREFIX + "Packet;"),
                        ValueBinding.returnValue(),
                        ValueBinding.instanceField(new FieldRef(entry, "trackedEntity",
                                "L" + PREFIX + "Entity;")),
                        ValueBinding.capturedValue())
                .skipWhenCapturedNonZero().build());
    }
}

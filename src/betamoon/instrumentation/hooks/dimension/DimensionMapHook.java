package betamoon.instrumentation.hooks.dimension;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.FieldReadRedirectHookDefinition;
import betamoon.instrumentation.api.FieldRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.api.ValueBinding;

/**
 * Retains native map rendering/storage while replacing only dimension identity
 * comparisons.
 */
public final class DimensionMapHook implements HookModule {
    private static final String ID = "betamoon:dimensions:map";
    private static final String STATE = "betamoon/dimension/DimensionMapState";
    private static final String MAP = "Lnet/minecraft/src/MapData;";
    private static final String WORLD = "Lnet/minecraft/src/World;";
    private static final String STACK = "Lnet/minecraft/src/ItemStack;";
    private static final String PLAYER = "Lnet/minecraft/src/EntityPlayer;";

    public String getId() {
        return ID;
    }

    public void register(HookRegistrar registrar) {
        ClassRef map = new ClassRef("net/minecraft/src/MapData");
        ClassRef item = new ClassRef("net/minecraft/src/ItemMap");
        ClassRef base = new ClassRef("net/minecraft/src/MapDataBase");
        for (String operation : new String[]{"read", "write"}) {
            AroundHookDefinition.Builder hook = AroundHookDefinition.builder(ID + ":" + operation,
                    new MethodRef(base, operation.equals("read") ? "readFromNBT" : "writeToNBT",
                            "(Lnet/minecraft/src/NBTTagCompound;)V").implementedBy(map));
            if (operation.equals("read")) {
                hook.capture(HandlerRef.of(STATE, "validateRead", "(Lnet/minecraft/src/NBTTagCompound;)I"),
                        ValueBinding.argument(0));
            } else {
                hook.capture(HandlerRef.of("betamoon/instrumentation/hooks/dimension/DimensionCallbacks", "entering",
                        "()I"));
            }
            registrar.register(
                    hook.onReturn(HandlerRef.of(STATE, operation, "(" + MAP + "Lnet/minecraft/src/NBTTagCompound;)V"),
                            ValueBinding.thisValue(), ValueBinding.argument(0)).build());
        }
        for (String method : new String[]{"func_28012_a", "func_28013_a"}) {
            String first = method.equals("func_28012_a") ? STACK : "S";
            registrar.register(AroundHookDefinition
                    .builder(ID + ":" + method, new MethodRef(item, method, "(" + first + WORLD + ")" + MAP))
                    .capture(HandlerRef.of("betamoon/instrumentation/hooks/dimension/DimensionCallbacks", "entering",
                            "()I"))
                    .onReturn(HandlerRef.of(STATE, "bindReturned", "(" + MAP + WORLD + ")" + MAP),
                            ValueBinding.returnValue(), ValueBinding.argument(1))
                    .build());
        }
        registrar.register(AroundHookDefinition
                .builder(ID + ":created",
                        new MethodRef(new ClassRef("net/minecraft/src/Item"), "onCreated",
                                "(" + STACK + WORLD + PLAYER + ")V").implementedBy(item))
                .capture(
                        HandlerRef.of("betamoon/instrumentation/hooks/dimension/DimensionCallbacks", "entering", "()I"))
                .onReturn(HandlerRef.of(STATE, "created", "(" + STACK + WORLD + ")V"), ValueBinding.argument(0),
                        ValueBinding.argument(1))
                .build());
        MethodRef terrain = new MethodRef(item, "func_28011_a",
                "(" + WORLD + "Lnet/minecraft/src/Entity;" + MAP + ")V");
        MethodRef markers = new MethodRef(map, "func_28169_a", "(" + PLAYER + STACK + ")V");
        scope(registrar, "terrain", terrain, ValueBinding.argument(2));
        scope(registrar, "markers", markers, ValueBinding.thisValue());
        registrar.register(new FieldReadRedirectHookDefinition(ID + ":terrain_identity", terrain,
                new FieldRef(new ClassRef("net/minecraft/src/WorldProvider"), "worldType", "I"),
                HandlerRef.of(STATE, "worldComparison", "(Lnet/minecraft/src/WorldProvider;)I")));
        registrar.register(new FieldReadRedirectHookDefinition(ID + ":marker_identity", markers,
                new FieldRef(new ClassRef("net/minecraft/src/EntityPlayer"), "dimension", "I"),
                HandlerRef.of(STATE, "playerComparison", "(" + PLAYER + ")I")));
    }

    private static void scope(HookRegistrar registrar, String id, MethodRef method, ValueBinding map) {
        registrar.register(AroundHookDefinition.builder(ID + ":scope_" + id, method)
                .capture(HandlerRef.of(STATE, "enter", "(" + MAP + ")" + MAP), map)
                .onReturn(HandlerRef.of(STATE, "exit", "(" + MAP + ")V"), ValueBinding.capturedValue())
                .cleanupOnException().build());
    }
}

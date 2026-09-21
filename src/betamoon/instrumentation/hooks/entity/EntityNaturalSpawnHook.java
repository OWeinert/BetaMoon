package betamoon.instrumentation.hooks.entity;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.HookModule;
import betamoon.instrumentation.api.HookRegistrar;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.api.ValueBinding;

/** Appends custom rules to SpawnerAnimals without replacing vanilla spawning. */
public final class EntityNaturalSpawnHook implements HookModule {
    public static final String ID = "betamoon:entity_natural_spawn";
    private static final String CALLBACKS =
            "betamoon/instrumentation/hooks/entity/EntityNaturalSpawnCallbacks";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void register(HookRegistrar registrar) {
        registrar.register(AroundHookDefinition.builder(ID,
                new MethodRef(new ClassRef("net/minecraft/src/SpawnerAnimals"), "performSpawning",
                        "(Lnet/minecraft/src/World;ZZ)I"))
                .capture(HandlerRef.of(CALLBACKS, "entering", "()I"))
                .onReturn(HandlerRef.of(CALLBACKS, "after", "(Lnet/minecraft/src/World;ZZI)I"),
                        ValueBinding.argument(0), ValueBinding.argument(1), ValueBinding.argument(2),
                        ValueBinding.returnValue())
                .build());
    }
}

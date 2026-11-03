package betamoon.gui.menu;

import betamoon.luamodloader.ScriptResourceTracker;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Ordering/admission and owned cleanup, without creating a native screen. */
public final class GuiMenuRegistryTest {
    public static void main(String[] args) throws Exception {
        orderingAndAtomicRejection();
        ownershipAndLifetime();
        System.out
                .println("Menu registry passed: deterministic constraints, forward anchors, atomic cycle/cross-target "
                        + "refusal, explicit activation and bounded owned cleanup.");
    }

    private static void orderingAndAtomicRejection() {
        String owner = "ui-menu-order-fixture.lua";
        GuiMenuRegistry registry = new GuiMenuRegistry();
        try {
            registry.add(definition(owner, "test:z", keys("test:a"), Collections.emptySet()));
            registry.add(definition(owner, "test:a", Collections.emptySet(), Collections.emptySet()));
            registry.add(definition(owner, "test:m", Collections.emptySet(), keys("test:a")));
            List<GuiMenuDefinition> ordered = GuiMenuOrder.order(registry.snapshot(), "minecraft:main", "actions",
                    true);
            require(ordered.get(0).key.equals("test:z") && ordered.get(1).key.equals("test:a")
                    && ordered.get(2).key.equals("test:m"), "Constraint order depended on declaration order/key alone");
            GuiMenuDefinition previous = registry.getRequired("test:a");
            boolean refused = false;
            try {
                registry.add(definition(owner, "test:a", keys("test:z"), Collections.emptySet()));
            } catch (IllegalArgumentException expected) {
                refused = true;
            }
            require(refused && registry.getRequired("test:a") == previous,
                    "Cyclic replacement changed a usable registry");
            refused = false;
            try {
                registry.add(new GuiMenuDefinition(owner, "test:other", "minecraft:pause", "test:screen", "actions",
                        false, 0, keys("test:a"), Collections.emptySet()));
            } catch (IllegalArgumentException expected) {
                refused = true;
            }
            require(refused && registry.get("test:other") == null, "A cross-target ordering anchor was published");
        } finally {
            ScriptResourceTracker.unload(owner);
        }
        require(registry.snapshot().isEmpty(), "Owner unload retained menu metadata");
    }

    private static void ownershipAndLifetime() throws Exception {
        String owner = "ui-menu-lifetime-fixture.lua";
        GuiMenuRegistry registry = new GuiMenuRegistry();
        try {
            registry.add(definition(owner, "test:claim", Collections.emptySet(), Collections.emptySet()));
            require(registry.activations().isEmpty(), "Registration implicitly seized a native menu");
            for (int attempt = 0; attempt < 100; attempt++) {
                GuiMenuActivation activation = registry.activate(owner, "test:claim");
                require(activation.requested() && activation.input.active(),
                        "An explicit activation did not own input eligibility");
                activation.close();
                activation.close();
                require(!activation.input.active() && registry.activations().isEmpty(),
                        "Close retained its activation/input lease");
            }
            Field cleanups = ScriptResourceTracker.class.getDeclaredField("CLEANUPS");
            cleanups.setAccessible(true);
            Map<?, ?> tracked = (Map<?, ?>) cleanups.get(null);
            require(((List<?>) tracked.get(owner)).size() == 1,
                    "Repeated activation/close accumulated owner cleanup records");
            GuiMenuActivation active = registry.activate(owner, "test:claim");
            ScriptResourceTracker.unload(owner);
            require(active.status().equals("closed") && !active.input.active() && registry.snapshot().isEmpty(),
                    "Owner unload did not immediately revoke the active claim");
        } finally {
            ScriptResourceTracker.unload(owner);
        }
    }

    private static GuiMenuDefinition definition(String owner, String key, Set<String> before, Set<String> after) {
        return new GuiMenuDefinition(owner, key, "minecraft:main", "test:screen", "actions", false, 0, before, after);
    }

    private static Set<String> keys(String... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

package betamoon.gui.menu;

import betamoon.gui.component.GuiComponentDefinition;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Objects;

/**
 * Owned menu metadata, independent of native screen instances and document
 * lifetimes.
 */
public final class GuiMenuDefinition {
    public static final Set<String> TARGETS = Collections
            .unmodifiableSet(new LinkedHashSet<>(Arrays.asList("minecraft:main", "minecraft:pause", "minecraft:options",
                    "minecraft:world_select", "betamoon:world_create")));
    public final String owner;
    public final String key;
    public final String target;
    public final String screen;
    public final String region;
    public final boolean replacement;
    public final int priority;
    public final Set<String> before;
    public final Set<String> after;
    private final ScriptResourceTracker.OwnerGeneration generation;
    private final long token;

    public GuiMenuDefinition(String owner, String key, String target, String screen, String region, boolean replacement,
            int priority, Set<String> before, Set<String> after) {
        this.owner = Objects.requireNonNull(owner, "menu owner");
        this.key = GuiComponentDefinition.key(key);
        this.screen = GuiComponentDefinition.key(screen);
        if (!TARGETS.contains(target) || !Arrays.asList("actions", "footer", "content").contains(region)
                || replacement != region.equals("content") || priority < -10000 || priority > 10000
                || !replacement && priority != 0 || before.size() > 32 || after.size() > 32
                || replacement && (!before.isEmpty() || !after.isEmpty())) {
            throw new IllegalArgumentException("Invalid menu target, region, replacement or ordering contract");
        }
        this.target = target;
        this.region = region;
        this.replacement = replacement;
        this.priority = priority;
        this.before = keys(before);
        this.after = keys(after);
        generation = ScriptResourceTracker.ownerToken(owner);
        token = generation.current();
    }

    private Set<String> keys(Set<String> keys) {
        Set<String> result = new LinkedHashSet<>();
        for (String key : keys) {
            result.add(GuiComponentDefinition.key(key));
            if (this.key.equals(key)) {
                throw new IllegalArgumentException("A menu contribution cannot order itself");
            }
        }
        return Collections.unmodifiableSet(result);
    }

    public boolean available() {
        return generation.matches(token);
    }
}

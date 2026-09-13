package betamoon.recipes;

import betamoon.luaapi.resource.RecipeRegistryApi;
import betamoon.luamodloader.ScriptResourceTracker;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.src.ItemStack;
import org.luaj.vm2.LuaValue;

/**
 * Restores live predecessors when scripts sharing a native furnace input
 * unload.
 */
public final class NativeSmeltingRegistrations {
    private static final Map<Integer, Registration> HEADS = new HashMap<Integer, Registration>();
    private NativeSmeltingRegistrations() {
    }
    private static final class Registration {
        final int input;
        final ItemStack baseline;
        final Registration previous;
        final LuaValue reference;
        boolean active = true;
        Registration(int input, ItemStack baseline, Registration previous, LuaValue reference) {
            this.input = input;
            this.baseline = baseline;
            this.previous = previous;
            this.reference = reference;
        }
    }
    /** Call after adding the native output, passing the previous map value. */
    public static void track(final int input, ItemStack previousOutput, final LuaValue reference) {
        Registration head = HEADS.get(Integer.valueOf(input));
        if (head != null && previousOutput != RecipeRegistryApi.nativeOutput(head.reference)
                && !(previousOutput == null && RecipeRegistryApi.nativeDisabled(head.reference))) {
            head = null;
        }
        final Registration registration = new Registration(input, previousOutput, head, reference);
        HEADS.put(Integer.valueOf(input), registration);
        ScriptResourceTracker.track(new ScriptResourceTracker.Cleanup() {
            public void run() {
                registration.active = false;
                Map<Integer, ItemStack> map = NativeRecipeRegistries.smelting();
                Integer key = Integer.valueOf(input);
                Object current = map.get(key);
                boolean ownsCurrent = current == RecipeRegistryApi.nativeOutput(reference)
                        || current == null && RecipeRegistryApi.nativeDisabled(reference);
                RecipeRegistryApi.retire(reference);
                if (HEADS.get(key) != registration) {
                    return;
                }
                Registration cursor = registration;
                while (cursor.previous != null && !cursor.previous.active) {
                    cursor = cursor.previous;
                }
                Registration previous = cursor.previous;
                if (previous == null) {
                    HEADS.remove(key);
                } else {
                    HEADS.put(key, previous);
                }
                if (!ownsCurrent) {
                    return; // Another mod replaced the live native entry.
                }
                ItemStack restored = previous == null
                        ? cursor.baseline
                        : RecipeRegistryApi.nativeDisabled(previous.reference)
                                ? null
                                : RecipeRegistryApi.nativeOutput(previous.reference);
                if (restored == null) {
                    map.remove(key);
                } else {
                    map.put(key, restored);
                }
                if (previous != null) {
                    RecipeRegistryApi.restoreNative(previous.reference);
                }
            }
        });
    }
}

package betamoon.hooks;

import java.util.List;
import java.util.Map;

/**
 * Loaded with a deliberately incompatible hook bridge, without initializing
 * normal APIs or game types.
 */
public final class HookUnavailableRuntime {
    public static void verify(String expectedReason) {
        for (int attempt = 0; attempt < 3; attempt++) {
            if (HookPreparation.current() != null) {
                throw new AssertionError("incompatible agent was accepted");
            }
            List<Map<String, Object>> rows = HookPreparation.unavailableRecords();
            if (rows.size() != 1 || !rows.get(0).get("reason").equals(expectedReason)) {
                throw new AssertionError("unavailable agent lacks a detached diagnostic: " + rows);
            }
            HookDeclarations.evaluate("unavailable", "pure.lua",
                    "local r=betamoon.hooks:createRegistry(); "
                            + "local t=betamoon.native:type('int[]'); assert(t.componentType.name=='int')",
                    1, null, new HookDeclarations.Budget());
        }
        System.out.println(
                "Incompatible hook agent diagnosed without poisoning pure native metadata or preparation classes.");
    }
}

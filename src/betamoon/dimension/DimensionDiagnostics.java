package betamoon.dimension;

import betamoon.teleport.TeleportDiagnostics;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.src.World;

/** On-demand export of public catalog identity and bounded navigation state. */
public final class DimensionDiagnostics {
    private DimensionDiagnostics() {
    }

    public static List<String> rows(World world) {
        List<String> rows = new ArrayList<String>();
        for (DimensionKey key : DimensionRegistry.snapshot().keySet()) {
            DimensionGenerationBinding binding = DimensionRegistry.binding(key);
            rows.add("dimension: " + key + " | displayName: " + binding.definition.displayName + " | generator: "
                    + binding.nativeBase + " | profile: " + binding.definition.profileKey + " | signature: "
                    + binding.signature);
            rows.add("    input hashes: " + binding.dependencies);
        }
        if (world != null && !world.multiplayerWorld) {
            try {
                DimensionSession session = DimensionQueries.session(world);
                rows.add("active key: " + DimensionIdentity.key(world));
                rows.add("save-local IDs (retained allocations): " + session.index.entries());
                rows.add("player revision: " + session.navigation.revision() + " | pending transfer: "
                        + session.navigation.pending());
                rows.add("return history counts: " + session.navigation.historyCounts());
                DimensionGenerationBinding active = DimensionRuntime.generation(world);
                if (active != null) {
                    rows.add("active pinned signature: " + active.signature);
                }
            } catch (IOException | IllegalArgumentException failure) {
                rows.add("active dimension state unavailable: " + failure.getMessage());
            }
        }
        for (Map<String, Object> transfer : TeleportDiagnostics.snapshot()) {
            rows.add("recent operation: " + transfer);
        }
        return rows;
    }
}

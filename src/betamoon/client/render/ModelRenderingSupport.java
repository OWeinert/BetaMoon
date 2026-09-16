package betamoon.client.render;

import betamoon.instrumentation.agent.AgentRuntime;
import betamoon.instrumentation.agent.BetaMoonAgent;
import betamoon.instrumentation.diagnostics.HookDiagnostic;
import betamoon.instrumentation.diagnostics.HookStatus;
import betamoon.instrumentation.diagnostics.TransformationReport;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.src.EntityRenderer;
import net.minecraft.src.Chunk;
import net.minecraft.src.RenderGlobal;
import net.minecraft.src.ItemRenderer;
import net.minecraft.src.RenderEngine;
import net.minecraft.src.RenderItem;
import org.luaj.vm2.LuaError;

/**
 * Required render bridges must be installed before native content accepts a
 * model appearance.
 */
public final class ModelRenderingSupport {
    private ModelRenderingSupport() {
    }

    public static void requireAvailable() {
        if (!BetaMoonAgent.isRegistered()) {
            throw new LuaError(
                    "Model appearances require the BetaMoon Java agent; add -javaagent to this instance's JVM options");
        }
        // Class literals use reobfuscated names and load the targets without native
        // initialization.
        Class<?>[] targets = {RenderItem.class, ItemRenderer.class, EntityRenderer.class, RenderEngine.class,
                Chunk.class, RenderGlobal.class};
        for (Class<?> target : targets) {
            target.getName();
        }
        TransformationReport report = AgentRuntime.getReport();
        Set<String> applied = new HashSet<>();
        if (report != null) {
            for (HookDiagnostic diagnostic : report.snapshot()) {
                if (diagnostic.getStatus() == HookStatus.APPLIED
                        || diagnostic.getStatus() == HookStatus.ALREADY_APPLIED) {
                    applied.add(diagnostic.getHookId());
                }
            }
        }
        for (String id : new String[]{"betamoon:model_render:held", "betamoon:model_render:gui",
                "betamoon:model_render:ground", "betamoon:model_render:frame", "betamoon:lua_texture_resource",
                "betamoon:lua_texture_resource:refresh", "betamoon:model_render:chunk_loaded",
                "betamoon:model_render:chunk_unloaded", "betamoon:model_render:setBlockID",
                "betamoon:model_render:setBlockIDWithMetadata", "betamoon:model_render:world"}) {
            if (!applied.contains(id)) {
                throw new LuaError(
                        "Required model rendering hook is unavailable: " + id + "; check the Java agent diagnostics");
            }
        }
    }
}

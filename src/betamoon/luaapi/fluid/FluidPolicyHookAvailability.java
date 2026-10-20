package betamoon.luaapi.fluid;

import betamoon.fluid.FluidCarrier;
import betamoon.fluid.FluidEntityPolicy;
import betamoon.instrumentation.agent.AgentRuntime;
import betamoon.instrumentation.diagnostics.HookDiagnostic;
import betamoon.instrumentation.diagnostics.HookStatus;
import betamoon.runtime.RuntimeEnvironment;
import org.luaj.vm2.LuaError;

/** Rejects requested policies when their native seams have not been installed. */
final class FluidPolicyHookAvailability {
    private FluidPolicyHookAvailability() {
    }

    static void requireEntity(FluidEntityPolicy entity, FluidCarrier carrier) {
        require("fluid.entity", "betamoon:fluid_contact:water_movement",
                "betamoon:fluid_contact:lava_movement", "betamoon:fluid_contact:eye_material",
                "betamoon:fluid_contact:entity_tick", "betamoon:fluid_contact:native_lava_damage",
                "betamoon:fluid_contact:breathing", "betamoon:fluid_contact:living_tick",
                "betamoon:fluid_contact:item_water_movement");
        if (entity.movement() == FluidEntityPolicy.Movement.LAVA || carrier == FluidCarrier.LAVA) {
            require("fluid.entity.movement", "betamoon:fluid_contact:item_lava_material");
        }
        if (RuntimeEnvironment.isClient()) {
            require("fluid.entity", "betamoon:fluid_camera:wet", "betamoon:fluid_camera:air_hud");
        }
    }

    static void requireCamera() {
        if (!RuntimeEnvironment.isDedicatedServer()) {
            require("fluid.camera", "betamoon:fluid_camera:updateFogColor_material",
                    "betamoon:fluid_camera:updateFogColor", "betamoon:fluid_camera:setupFog_material",
                    "betamoon:fluid_camera:setupFog", "betamoon:fluid_camera:overlay_visibility",
                    "betamoon:fluid_camera:overlay_texture", "betamoon:fluid_camera:air_hud");
        }
    }

    private static void require(String path, String... hookIds) {
        if (!AgentRuntime.isRegistered()) {
            throw new LuaError(path + " requires BetaMoon native instrumentation.");
        }
        for (String id : hookIds) {
            boolean installed = false;
            for (HookDiagnostic diagnostic : AgentRuntime.getReport().snapshot()) {
                if (id.equals(diagnostic.getHookId())) {
                    HookStatus status = diagnostic.getStatus();
                    installed = status == HookStatus.APPLIED || status == HookStatus.ALREADY_APPLIED
                            || status == HookStatus.WAITING_FOR_TARGET;
                    break;
                }
            }
            if (!installed) {
                throw new LuaError(path + " requires unavailable native hook '" + id + "'.");
            }
        }
    }
}

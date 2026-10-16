package betamoon.worldgen.terrain;

import betamoon.atmosphere.AtmosphereRegistry;
import betamoon.client.atmosphere.AtmosphereVolumeRamp;
import betamoon.luaapi.world.AtmospheresApi;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Priority composition, temporal fades, ownership and world transitions without
 * an OpenGL context.
 */
public final class AtmosphereTest {
    public static void main(String[] arguments) throws Exception {
        for (float target : new float[]{0.25f, 1, 4}) {
            AtmosphereVolumeRamp ramp = new AtmosphereVolumeRamp();
            float value = 0;
            for (int tick = 1; tick <= 20; tick++) {
                value = ramp.tick(target, 20);
                require(Math.abs(value - target * tick / 20) < 0.00001,
                        "Sound fade-in duration must not depend on target volume");
            }
            require(value == target, "Fade-in must reach its target exactly");
            for (int tick = 1; tick <= 20; tick++) {
                value = ramp.tick(0, 20);
                require(Math.abs(value - target * (20 - tick) / 20) < 0.00001,
                        "Sound fade-out must follow its declared duration");
            }
            require(value == 0 && ramp.tick(target, 0) == target,
                    "Fade-out must finish exactly, and zero-duration changes must be immediate");
        }
        Globals lua = JsePlatform.standardGlobals();
        LuaTable module = new LuaTable();
        AtmospheresApi.attach(module);
        lua.set("betamoon", module);
        try (AtmosphereRegistry.Batch batch = AtmosphereRegistry.begin("atmosphere.lua")) {
            lua.load("local a=betamoon.client.visuals.atmospheres;"
                    + "base=a:add{key='test:atmosphere/base',fog={color=0x884422,near=16,far=96},"
                    + "sky={color={1,0.8,0.6}},lightTone={1,0.9,0.8}};"
                    + "top=a:add{key='test:atmosphere/top',fog={color={0.1,0.2,0.3},near=8,far=32,density=0.2}};"
                    + "a:bind{profile=base,scope={type='dimension',id=0},priority=1}").call();
            batch.publish();
        }
        TerrainFoundationTest.Save save = new TerrainFoundationTest.Save(
                Files.createTempDirectory("betamoon-atmosphere-"));
        TerrainFoundationTest.Fixture world = new TerrainFoundationTest.Fixture(save, 1);
        require(weight(AtmosphereRegistry.query(world, 0, 10, 0), "base") == 1, "Dimension binding must apply");
        Map<String, Object> scope = new LinkedHashMap<String, Object>();
        scope.put("type", "sphere");
        scope.put("center", point(0, 10, 0));
        scope.put("radius", 5.0);
        Map<String, Object> options = new LinkedHashMap<String, Object>();
        options.put("scope", scope);
        options.put("priority", 10.0);
        options.put("fadeTicks", 2.0);
        options.put("blendBlocks", 5.0);
        long id = AtmosphereRegistry.activate("atmosphere.lua", "test:atmosphere/top", world, options);
        require(weight(AtmosphereRegistry.query(world, 0, 10, 0), "base") == 1,
                "Zero-weight fade starts at lower layer");
        AtmosphereRegistry.tick(world);
        List<AtmosphereRegistry.Contribution> half = AtmosphereRegistry.query(world, 0, 10, 0);
        require(weight(half, "top") == 0.5 && weight(half, "base") == 0.5,
                "Higher layer must fade over the lower layer");
        AtmosphereRegistry.tick(world);
        require(weight(AtmosphereRegistry.query(world, 0, 10, 0), "top") == 1,
                "Completed fade must select highest priority");
        AtmosphereRegistry.Presentation presentation = AtmosphereRegistry.queryPresentation(world, 0, 10, 0);
        require(weight(presentation.channel(0), "top") == 1 && weight(presentation.channel(2), "base") == 1,
                "A high-priority fog-only profile must retain lower-priority light tone and sky channels");
        require(weight(AtmosphereRegistry.query(world, 7.5, 10, 0), "top") == 0.5,
                "Sphere falloff must compose spatial and temporal weights");
        AtmosphereRegistry.close(id);
        require(!AtmosphereRegistry.isActive(id), "Closing is idempotent and marks the handle inactive");
        AtmosphereRegistry.close(id);
        AtmosphereRegistry.tick(world);
        require(weight(AtmosphereRegistry.query(world, 0, 10, 0), "base") == 0.5,
                "Close must fade back to the lower layer");
        AtmosphereRegistry.tick(world);
        require(weight(AtmosphereRegistry.query(world, 0, 10, 0), "base") == 1, "Closed layer must release completely");
        long transition = AtmosphereRegistry.activate("atmosphere.lua", "test:atmosphere/top", world, options);
        AtmosphereRegistry.tick(new TerrainFoundationTest.Fixture(save, 1));
        require(!AtmosphereRegistry.isActive(transition), "World transitions must close temporary layers");
        try (AtmosphereRegistry.Batch batch = AtmosphereRegistry.begin("broken.lua")) {
            lua.load("betamoon.client.visuals.atmospheres:bind{profile='test:atmosphere/missing',"
                    + "scope={type='dimension',id=0}}").call();
            try {
                batch.publish();
                throw new AssertionError("Missing binding profile must reject publication");
            } catch (IllegalArgumentException expected) {
                require(AtmosphereRegistry.get("test:atmosphere/base") != null,
                        "Failed publication must preserve the prior profile catalog");
            }
        }
        AtmosphereRegistry.retainOwners(Collections.<String>emptySet());
        require(AtmosphereRegistry.query(world, 0, 10, 0).isEmpty(), "Owner removal must restore native presentation");
        System.out.println("Atmosphere checks passed: Lua profiles/bindings, priority groups, fades, spatial falloff, "
                + "world transitions, failed publication and owner cleanup.");
    }

    private static Map<String, Object> point(double x, double y, double z) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("x", x);
        result.put("y", y);
        result.put("z", z);
        return result;
    }

    private static double weight(List<AtmosphereRegistry.Contribution> contributions, String suffix) {
        double result = 0;
        for (AtmosphereRegistry.Contribution contribution : contributions) {
            if (contribution.profile.key.endsWith("/" + suffix)) {
                result += contribution.weight;
            }
        }
        return result;
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}

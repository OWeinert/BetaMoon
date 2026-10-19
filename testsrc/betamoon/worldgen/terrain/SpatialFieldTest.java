package betamoon.worldgen.terrain;

import betamoon.luaapi.world.WorldGenApi;
import betamoon.worldgen.WorldGenKind;
import betamoon.worldgen.spatial.FieldContext;
import betamoon.worldgen.spatial.ScalarField;
import betamoon.worldgen.spatial.SpatialRegistry;
import java.util.Collections;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * User declarations compile to finite deterministic graphs without runtime Lua
 * evaluation.
 */
public final class SpatialFieldTest {
    private static Globals lua;

    public static void main(String[] arguments) {
        lua = JsePlatform.standardGlobals();
        LuaTable module = new LuaTable();
        WorldGenApi.attach(module);
        lua.set("betamoon", module);
        try (SpatialRegistry.Batch batch = SpatialRegistry.begin("spatial.lua")) {
            lua.load("noise=betamoon.worldgen.spatial.noises:add{key='test:noise/hills',algorithm='value_noise_v1',"
                    + "amplitudes={1,0.5,0.25}};"
                    + "anchor=betamoon.worldgen.spatial.anchors:add{key='test:anchor/center',x=-30000000,z=12};"
                    + "distance=betamoon.worldgen.spatial.fields:add{key='test:field/distance',node={type='distance',"
                    + "anchor=anchor,blocksPerUnit=10,metric='manhattan'}};"
                    + "field=betamoon.worldgen.spatial.fields:add{key='test:field/hills',range={min=0,max=6},"
                    + "node={type='clamp',min=0,max=6,input={type='add',inputs={3,{type='multiply',inputs={2,"
                    + "{type='noise',noise=noise,scale={x=0.005,z=0.005}}}}}}}};"
                    + "rotated=betamoon.worldgen.spatial.fields:add{key='test:field/rotated',"
                    + "node={type='rotate_2d',degrees=90,input={type='x'}}};"
                    + "density=betamoon.worldgen.spatial.fields:add{key='test:field/density',"
                    + "node={type='y_gradient',fromY=0,toY=127,fromValue=1,toValue=-1}};").call();
            batch.publish();
        }
        SpatialRegistry.Snapshot snapshot = SpatialRegistry.snapshot();
        FieldContext context = new FieldContext(123, 0);
        ScalarField field = snapshot.field("test:hills");
        double[] volume = new double[16 * 16 * 8];
        field.sampleVolume(context, -16, 0, -16, 16, 8, 16, 1, 1, 1, volume);
        int index = 0;
        for (int x = -16; x < 0; x++) {
            for (int z = -16; z < 0; z++) {
                for (int y = 0; y < 8; y++) {
                    double value = field.sample(context, x, y, z);
                    require(value >= 0 && value <= 6 && value == volume[index++], "Bulk/point sampling must agree");
                }
            }
        }
        require(snapshot.field("test:distance").sample(context, -29999990, 0, 12) == 1,
                "Far fixed anchors must preserve integer coordinates");
        ScalarField rotated = snapshot.field("test:rotated");
        require(Math.abs(rotated.sample(context, 12, 0, 9) + 9) < 1e-9 && (rotated.dependencies & 5) == 5,
                "Coordinate rotation must propagate both horizontal dependencies");
        require(snapshot.field("test:density").sample(context, 0, 127, 0) == -1,
                "Density gradient must cover the world height");
        reject("{type='sqrt',input=-1}");
        reject("{type='divide',left=1,right=0}");
        reject("{type='noise',noise='test:noise/missing'}");
        reject("{type='cache_2d',input={type='y'}}");
        reject("{type='clamp',input=1,min=2,max=1}");
        reject("{type='constant',value=2,typo=true}");
        reject("{type='curve',input=1,points={{at=0,value=1},{at=0,value=2}}}");
        try (SpatialRegistry.Batch batch = SpatialRegistry.begin("cyclic.lua")) {
            lua.load("betamoon.worldgen.spatial.fields:add{key='cycle:field/a',node='cycle:field/b'};"
                    + "betamoon.worldgen.spatial.fields:add{key='cycle:field/b',node='cycle:field/a'}").call();
            try {
                batch.validate();
                throw new AssertionError("Cycles must fail publication");
            } catch (IllegalArgumentException expected) {
                require(expected.getMessage().contains("cyclic"), "Cycle diagnostics must identify the cause");
            }
        }
        require(snapshot == SpatialRegistry.snapshot(), "Failed batches must preserve the active graph snapshot");
        String signature = snapshot.signature("test:hills", WorldGenKind.FIELD);
        try (SpatialRegistry.Batch batch = SpatialRegistry.begin("unrelated.lua")) {
            lua.load("betamoon.worldgen.spatial.fields:add{key='other:field/constant',node=7}").call();
            batch.publish();
        }
        require(signature.equals(SpatialRegistry.snapshot().signature("test:hills", WorldGenKind.FIELD)),
                "Unrelated declarations must not change a field signature");
        SpatialRegistry.retainOwners(Collections.<String>emptySet());
        require(snapshot.field("test:hills").sample(context, 0, 0, 0) == field.sample(context, 0, 0, 0),
                "Pinned snapshots must survive global registry cleanup");
        System.out
                .println("Spatial fields passed: Lua publication, references, ranges, axes, negative/far coordinates, "
                        + "bulk samples, cycles, signature isolation and snapshot lifetime.");
    }

    private static void reject(String expression) {
        try (SpatialRegistry.Batch batch = SpatialRegistry.begin("invalid.lua")) {
            lua.load("betamoon.worldgen.spatial.fields:add{key='bad:field/test',node=" + expression + "}").call();
            try {
                batch.validate();
                throw new AssertionError("Invalid graph accepted: " + expression);
            } catch (IllegalArgumentException expected) {
                // Expected declaration-time rejection.
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

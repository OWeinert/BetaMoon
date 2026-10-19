package betamoon.worldgen.terrain;

import betamoon.luaapi.utils.LuaDataSnapshot;
import betamoon.worldgen.spatial.FieldContext;
import betamoon.worldgen.spatial.FieldTrace;
import betamoon.worldgen.spatial.NoiseDefinition;
import betamoon.worldgen.spatial.ScalarField;
import betamoon.worldgen.spatial.ScalarFieldCompiler;
import betamoon.worldgen.spatial.SpatialRegistry;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Independent numeric fixtures for graph operations and versioned noise
 * vectors.
 */
public final class SpatialGoldenTest {
    private static final Globals LUA = JsePlatform.standardGlobals();

    public static void main(String[] args) {
        String x = "{type='x'}";
        String y = "{type='y'}";
        String z = "{type='z'}";
        check("{type='constant',value=7}", 7);
        check("{type='add',inputs={" + x + "," + y + "," + z + "}}", 11.25);
        check("{type='multiply',inputs={" + x + ",2}}", -4.5);
        check("{type='min',inputs={" + x + "," + y + "," + z + "}}", -2.25);
        check("{type='max',inputs={" + x + "," + y + "," + z + "}}", 10);
        check("{type='subtract',left=" + y + ",right=" + x + "}", 12.25);
        check("{type='divide',left=" + x + ",right=2}", -1.125);
        check("{type='abs',input=" + x + "}", 2.25);
        check("{type='negate',input=" + x + "}", 2.25);
        check("{type='square',input=" + x + "}", 5.0625);
        check("{type='cube',input=" + x + "}", -11.390625);
        check("{type='sqrt',input={type='square',input=" + x + "}}", 2.25);
        check("{type='power',input=" + x + ",exponent=4}", 25.62890625);
        check("{type='floor',input=" + x + "}", -3);
        check("{type='ceil',input=" + x + "}", -2);
        check("{type='round',input=" + x + "}", -2);
        check("{type='clamp',input=" + x + ",min=-1,max=1}", -1);
        check("{type='smoothstep',input=" + y + ",min=0,max=20}", 0.5);
        check("{type='map_range',input=" + x + ",fromMin=-10,fromMax=10,toMin=0,toMax=100}", 38.75);
        for (String curve : new String[]{"curve", "spline"}) {
            check("{type='" + curve + "',input=" + y + ",points={{at=0,value=0},{at=20,value=100}}}", 50);
        }
        check("{type='terrace',input=" + y + ",stepHeight=4,blend=0}", 8);
        check("{type='lerp',alpha=0.25,from=" + x + ",to=" + z + "}", -0.8125);
        check("{type='select',input=" + x + ",min=-3,max=-2,whenTrue=" + y + ",whenFalse=" + z + "}", 10);
        check("{type='range_choice',input=" + y + ",min=1,max=5,whenTrue=" + x + ",whenFalse=" + z + "}", 3.5);
        check("{type='y_gradient',fromY=0,toY=20,fromValue=2,toValue=-2}", 0);
        check("{type='shift',input=" + x + ",x=3}", 0.75);
        check("{type='scale_coordinates',input=" + x + ",x=2}", -4.5);
        check("{type='rotate_2d',input=" + x + ",degrees=90}", -3.5);
        for (String cache : new String[]{"cache_2d", "cache_cell", "cache_once"}) {
            check("{type='" + cache + "',input=" + x + "}", -2.25);
        }
        check("{type='find_top_surface',input={type='y_gradient',fromY=0,toY=20,fromValue=10,toValue=-10},maxY=20}", 9);
        for (String metric : new String[]{"euclidean", "manhattan", "chebyshev"}) {
            ScalarField distance = graph(
                    "{type='distance',anchor='minecraft:anchor/original_spawn',metric='" + metric + "'}");
            double value = distance.sample(new FieldContext(1, 0, true, 0, 0), -2.25, 10, 3.5);
            double expected = metric.equals("euclidean")
                    ? StrictMath.sqrt(17.3125)
                    : metric.equals("manhattan") ? 5.75 : 3.5;
            if (Math.abs(value - expected) > 1e-12) {
                throw new AssertionError("Distance metric changed: " + metric);
            }
        }
        ScalarField random = graph("{type='random_hash',salt=7}");
        ScalarField spots = graph("{type='spots',cellSize=64,chance=1,radius={min=32,max=32},amplitude=1}");
        requireBits(random.sample(new FieldContext(123, 0), -2.25, 10, 3.5), 0x1.85420d73098bep-1);
        double[] expectedSpots = {0, 0x1.b4f87c76cfe5p-2, 0x1.a045eb5e3898cp-15, 0x1.fe019191547f3p-2, 0};
        for (int i = 0; i < expectedSpots.length; i++) {
            int coordinate = -64 + i * 32;
            requireBits(spots.sample(new FieldContext(123, 0), coordinate, 0, coordinate), expectedSpots[i]);
        }
        climateCache();
        noiseVectors();
        geographicProgressionAndBulk();
        System.out.println(
                "Spatial golden checks passed: arithmetic, ranges, transforms, curves, caches, scans and noise revisions.");
    }

    private static void geographicProgressionAndBulk() {
        FieldContext context = new FieldContext(123, 0, true, -32, 48);
        ScalarField progression = graph("{type='clamp',min=0,max=1,input={type='distance',"
                + "anchor='minecraft:anchor/original_spawn',blocksPerUnit=256}}");
        for (int[] direction : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            for (int distance : new int[]{0, 64, 128, 256, 512}) {
                requireBits(progression.sample(context, -32 + direction[0] * distance, 0, 48 + direction[1] * distance),
                        Math.min(1, distance / 256.0));
            }
        }
        ScalarField warped = graph("{type='shift',input={type='add',inputs={{type='x'},{type='y'},{type='z'}}},"
                + "x={type='multiply',inputs={{type='z'},0.5}},y={type='multiply',inputs={{type='x'},0.25}}}");
        FieldTrace trace = new FieldTrace();
        requireBits(warped.sample(context.withTrace(trace), -17, 3, -17), 1.25 * -17 + 3 + 1.5 * -17);
        boolean transformed = false;
        for (Map<String, Object> sample : trace.samples()) {
            transformed |= ((Number) sample.get("x")).doubleValue() == -25.5;
        }
        if (!transformed || trace.truncated()) {
            throw new AssertionError("Inspection must retain evaluated transformed coordinates");
        }
        ScalarField scan = graph("{type='find_top_surface',threshold=1000,"
                + "input={type='add',inputs={{type='y'},{type='x'},0,0,0,0,0,0}}}");
        FieldTrace bounded = new FieldTrace();
        requireBits(scan.sample(context.withTrace(bounded), 0, 0, 0), -1);
        if (!bounded.truncated() || bounded.samples().size() != 512) {
            throw new AssertionError(
                    "Inspection trace count=" + bounded.samples().size() + ", truncated=" + bounded.truncated() + "");
        }
        double[] volume = new double[4 * 8 * 4];
        warped.sampleVolume(context, -17, 3, -17, 4, 8, 4, 1, 1, 1, volume);
        int cursor = 0;
        for (int x = -17; x < -13; x++) {
            for (int z = -17; z < -13; z++) {
                for (int y = 3; y < 11; y++) {
                    double expected = 1.25 * x + y + 1.5 * z;
                    requireBits(volume[cursor++], expected);
                    requireBits(warped.sample(context, x, y, z), expected);
                }
            }
        }
    }

    private static void requireBits(double actual, double expected) {
        if (Double.doubleToLongBits(actual) != Double.doubleToLongBits(expected)) {
            throw new AssertionError("Deterministic vector changed: " + Double.toHexString(actual));
        }
    }

    private static ScalarField graph(String expression) {
        LuaValue declaration = LUA.load("return {node=" + expression + "}").call();
        return new ScalarFieldCompiler("test:field/golden", SpatialRegistry.snapshot())
                .compile(LuaDataSnapshot.object(declaration, "golden").get("node"));
    }

    private static void check(String expression, double expected) {
        ScalarField field = graph(expression);
        double actual = field.sample(new FieldContext(123, 0), -2.25, 10, 3.5);
        if (Math.abs(actual - expected) > 1e-10) {
            throw new AssertionError(expression + ": expected " + expected + ", got " + actual);
        }
    }

    private static void climateCache() {
        final int[] samples = {0};
        FieldContext.Environment environment = new FieldContext.Environment() {
            public double biomeValue(String property, int x, int z) {
                samples[0]++;
                return property.equals("temperature") ? 0.25 : 0.75;
            }

            public boolean biomeMatches(String selector, int x, int z) {
                return false;
            }
        };
        FieldContext context = new FieldContext(1, 0, true, 0, 0, environment);
        ScalarField field = graph("{type='biome_value',property='temperature'}");
        for (int y = 0; y < 128; y++) {
            if (field.sample(context, -12.5, y, 2.5) != 0.25) {
                throw new AssertionError("Climate samples changed across Y");
            }
        }
        if (samples[0] != 1) {
            throw new AssertionError("An X/Z field must sample once for the column, got " + samples[0]);
        }
    }

    private static void noiseVectors() {
        String[] algorithms = {"value_noise_v1", "perlin_v1", "simplex_2d_v1", "beta_octaves_v1"};
        double[][] points = {{0, 0, 0}, {-12.25, 3.5, 19.75}, {29999999.25, 127, -29999999.5}};
        double[][] expected = {{-0x1.0168ae540a8dbp-1, -0x1.a1b41f68a0862p-2, -0x1.b0ad645ffa3cbp-3},
                {0x0.0p0, -0x1.3a8581e00db6ep-2, 0x1.44fc892492492p-3},
                {0x0.0p0, -0x1.8e98281d8295ap-2, -0x1.10ec68323aa72p-2},
                {0x1.c0ca7b5439a11p-3, 0x1.47ce909d38e33p-2, 0x1.657cd48a1a2a5p-6}};
        for (int algorithmIndex = 0; algorithmIndex < algorithms.length; algorithmIndex++) {
            String algorithm = algorithms[algorithmIndex];
            Map<String, Object> definition = new LinkedHashMap<String, Object>();
            definition.put("algorithm", algorithm);
            definition.put("amplitudes", Arrays.<Object>asList(1.0, 0.5, 0.25));
            definition.put("firstOctave", -1.0);
            NoiseDefinition noise = new NoiseDefinition("test:noise/golden", definition);
            StringBuilder vector = new StringBuilder(algorithm);
            for (int pointIndex = 0; pointIndex < points.length; pointIndex++) {
                double[] point = points[pointIndex];
                double value = noise.sample(12345, point[0], point[1], point[2]);
                if (!Double.isFinite(value) || Math.abs(value) > 1) {
                    throw new AssertionError("Noise escaped its bound: " + algorithm);
                }
                if (Double.doubleToLongBits(value) != Double.doubleToLongBits(expected[algorithmIndex][pointIndex])) {
                    throw new AssertionError("Noise revision changed: " + algorithm + ":" + pointIndex + " got "
                            + Double.toHexString(value));
                }
            }

        }
    }
}

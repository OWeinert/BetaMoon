package betamoon.worldgen.spatial;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Frozen outputs from the pre-optimization versioned samplers, including
 * adversarial coordinates.
 */
public final class NoiseSamplerCompatibilityTest {
    private static final Path FIXTURE = Paths.get("testsrc/betamoon/worldgen/fixtures/noise_outputs.sha256");

    public static void main(String[] args) throws Exception {
        List<String> actual = outputs();
        if (args.length == 1 && args[0].equals("record")) {
            Files.write(FIXTURE, actual, StandardCharsets.UTF_8);
        } else if (!actual.equals(Files.readAllLines(FIXTURE, StandardCharsets.UTF_8))) {
            throw new AssertionError("Versioned noise output changed");
        }
    }

    public static void verify() throws Exception {
        main(new String[0]);
    }

    private static List<String> outputs() throws Exception {
        List<String> rows = new ArrayList<String>();
        for (String algorithm : new String[]{"value_noise_v1", "perlin_v1", "simplex_2d_v1", "beta_octaves_v1"}) {
            for (int variant = 0; variant < 3; variant++) {
                Map<String, Object> declaration = new LinkedHashMap<String, Object>();
                declaration.put("algorithm", algorithm);
                declaration.put("firstOctave", variant == 0 ? -3 : variant == 1 ? 0 : 16);
                declaration.put("amplitudes",
                        variant == 0
                                ? Arrays.asList(1.0, 0.4)
                                : variant == 1 ? Arrays.asList(-0.5, 0.0, 1.0, 0.25) : Arrays.asList(1.0));
                declaration.put("normalization", variant == 1 ? "none" : "bounded");
                NoiseDefinition noise = new NoiseDefinition("test:noise/compatibility", declaration);
                for (long seed : new long[]{42, -5852049645459666272L, Long.MIN_VALUE}) {
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    ByteBuffer bits = ByteBuffer.allocate(8);
                    Random positions = new Random(13249);
                    double[] edges = {0, -0.0, 1, -1, 1.0e12, -1.0e12, 29999999.5, -29999999.5, Math.nextAfter(1, 0),
                            Math.nextAfter(-1, 0)};
                    for (int index = 0; index < 1024; index++) {
                        double x = index < edges.length ? edges[index] : (positions.nextDouble() - 0.5) * 60000000;
                        double y = index < edges.length
                                ? edges[edges.length - index - 1]
                                : positions.nextDouble() * 128;
                        double z = index < edges.length ? -edges[index] : (positions.nextDouble() - 0.5) * 60000000;
                        bits.clear();
                        double point = noise.sample(seed, x, y, z);
                        double prepared = noise.prepareColumn(seed, x, z).applyAsDouble(y);
                        if (Double.doubleToLongBits(point) != Double.doubleToLongBits(prepared)) {
                            throw new AssertionError("Prepared noise column changed exact output: " + algorithm);
                        }
                        bits.putLong(Double.doubleToLongBits(point));
                        digest.update(bits.array());
                    }
                    StringBuilder hash = new StringBuilder();
                    for (byte value : digest.digest()) {
                        hash.append(String.format(Locale.ROOT, "%02x", value & 255));
                    }
                    rows.add(algorithm + " " + variant + " " + seed + " " + hash);
                }
            }
        }
        return rows;
    }
}

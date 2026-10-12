package betamoon.fluid.contact;

import betamoon.fluid.FluidRegistration;
import betamoon.fluid.FluidRegistry;
import betamoon.fluid.FluidState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.src.AxisAlignedBB;
import net.minecraft.src.MathHelper;
import net.minecraft.src.World;

/** Resolves registered fluid identity without relying on temporary carrier materials. */
public final class FluidContactResolver {
    private static final double VOLUME_EPSILON = 1.0E-9D;
    private static final int MAX_BODY_CELLS = 63;
    private static final int MAX_FLUIDS = 8;

    private FluidContactResolver() {
    }

    public static FluidContactSnapshot resolve(World world, AxisAlignedBB body, double eyeX, double eyeY,
            double eyeZ) {
        if (world == null || body == null) {
            throw new NullPointerException("Fluid contact world and body");
        }
        BodyResult bodyResult = bodyContacts(world, body);
        return new FluidContactSnapshot(bodyResult.contacts, dominant(bodyResult.contacts),
                eyeFluid(world, eyeX, eyeY, eyeZ), bodyResult.approximate, bodyResult.cellsRead + 1);
    }

    private static BodyResult bodyContacts(World world, AxisAlignedBB body) {
        Map<FluidRegistration, MutableContact> grouped = new IdentityHashMap<FluidRegistration, MutableContact>();
        int minX = MathHelper.floor_double(body.minX);
        int maxX = MathHelper.floor_double(Math.nextAfter(body.maxX, Double.NEGATIVE_INFINITY));
        int minY = MathHelper.floor_double(body.minY);
        int maxY = MathHelper.floor_double(Math.nextAfter(body.maxY, Double.NEGATIVE_INFINITY));
        int minZ = MathHelper.floor_double(body.minZ);
        int maxZ = MathHelper.floor_double(Math.nextAfter(body.maxZ, Double.NEGATIVE_INFINITY));

        if (maxX < minX || maxY < minY || maxZ < minZ) {
            return new BodyResult(Collections.<FluidBodyContact>emptyList(), false, 0);
        }
        long spanX = (long) maxX - minX + 1L;
        long spanY = (long) maxY - minY + 1L;
        long spanZ = (long) maxZ - minZ + 1L;
        boolean approximate = spanX > MAX_BODY_CELLS || spanY > MAX_BODY_CELLS
                || spanZ > MAX_BODY_CELLS || spanX * spanY * spanZ > MAX_BODY_CELLS;
        int countX = (int) (approximate ? Math.min(spanX, 4L) : spanX);
        int countY = (int) (approximate ? Math.min(spanY, 4L) : spanY);
        int countZ = (int) (approximate ? Math.min(spanZ, Math.max(1, MAX_BODY_CELLS / (countX * countY)))
                : spanZ);
        int reads = 0;

        for (int ix = 0; ix < countX; ix++) {
            int x = sample(minX, maxX, ix, countX);
            for (int iy = 0; iy < countY; iy++) {
                int y = sample(minY, maxY, iy, countY);
                for (int iz = 0; iz < countZ; iz++) {
                    int z = sample(minZ, maxZ, iz, countZ);
                    reads++;
                    if (!world.blockExists(x, y, z)) {
                        continue;
                    }
                    FluidRegistration fluid = FluidRegistry.instance().getByBlockId(world.getBlockId(x, y, z));
                    if (fluid == null) {
                        continue;
                    }
                    double volume = intersectionVolume(body, x, y, z,
                            FluidState.of(world.getBlockMetadata(x, y, z)).surfaceHeight());
                    if (volume <= VOLUME_EPSILON) {
                        continue;
                    }
                    MutableContact contact = grouped.get(fluid);
                    if (contact == null) {
                        grouped.put(fluid, new MutableContact(fluid, volume, x, y, z));
                    } else {
                        contact.add(volume, x, y, z);
                    }
                }
            }
        }

        List<FluidBodyContact> result = new ArrayList<FluidBodyContact>();
        for (MutableContact contact : grouped.values()) {
            result.add(contact.freeze());
        }
        Collections.sort(result, CONTACT_ORDER);
        if (result.size() > MAX_FLUIDS) {
            Collections.sort(result, DOMINANCE);
            result = new ArrayList<FluidBodyContact>(result.subList(0, MAX_FLUIDS));
            Collections.sort(result, CONTACT_ORDER);
        }
        return new BodyResult(result, approximate, reads);
    }

    private static int sample(int minimum, int maximum, int index, int count) {
        long span = (long) maximum - minimum + 1L;
        return (int) (minimum + (span * (2 * index + 1)) / (2 * count));
    }

    private static double intersectionVolume(AxisAlignedBB body, int x, int y, int z, float height) {
        double width = overlap(body.minX, body.maxX, x, x + 1.0D);
        double depth = overlap(body.minZ, body.maxZ, z, z + 1.0D);
        double vertical = overlap(body.minY, body.maxY, y, y + height);
        return width * depth * vertical;
    }

    private static double overlap(double firstMin, double firstMax, double secondMin, double secondMax) {
        return Math.max(0.0D, Math.min(firstMax, secondMax) - Math.max(firstMin, secondMin));
    }

    private static FluidBodyContact dominant(List<FluidBodyContact> contacts) {
        FluidBodyContact dominant = null;
        for (FluidBodyContact contact : contacts) {
            if (dominant == null || DOMINANCE.compare(contact, dominant) < 0) {
                dominant = contact;
            }
        }
        return dominant;
    }

    private static FluidRegistration eyeFluid(World world, double eyeX, double eyeY, double eyeZ) {
        int x = MathHelper.floor_double(eyeX);
        int y = MathHelper.floor_double(eyeY);
        int z = MathHelper.floor_double(eyeZ);
        if (!world.blockExists(x, y, z)) {
            return null;
        }
        FluidRegistration fluid = FluidRegistry.instance().getByBlockId(world.getBlockId(x, y, z));
        if (fluid == null) {
            return null;
        }
        double surface = y + FluidState.of(world.getBlockMetadata(x, y, z)).surfaceHeight();
        return eyeY >= y && eyeY < surface ? fluid : null;
    }

    private static int compareCoordinates(FluidBodyContact first, FluidBodyContact second) {
        int result = Integer.compare(first.firstX(), second.firstX());
        if (result == 0) {
            result = Integer.compare(first.firstY(), second.firstY());
        }
        if (result == 0) {
            result = Integer.compare(first.firstZ(), second.firstZ());
        }
        return result;
    }

    private static final Comparator<FluidBodyContact> CONTACT_ORDER = new Comparator<FluidBodyContact>() {
        @Override
        public int compare(FluidBodyContact first, FluidBodyContact second) {
            int result = first.fluid().key().toString().compareTo(second.fluid().key().toString());
            return result != 0 ? result : compareCoordinates(first, second);
        }
    };

    private static final Comparator<FluidBodyContact> DOMINANCE = new Comparator<FluidBodyContact>() {
        @Override
        public int compare(FluidBodyContact first, FluidBodyContact second) {
            int result = Double.compare(second.volume(), first.volume());
            if (result == 0) {
                result = compareCoordinates(first, second);
            }
            if (result == 0) {
                result = first.fluid().key().toString().compareTo(second.fluid().key().toString());
            }
            return result;
        }
    };

    private static final class MutableContact {
        private final FluidRegistration fluid;
        private double volume;
        private int firstX;
        private int firstY;
        private int firstZ;

        private MutableContact(FluidRegistration fluid, double volume, int x, int y, int z) {
            this.fluid = fluid;
            this.volume = volume;
            firstX = x;
            firstY = y;
            firstZ = z;
        }

        private void add(double additionalVolume, int x, int y, int z) {
            volume += additionalVolume;
            if (x < firstX || x == firstX && (y < firstY || y == firstY && z < firstZ)) {
                firstX = x;
                firstY = y;
                firstZ = z;
            }
        }

        private FluidBodyContact freeze() {
            return new FluidBodyContact(fluid, volume, firstX, firstY, firstZ);
        }
    }

    private static final class BodyResult {
        private final List<FluidBodyContact> contacts;
        private final boolean approximate;
        private final int cellsRead;

        private BodyResult(List<FluidBodyContact> contacts, boolean approximate, int cellsRead) {
            this.contacts = contacts;
            this.approximate = approximate;
            this.cellsRead = cellsRead;
        }
    }
}

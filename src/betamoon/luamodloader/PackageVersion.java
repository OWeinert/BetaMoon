package betamoon.luamodloader;

/** Numeric three-part package versions used only by minimum dependency checks. */
final class PackageVersion implements Comparable<PackageVersion> {
    private final String[] parts;

    private PackageVersion(String[] parts) {
        this.parts = parts;
    }

    static PackageVersion parse(String value) {
        if (value == null || !value.matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")) {
            throw new IllegalArgumentException("version must contain three non-negative numeric parts (major.minor.patch)");
        }
        return new PackageVersion(value.split("\\."));
    }

    @Override
    public int compareTo(PackageVersion other) {
        for (int index = 0; index < parts.length; index++) {
            int length = Integer.compare(parts[index].length(), other.parts[index].length());
            if (length != 0) {
                return length;
            }
            int digits = parts[index].compareTo(other.parts[index]);
            if (digits != 0) {
                return digits;
            }
        }
        return 0;
    }
}

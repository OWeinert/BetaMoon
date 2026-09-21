package betamoon.update;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strict SemVer 2.0.0 syntax and precedence, without machine-integer limits.
 */
public final class SemanticVersion {
    private static final String NUMBER = "(0|[1-9][0-9]*)";
    private static final Pattern SYNTAX = Pattern.compile(NUMBER + "\\." + NUMBER + "\\." + NUMBER
            + "(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?" + "(?:\\+([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?");

    private final String text;
    private final String[] core;
    private final String[] prerelease;

    private SemanticVersion(String text, String[] core, String[] prerelease) {
        this.text = text;
        this.core = core;
        this.prerelease = prerelease;
    }

    public static SemanticVersion parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Missing semantic version");
        }
        Matcher matcher = SYNTAX.matcher(text);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid semantic version");
        }
        String[] prerelease = matcher.group(4) == null ? new String[0] : matcher.group(4).split("\\.");
        for (String identifier : prerelease) {
            if (numeric(identifier) && identifier.length() > 1 && identifier.charAt(0) == '0') {
                throw new IllegalArgumentException("Leading zero in numeric prerelease identifier");
            }
        }
        return new SemanticVersion(text, new String[]{matcher.group(1), matcher.group(2), matcher.group(3)},
                prerelease);
    }

    public int comparePrecedence(SemanticVersion other) {
        for (int i = 0; i < core.length; i++) {
            int comparison = compareNumbers(core[i], other.core[i]);
            if (comparison != 0) {
                return comparison;
            }
        }
        if (prerelease.length == 0 || other.prerelease.length == 0) {
            if (prerelease.length == other.prerelease.length) {
                return 0;
            }
            return prerelease.length == 0 ? 1 : -1;
        }
        for (int i = 0; i < Math.min(prerelease.length, other.prerelease.length); i++) {
            String left = prerelease[i];
            String right = other.prerelease[i];
            boolean leftNumeric = numeric(left);
            boolean rightNumeric = numeric(right);
            int comparison;
            if (leftNumeric && rightNumeric) {
                comparison = compareNumbers(left, right);
            } else if (leftNumeric != rightNumeric) {
                comparison = leftNumeric ? -1 : 1;
            } else {
                comparison = left.compareTo(right);
            }
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(prerelease.length, other.prerelease.length);
    }

    private static int compareNumbers(String left, String right) {
        int length = Integer.compare(left.length(), right.length());
        return length == 0 ? left.compareTo(right) : length;
    }

    private static boolean numeric(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < '0' || value.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return text;
    }
}

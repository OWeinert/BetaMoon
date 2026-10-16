package betamoon.multiblock;

import java.util.ArrayList;
import java.util.List;

/**
 * One detached result; publishing a report never changes physical world state.
 */
public final class MultiblockReport {
    public String status = "unavailable";
    public String reason = "checking";
    public int checked;
    public int required;
    public int repeats;
    public int width;
    public int height;
    public int depth;
    public boolean truncated;
    public boolean mirror;
    public final List<Issue> issues = new ArrayList<Issue>();
    public final List<Count> constraints = new ArrayList<Count>();
    public final List<Match> matches = new ArrayList<Match>();

    public void issue(Issue issue) {
        if (issues.size() < 64) {
            issues.add(issue);
        } else {
            truncated = true;
        }
    }

    public static final class Match {
        public final int x;
        public final int y;
        public final int z;
        public final String symbol;
        public final int blockId;
        public final int metadata;

        public Match(int x, int y, int z, String symbol, int blockId, int metadata) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.symbol = symbol;
            this.blockId = blockId;
            this.metadata = metadata;
        }
    }

    public static final class Issue {
        public final int x;
        public final int y;
        public final int z;
        public final MultiblockDefinition.Cell cell;
        public final String reason;
        public final int blockId;
        public final int metadata;

        public Issue(int x, int y, int z, MultiblockDefinition.Cell cell, String reason, int blockId, int metadata) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.cell = cell;
            this.reason = reason;
            this.blockId = blockId;
            this.metadata = metadata;
        }
    }

    public static final class Count {
        public final MultiblockDefinition.Constraint constraint;
        public final int found;
        public final boolean passed;

        public Count(MultiblockDefinition.Constraint constraint, int found, boolean passed) {
            this.constraint = constraint;
            this.found = found;
            this.passed = passed;
        }
    }
}

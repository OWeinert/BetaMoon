package betamoon.multiblock;

import betamoon.luaapi.block.BlockStateSchema;
import betamoon.multiblock.MultiblockDefinition.Cell;
import betamoon.multiblock.MultiblockDefinition.Constraint;
import betamoon.multiblock.MultiblockDefinition.Variant;
import betamoon.multiblock.MultiblockReport.Match;

/**
 * Resumable loaded-only matching. Callers discard a job whenever its dependency
 * revision changes.
 */
public final class MultiblockMatchJob {
    public interface Reader {
        boolean loaded(int x, int z);

        int blockId(int x, int y, int z);

        int metadata(int x, int y, int z);
    }

    private final MultiblockDefinition definition;
    private final Reader reader;
    private final int x;
    private final int y;
    private final int z;
    private final int facing;
    private boolean mirror;
    private final boolean searchMirrors;
    private final boolean detailed;
    private final Integer requestedRepeats;
    private int variantIndex;
    private int preflight;
    private int cursor;
    private int constraintIndex;
    private int constraintCursor;
    private int count;
    private Object same;
    private boolean consistent = true;
    private boolean failed;
    private boolean done;
    private MultiblockReport report;

    public MultiblockMatchJob(MultiblockDefinition definition, Reader reader, int x, int y, int z, int facing,
            boolean mirror, boolean detailed, Integer requestedRepeats) {
        this(definition, reader, x, y, z, facing, mirror, detailed, requestedRepeats, false);
    }

    public MultiblockMatchJob(MultiblockDefinition definition, Reader reader, int x, int y, int z, int facing,
            boolean mirror, boolean detailed, Integer requestedRepeats, boolean searchMirrors) {
        if (mirror && !definition.mirror) {
            throw new IllegalArgumentException("Mirroring is not enabled for " + definition.key);
        }
        this.definition = definition;
        this.reader = reader;
        this.x = x;
        this.y = y;
        this.z = z;
        this.facing = facing;
        this.mirror = mirror;
        this.searchMirrors = searchMirrors && definition.mirror;
        this.detailed = detailed;
        this.requestedRepeats = requestedRepeats;
        if (requestedRepeats != null) {
            while (variantIndex < definition.variants.size()
                    && definition.variants.get(variantIndex).repeats != requestedRepeats.intValue()) {
                variantIndex++;
            }
            if (variantIndex == definition.variants.size()) {
                throw new IllegalArgumentException("Repeat count is outside the declared range");
            }
        }
        reset();
        if (!inBounds(x, y, z)) {
            report.status = "incomplete";
            report.reason = "out_of_bounds";
            for (Cell cell : variant().cells) {
                if (cell.symbol.equals("@")) {
                    report.issue(new MultiblockReport.Issue(x, y, z, cell, report.reason, -1, -1));
                    break;
                }
            }
            done = true;
        }
    }

    private void reset() {
        Variant variant = variant();
        report = new MultiblockReport();
        report.required = variant.cells.size();
        report.mirror = mirror;
        report.repeats = variant.repeats;
        report.width = variant.width;
        report.height = variant.height;
        report.depth = variant.depth;
        preflight = 0;
        cursor = 0;
        constraintIndex = 0;
        constraintCursor = 0;
        count = 0;
        same = null;
        consistent = true;
        failed = false;
    }

    public boolean done() {
        return done;
    }

    public MultiblockReport report() {
        return report;
    }

    public int advance(int budget) {
        int spent = 0;
        while (!done && spent < budget) {
            Variant variant = variant();
            if (preflight < variant.cells.size()) {
                Cell cell = variant.cells.get(preflight++);
                int px = worldX(cell);
                int py = y + cell.y;
                int pz = worldZ(cell);
                spent++;
                if (!inBounds(px, py, pz)) {
                    report.issue(new MultiblockReport.Issue(px, py, pz, cell, "out_of_bounds", -1, -1));
                    failed = true;
                } else if (!reader.loaded(px, pz)) {
                    report.reason = "chunk_unavailable";
                    report.issue(new MultiblockReport.Issue(px, py, pz, cell, report.reason, -1, -1));
                    done = true;
                }
                if (preflight == variant.cells.size() && failed && !done) {
                    mismatch("out_of_bounds");
                }
                continue;
            }
            if (cursor < variant.cells.size()) {
                Cell cell = variant.cells.get(cursor++);
                int px = worldX(cell);
                int py = y + cell.y;
                int pz = worldZ(cell);
                spent++;
                if (!reader.loaded(px, pz)) {
                    report.status = "unavailable";
                    report.reason = "chunk_unavailable";
                    done = true;
                    continue;
                }
                int id = reader.blockId(px, py, pz);
                int metadata = reader.metadata(px, py, pz);
                report.checked++;
                report.matches.add(new Match(px, py, pz, cell.symbol, id, metadata));
                if (!cell.rule.matches(id, metadata, facing, mirror)) {
                    failed = true;
                    report.issue(new MultiblockReport.Issue(px, py, pz, cell, "cell_mismatch", id, metadata));
                    if (!detailed) {
                        mismatch("cell_mismatch");
                    }
                }
                continue;
            }
            if (failed && (constraintIndex == 0 || !detailed)) {
                mismatch(constraintIndex > 0 ? "constraint_mismatch" : "cell_mismatch");
                continue;
            }
            if (constraintIndex < definition.constraints.size()) {
                Constraint constraint = definition.constraints.get(constraintIndex);
                if (constraintCursor < report.matches.size()) {
                    Match match = report.matches.get(constraintCursor++);
                    spent++;
                    if (constraint.cells.contains(match.symbol)) {
                        if (constraint.allSame != null) {
                            Object observed = value(constraint, match);
                            consistent &= observed != null && (same == null || same.equals(observed));
                            same = observed;
                            count++;
                        } else if (constraint.rule.matches(match.blockId, match.metadata, facing, mirror)) {
                            count++;
                        }
                    }
                } else {
                    boolean passed = constraint.allSame == null
                            ? count >= constraint.min && count <= constraint.max
                            : consistent;
                    report.constraints.add(new MultiblockReport.Count(constraint, count, passed));
                    failed |= !passed;
                    constraintIndex++;
                    constraintCursor = 0;
                    count = 0;
                    same = null;
                    consistent = true;
                }
                continue;
            }
            if (failed) {
                mismatch("constraint_mismatch");
                continue;
            }
            report.status = "complete";
            report.reason = "complete";
            done = true;
        }
        return spent;
    }

    private Object value(Constraint constraint, Match match) {
        String field = constraint.allSame;
        if (field.equals("block")) {
            return Integer.valueOf(match.blockId);
        }
        if (field.equals("metadata")) {
            return Integer.valueOf(match.metadata);
        }
        BlockStateSchema schema = constraint.states.get(match.blockId);
        return schema == null ? null : schema.get(match.metadata, field.substring("state:".length()));
    }

    private void mismatch(String reason) {
        if (searchMirrors && !mirror) {
            mirror = true;
            reset();
        } else if (requestedRepeats == null && variantIndex + 1 < definition.variants.size()) {
            mirror = false;
            variantIndex++;
            reset();
        } else {
            report.status = "incomplete";
            report.reason = reason;
            done = true;
        }
    }

    private Variant variant() {
        return definition.variants.get(variantIndex);
    }

    public int worldX(Cell cell) {
        return x + MultiblockOrientation.x(cell.x, cell.z, facing, mirror);
    }

    public int worldZ(Cell cell) {
        return z + MultiblockOrientation.z(cell.x, cell.z, facing, mirror);
    }

    public static boolean inBounds(int x, int y, int z) {
        return y >= 0 && y < 128 && x >= -32000000 && x < 32000000 && z >= -32000000 && z < 32000000;
    }
}

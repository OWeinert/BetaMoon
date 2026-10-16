package betamoon.multiblock;

import betamoon.multiblock.MultiblockDefinition.Cell;
import betamoon.multiblock.MultiblockDefinition.Variant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resumable finite search shared by automatic discovery, item triggers and pure
 * Lua queries.
 */
public final class MultiblockSearch {
    public interface ReaderFactory {
        MultiblockMatchJob.Reader reader(MultiblockPlacement placement);
    }

    public static final class Match {
        public final MultiblockPlacement placement;
        public final MultiblockReport report;

        Match(MultiblockPlacement placement, MultiblockReport report) {
            this.placement = placement;
            this.report = report;
        }
    }

    public static final class Seed {
        public final MultiblockDefinition definition;
        public final Cell cell;
        public final Variant variant;
        public final int facing;
        public final boolean mirror;

        public Seed(MultiblockDefinition definition, Cell cell, Variant variant, int facing, boolean mirror) {
            this.definition = definition;
            this.cell = cell;
            this.variant = variant;
            this.facing = facing;
            this.mirror = mirror;
        }

        public MultiblockPlacement at(MultiblockPosition point) {
            return new MultiblockPlacement(definition,
                    new MultiblockPosition(point.x - MultiblockOrientation.x(cell.x, cell.z, facing, mirror),
                            point.y - cell.y, point.z - MultiblockOrientation.z(cell.x, cell.z, facing, mirror)),
                    facing, mirror, variant);
        }
    }

    public static final class Bounds {
        public final int minX;
        public final int minY;
        public final int minZ;
        public final int maxX;
        public final int maxY;
        public final int maxZ;

        public Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this(minX, minY, minZ, maxX, maxY, maxZ, true);
        }

        public Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean searchVolume) {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("Search bounds must have minimum <= maximum");
            }
            long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
            if (volume < 1 || searchVolume && volume > 32768 || !MultiblockMatchJob.inBounds(minX, minY, minZ)
                    || !MultiblockMatchJob.inBounds(maxX, maxY, maxZ)) {
                throw new IllegalArgumentException("Search bounds require 1..32768 in-world reference positions");
            }
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        public boolean contains(MultiblockPosition point) {
            return point.x >= minX && point.x <= maxX && point.y >= minY && point.y <= maxY && point.z >= minZ
                    && point.z <= maxZ;
        }

        public boolean contains(MultiblockPlacement placement) {
            return placement.minX >= minX && placement.maxX <= maxX && placement.minY >= minY && placement.maxY <= maxY
                    && placement.minZ >= minZ && placement.maxZ <= maxZ;
        }
    }

    private final Iterator<MultiblockPlacement> candidates;
    private final ReaderFactory readers;
    private final int maxMatches;
    private final Set<String> seen = new LinkedHashSet<>();
    private final Map<String, Match> matches = new LinkedHashMap<>();
    private MultiblockPlacement current;
    private MultiblockMatchJob job;
    private MultiblockMatchJob.Reader pruneReader;
    private int pruneCursor;
    private boolean missing;
    private boolean done;
    private String reason = "complete";
    private boolean unavailable;

    public MultiblockSearch(Iterator<MultiblockPlacement> candidates, ReaderFactory readers, int maxMatches) {
        this.candidates = candidates;
        this.readers = readers;
        this.maxMatches = maxMatches;
    }

    public int advance(int budget) {
        int spent = 0;
        while (!done && spent < budget) {
            if (current == null) {
                if (!candidates.hasNext()) {
                    done = true;
                    break;
                }
                current = candidates.next();
                spent++;
                if (current == null) {
                    continue;
                }
                if (!seen.add(current.key)) {
                    current = null;
                    continue;
                }
                if (seen.size() > 8192) {
                    reason = "search_budget_exceeded";
                    done = true;
                    break;
                }
                pruneReader = readers.reader(current);
                pruneCursor = 0;
                missing = false;
            }
            if (job == null && pruneCursor < current.variant.cells.size()) {
                Cell cell = current.variant.cells.get(pruneCursor++);
                MultiblockPosition point = current.point(cell);
                spent++;
                if (!MultiblockMatchJob.inBounds(point.x, point.y, point.z)) {
                    current = null;
                } else if (!pruneReader.loaded(point.x, point.z)) {
                    missing = true;
                } else if (!cell.rule.matches(pruneReader.blockId(point.x, point.y, point.z),
                        pruneReader.metadata(point.x, point.y, point.z), current.facing, current.mirror)) {
                    current = null;
                }
                continue;
            }
            if (job == null) {
                if (missing) {
                    unavailable = true;
                    current = null;
                    continue;
                }
                job = new MultiblockMatchJob(current.definition, pruneReader, current.anchor.x, current.anchor.y,
                        current.anchor.z, current.facing, current.mirror, false, current.variant.repeats);
            }
            spent += job.advance(Math.min(256, Math.max(0, budget - spent)));
            if (job.done()) {
                MultiblockReport report = job.report();
                unavailable |= report.status.equals("unavailable");
                if (report.status.equals("complete")) {
                    if (matches.size() >= maxMatches) {
                        reason = "result_limit";
                        done = true;
                    } else {
                        matches.put(current.key, new Match(current, report));
                    }
                }
                job = null;
                current = null;
            }
        }
        if (!done && current == null && !candidates.hasNext()) {
            done = true;
        }
        return spent;
    }

    public boolean done() {
        return done;
    }

    public boolean complete() {
        return done && reason.equals("complete") && !unavailable;
    }

    public String reason() {
        return !done
                ? "search_budget_exceeded"
                : !reason.equals("complete") ? reason : unavailable ? "chunk_unavailable" : "complete";
    }

    public List<Match> matches() {
        List<Match> result = new ArrayList<>(matches.values());
        result.sort((left, right) -> left.placement.order().compareTo(right.placement.order()));
        return result;
    }

    public static List<Seed> seeds(MultiblockDefinition definition, String selected, List<Integer> facings,
            boolean mirrors, Integer repeats) {
        List<Seed> result = new ArrayList<>();
        for (Variant variant : definition.variants) {
            if (repeats != null && repeats != variant.repeats) {
                continue;
            }
            for (int facing : facings) {
                for (int reflection = 0; reflection < (mirrors ? 2 : 1); reflection++) {
                    for (Cell cell : variant.cells) {
                        if (selected.contains(cell.symbol)) {
                            result.add(new Seed(definition, cell, variant, facing, reflection == 1));
                        }
                    }
                }
            }
        }
        return result;
    }

    public static Iterator<MultiblockPlacement> near(List<Seed> seeds, MultiblockPosition point, Bounds bounds,
            Bounds containedIn) {
        Iterator<Seed> iterator = seeds.iterator();
        return new Iterator<MultiblockPlacement>() {
            public boolean hasNext() {
                return iterator.hasNext();
            }

            public MultiblockPlacement next() {
                MultiblockPlacement placement = iterator.next().at(point);
                return (bounds == null || bounds.contains(placement.anchor))
                        && (containedIn == null || containedIn.contains(placement)) ? placement : null;
            }
        };
    }

    public static Iterator<MultiblockPlacement> within(MultiblockDefinition definition, Bounds bounds,
            Bounds containedIn, List<Integer> facings, boolean mirrors, Integer repeats) {
        List<Seed> transforms = seeds(definition, "@", facings, mirrors, repeats);
        if (transforms.isEmpty()) {
            return Collections.emptyIterator();
        }
        return new Iterator<MultiblockPlacement>() {
            private int x = bounds.minX;
            private int y = bounds.minY;
            private int z = bounds.minZ;
            private int transform;

            public boolean hasNext() {
                return y <= bounds.maxY;
            }

            public MultiblockPlacement next() {
                Seed seed = transforms.get(transform++);
                MultiblockPlacement result = seed.at(new MultiblockPosition(x, y, z));
                if (transform == transforms.size()) {
                    transform = 0;
                    if (++x > bounds.maxX) {
                        x = bounds.minX;
                        if (++z > bounds.maxZ) {
                            z = bounds.minZ;
                            y++;
                        }
                    }
                }
                return containedIn == null || containedIn.contains(result) ? result : null;
            }
        };
    }
}

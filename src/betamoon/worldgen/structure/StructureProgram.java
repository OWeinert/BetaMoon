package betamoon.worldgen.structure;

import betamoon.worldgen.BlockPosition;
import betamoon.worldgen.SeedMixer;
import betamoon.worldgen.WorldGenLimits;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.src.Block;

/** Immutable, bounded placement-time variation program compiled from a structure document. */
final class StructureProgram {
    private static final Direction[] DIRECTIONS = Direction.values();

    private final List<Action> actions;
    private final List<Processor> processors;

    StructureProgram(List<Action> actions, List<Processor> processors) {
        this.actions = immutable(actions);
        this.processors = immutable(processors);
    }

    Resolved resolve(long placementSeed, List<StructureTemplate.PaletteEntry> palette) {
        State state = new State(palette);
        for (Action action : actions) {
            action.apply(state, placementSeed);
        }
        for (Processor processor : processors) {
            processor.apply(state, placementSeed);
        }
        return state.freeze();
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }

    interface Action {
        void apply(State state, long seed);
    }

    static final class WriteAction implements Action {
        private final List<CellWrite> writes;

        WriteAction(List<CellWrite> writes) {
            this.writes = immutable(writes);
        }

        @Override
        public void apply(State state, long seed) {
            for (CellWrite write : writes) {
                MutableCell previous = state.cells.get(write.position);
                if (previous == null || write.replace) {
                    state.cells.put(write.position, new MutableCell(write.state, write.tags, write.transform));
                } else if (previous.state == write.state) {
                    previous.elementTags.addAll(write.tags);
                } else {
                    throw new IllegalStateException("Compiled structure has a conflicting conditional write at "
                            + coordinate(write.position));
                }
            }
        }
    }

    static final class ClearAction implements Action {
        private final List<BlockPosition> positions;

        ClearAction(List<BlockPosition> positions) {
            this.positions = immutable(positions);
        }

        @Override
        public void apply(State state, long seed) {
            for (BlockPosition position : positions) {
                state.cells.remove(position);
            }
        }
    }

    static final class ReplaceStateAction implements Action {
        private final List<BlockPosition> positions;
        private final Set<Integer> replace;
        private final int with;

        ReplaceStateAction(List<BlockPosition> positions, Set<Integer> replace, int with) {
            this.positions = immutable(positions);
            this.replace = Collections.unmodifiableSet(new LinkedHashSet<Integer>(replace));
            this.with = with;
        }

        @Override
        public void apply(State state, long seed) {
            for (BlockPosition position : positions) {
                MutableCell cell = state.cells.get(position);
                if (cell != null && replace.contains(Integer.valueOf(cell.state))) {
                    cell.state = with;
                }
            }
        }
    }

    static final class MarkerAction implements Action {
        private final StructureTemplate.Marker marker;

        MarkerAction(StructureTemplate.Marker marker) {
            this.marker = marker;
        }

        @Override
        public void apply(State state, long seed) {
            state.markers.add(marker);
        }
    }

    static final class LootAction implements Action {
        private final StructureLoot loot;

        LootAction(StructureLoot loot) {
            this.loot = loot;
        }

        @Override
        public void apply(State state, long seed) {
            state.loots.add(loot);
        }
    }

    static final class ChanceAction implements Action {
        private final String key;
        private final double chance;
        private final List<Action> actions;

        ChanceAction(String key, double chance, List<Action> actions) {
            this.key = key;
            this.chance = chance;
            this.actions = immutable(actions);
        }

        @Override
        public void apply(State state, long seed) {
            if (sample(seed, key, null, "chance", 0) >= chance) {
                return;
            }
            for (Action action : actions) {
                action.apply(state, seed);
            }
        }
    }

    static final class ChoiceAction implements Action {
        private final String key;
        private final List<Choice> choices;
        private final int totalWeight;

        ChoiceAction(String key, List<Choice> choices, int totalWeight) {
            this.key = key;
            this.choices = immutable(choices);
            this.totalWeight = totalWeight;
        }

        @Override
        public void apply(State state, long seed) {
            int selected = (int) Math.floor(sample(seed, key, null, "choice", 0) * totalWeight);
            for (Choice choice : choices) {
                selected -= choice.weight;
                if (selected < 0) {
                    for (Action action : choice.actions) {
                        action.apply(state, seed);
                    }
                    return;
                }
            }
        }
    }

    static final class Choice {
        private final int weight;
        private final List<Action> actions;

        Choice(int weight, List<Action> actions) {
            this.weight = weight;
            this.actions = immutable(actions);
        }
    }

    static final class CellWrite {
        private final BlockPosition position;
        private final int state;
        private final Set<String> tags;
        private final StructureTransform transform;
        private final boolean replace;

        CellWrite(BlockPosition position, int state, Set<String> tags, StructureTransform transform,
                boolean replace) {
            this.position = position;
            this.state = state;
            this.tags = Collections.unmodifiableSet(new LinkedHashSet<String>(tags));
            this.transform = transform;
            this.replace = replace;
        }
    }

    static final class Resolved {
        final List<StructureTemplate.TemplateBlock> blocks;
        final List<StructureTemplate.Marker> markers;
        final List<StructureLoot> loots;

        private Resolved(List<StructureTemplate.TemplateBlock> blocks, List<StructureTemplate.Marker> markers,
                List<StructureLoot> loots) {
            this.blocks = Collections.unmodifiableList(blocks);
            this.markers = Collections.unmodifiableList(markers);
            this.loots = Collections.unmodifiableList(loots);
        }
    }

    static final class Processor {
        enum Type {
            RECOLOR,
            REPLACE,
            DECAY,
            ERODE,
            DEPOSIT
        }

        private final Type type;
        private final String key;
        private final Selector selector;
        private final double chance;
        private final Distribution distribution;
        private final Integer with;
        private final Map<Integer, Integer> mapping;
        private final List<WeightedState> weighted;
        private final int totalWeight;
        private final int iterations;
        private final int distance;
        private final String conflict;
        private final boolean allowProtected;

        Processor(Type type, String key, Selector selector, double chance, Distribution distribution, Integer with,
                Map<Integer, Integer> mapping, List<WeightedState> weighted, int totalWeight, int iterations,
                int distance, String conflict, boolean allowProtected) {
            this.type = type;
            this.key = key;
            this.selector = selector;
            this.chance = chance;
            this.distribution = distribution;
            this.with = with;
            this.mapping = Collections.unmodifiableMap(new LinkedHashMap<Integer, Integer>(mapping));
            this.weighted = immutable(weighted);
            this.totalWeight = totalWeight;
            this.iterations = iterations;
            this.distance = distance;
            this.conflict = conflict;
            this.allowProtected = allowProtected;
        }

        private void apply(State state, long seed) {
            if (type == Type.ERODE) {
                for (int iteration = 0; iteration < iterations; iteration++) {
                    remove(state, seed, iteration);
                }
                return;
            }
            Snapshot snapshot = state.snapshot();
            if (type == Type.DEPOSIT) {
                deposit(state, snapshot, seed);
                return;
            }
            List<BlockPosition> positions = new ArrayList<BlockPosition>();
            for (Map.Entry<BlockPosition, MutableCell> entry : snapshot.cells.entrySet()) {
                if (eligible(snapshot, entry.getKey(), entry.getValue(), seed, 0)) {
                    positions.add(entry.getKey());
                }
            }
            for (BlockPosition position : positions) {
                MutableCell cell = state.cells.get(position);
                if (cell == null) {
                    continue;
                }
                if (type == Type.RECOLOR) {
                    Integer target = with == null ? mapping.get(Integer.valueOf(cell.state)) : with;
                    if (target != null) {
                        cell.state = target.intValue();
                    }
                } else if (type == Type.REPLACE) {
                    cell.state = target(seed, position, 0);
                } else if (type == Type.DECAY) {
                    state.cells.remove(position);
                }
            }
        }

        private void remove(State state, long seed, int iteration) {
            Snapshot snapshot = state.snapshot();
            List<BlockPosition> removals = new ArrayList<BlockPosition>();
            for (Map.Entry<BlockPosition, MutableCell> entry : snapshot.cells.entrySet()) {
                if (eligible(snapshot, entry.getKey(), entry.getValue(), seed, iteration)) {
                    removals.add(entry.getKey());
                }
            }
            for (BlockPosition position : removals) {
                state.cells.remove(position);
            }
        }

        private void deposit(State state, Snapshot snapshot, long seed) {
            Map<BlockPosition, DepositCandidate> additions = new LinkedHashMap<BlockPosition, DepositCandidate>();
            for (Map.Entry<BlockPosition, MutableCell> entry : snapshot.cells.entrySet()) {
                BlockPosition source = entry.getKey();
                MutableCell cell = entry.getValue();
                // Deposit reads from its source without modifying it, so protected
                // geometry may still act as a support surface.
                if (!selector.matches(snapshot, source, cell, true)) {
                    continue;
                }
                for (Direction direction : DIRECTIONS) {
                    if (!selector.acceptsFace(direction) || snapshot.cells.containsKey(direction.offset(source, 1))) {
                        continue;
                    }
                    int faceSalt = direction.ordinal();
                    if (!distribution.matches(seed, key, source, faceSalt)
                            || sample(seed, key, source, "eligibility", faceSalt) >= chance) {
                        continue;
                    }
                    BlockPosition target = direction.offset(source, distance);
                    DepositCandidate candidate = new DepositCandidate(source, direction,
                            target(seed, target, faceSalt), Collections.<String>emptySet(), direction.orientation());
                    DepositCandidate previous = additions.get(target);
                    if (previous == null || DEPOSIT_ORDER.compare(candidate, previous) < 0) {
                        additions.put(target, candidate);
                    }
                }
            }
            for (Map.Entry<BlockPosition, DepositCandidate> entry : additions.entrySet()) {
                BlockPosition position = entry.getKey();
                if (Math.abs(position.x) > WorldGenLimits.MAX_FEATURE_RADIUS
                        || Math.abs(position.z) > WorldGenLimits.MAX_FEATURE_RADIUS
                        || Math.abs(position.y) > WorldGenLimits.MAX_HEIGHT) {
                    throw new IllegalStateException("Processor " + key + " deposited outside compiled bounds at "
                            + coordinate(position));
                }
                MutableCell occupied = state.cells.get(position);
                if (!allowProtected && Selector.protectedCell(snapshot, position, occupied)) {
                    continue;
                }
                if (occupied != null) {
                    if (conflict.equals("skip")) {
                        continue;
                    }
                    if (conflict.equals("error")) {
                        throw new IllegalStateException("Processor " + key + " deposit conflict at "
                                + coordinate(position));
                    }
                }
                DepositCandidate candidate = entry.getValue();
                state.cells.put(position, new MutableCell(candidate.state, candidate.tags, candidate.transform));
                if (state.cells.size() > WorldGenLimits.MAX_BLOCK_CHANGES_PER_FEATURE) {
                    throw new IllegalStateException("Processor " + key + " exceeded the structure cell budget");
                }
            }
        }

        private boolean eligible(Snapshot snapshot, BlockPosition position, MutableCell cell, long seed,
                int iteration) {
            return selector.matches(snapshot, position, cell, allowProtected)
                    && distribution.matches(seed, key, position, iteration)
                    && sample(seed, key, position, "eligibility", iteration) < chance;
        }

        private int target(long seed, BlockPosition position, int discriminator) {
            if (with != null) {
                return with.intValue();
            }
            int selected = (int) Math.floor(sample(seed, key, position, "target", discriminator) * totalWeight);
            for (WeightedState state : weighted) {
                selected -= state.weight;
                if (selected < 0) {
                    return state.state;
                }
            }
            return weighted.get(weighted.size() - 1).state;
        }
    }

    static final class WeightedState {
        final int state;
        final int weight;

        WeightedState(int state, int weight) {
            this.state = state;
            this.weight = weight;
        }
    }

    static final class Selector {
        private final Set<Integer> states;
        private final Set<Integer> excludedStates;
        private final Set<String> tags;
        private final boolean allTags;
        private final Set<String> excludedTags;
        private final BlockPosition minimum;
        private final BlockPosition maximum;
        private final Integer minHeight;
        private final Integer maxHeight;
        private final Set<Direction> faces;
        private final Boolean exposed;
        private final int minExposed;
        private final int maxExposed;
        private final MarkerDistance nearMarker;
        private final Set<String> excludedMarkers;

        Selector(Set<Integer> states, Set<Integer> excludedStates, Set<String> tags, boolean allTags,
                Set<String> excludedTags, BlockPosition minimum, BlockPosition maximum, Integer minHeight,
                Integer maxHeight, Set<Direction> faces, Boolean exposed, int minExposed, int maxExposed,
                MarkerDistance nearMarker, Set<String> excludedMarkers) {
            this.states = immutableSet(states);
            this.excludedStates = immutableSet(excludedStates);
            this.tags = immutableSet(tags);
            this.allTags = allTags;
            this.excludedTags = immutableSet(excludedTags);
            this.minimum = minimum;
            this.maximum = maximum;
            this.minHeight = minHeight;
            this.maxHeight = maxHeight;
            this.faces = immutableSet(faces);
            this.exposed = exposed;
            this.minExposed = minExposed;
            this.maxExposed = maxExposed;
            this.nearMarker = nearMarker;
            this.excludedMarkers = immutableSet(excludedMarkers);
        }

        private boolean matches(Snapshot snapshot, BlockPosition position, MutableCell cell,
                boolean allowProtected) {
            Set<String> effectiveTags = new LinkedHashSet<String>(cell.elementTags);
            effectiveTags.addAll(snapshot.palette.get(cell.state).tags);
            if (!allowProtected && protectedCell(snapshot, position, cell)) {
                return false;
            }
            if (!states.isEmpty() && !states.contains(Integer.valueOf(cell.state))
                    || excludedStates.contains(Integer.valueOf(cell.state))) {
                return false;
            }
            if (!tags.isEmpty() && (allTags ? !effectiveTags.containsAll(tags)
                    : Collections.disjoint(effectiveTags, tags))) {
                return false;
            }
            if (!Collections.disjoint(effectiveTags, excludedTags)) {
                return false;
            }
            if (minimum != null && (position.x < minimum.x || position.y < minimum.y || position.z < minimum.z
                    || position.x > maximum.x || position.y > maximum.y || position.z > maximum.z)) {
                return false;
            }
            if (minHeight != null && position.y < minHeight.intValue()
                    || maxHeight != null && position.y > maxHeight.intValue()) {
                return false;
            }
            int exposedFaces = exposedFaces(snapshot, position);
            if (exposed != null && exposed.booleanValue() != (exposedFaces > 0)
                    || exposedFaces < minExposed || exposedFaces > maxExposed) {
                return false;
            }
            if (!faces.isEmpty()) {
                boolean matchingFace = false;
                for (Direction face : faces) {
                    matchingFace |= !snapshot.cells.containsKey(face.offset(position, 1));
                }
                if (!matchingFace) {
                    return false;
                }
            }
            if (nearMarker != null && !nearMarker.matches(snapshot.markers, position)) {
                return false;
            }
            for (StructureTemplate.Marker marker : snapshot.markers) {
                if (marker.position.equals(position) && excludedMarkers.contains(marker.name)) {
                    return false;
                }
            }
            return true;
        }

        private boolean acceptsFace(Direction direction) {
            return faces.isEmpty() || faces.contains(direction);
        }

        Set<Direction> depositDirections() {
            return faces.isEmpty() ? new LinkedHashSet<Direction>(java.util.Arrays.asList(DIRECTIONS)) : faces;
        }

        private static boolean protectedCell(Snapshot snapshot, BlockPosition position, MutableCell cell) {
            if (snapshot.protectedPositions.contains(position)) {
                return true;
            }
            if (cell == null) {
                return false;
            }
            if (cell.elementTags.contains("protected")) {
                return true;
            }
            StructureTemplate.PaletteEntry paletteState = snapshot.palette.get(cell.state);
            return paletteState.tags.contains("protected")
                    || snapshot.tileEntityStates.contains(Integer.valueOf(cell.state));
        }

        private static int exposedFaces(Snapshot snapshot, BlockPosition position) {
            int result = 0;
            for (Direction direction : DIRECTIONS) {
                if (!snapshot.cells.containsKey(direction.offset(position, 1))) {
                    result++;
                }
            }
            return result;
        }

        private static <T> Set<T> immutableSet(Set<T> values) {
            return Collections.unmodifiableSet(new LinkedHashSet<T>(values));
        }
    }

    static final class MarkerDistance {
        private final String name;
        private final int distance;
        private final Object value;

        MarkerDistance(String name, int distance, Object value) {
            this.name = name;
            this.distance = distance;
            this.value = value;
        }

        private boolean matches(List<StructureTemplate.Marker> markers, BlockPosition position) {
            for (StructureTemplate.Marker marker : markers) {
                int separation = Math.abs(marker.position.x - position.x) + Math.abs(marker.position.y - position.y)
                        + Math.abs(marker.position.z - position.z);
                if (marker.name.equals(name) && separation <= distance
                        && (value == null || value.equals(marker.value))) {
                    return true;
                }
            }
            return false;
        }
    }

    interface Distribution {
        boolean matches(long seed, String key, BlockPosition position, int discriminator);
    }

    static final class IndependentDistribution implements Distribution {
        @Override
        public boolean matches(long seed, String key, BlockPosition position, int discriminator) {
            return true;
        }
    }

    static final class NoiseDistribution implements Distribution {
        private final int scale;
        private final double threshold;

        NoiseDistribution(int scale, double threshold) {
            this.scale = scale;
            this.threshold = threshold;
        }

        @Override
        public boolean matches(long seed, String key, BlockPosition position, int discriminator) {
            int cellX = Math.floorDiv(position.x, scale);
            int cellY = Math.floorDiv(position.y, scale);
            int cellZ = Math.floorDiv(position.z, scale);
            double x = smooth(Math.floorMod(position.x, scale) / (double) scale);
            double y = smooth(Math.floorMod(position.y, scale) / (double) scale);
            double z = smooth(Math.floorMod(position.z, scale) / (double) scale);
            double lowerNorth = interpolate(lattice(seed, key, cellX, cellY, cellZ),
                    lattice(seed, key, cellX + 1, cellY, cellZ), x);
            double lowerSouth = interpolate(lattice(seed, key, cellX, cellY, cellZ + 1),
                    lattice(seed, key, cellX + 1, cellY, cellZ + 1), x);
            double upperNorth = interpolate(lattice(seed, key, cellX, cellY + 1, cellZ),
                    lattice(seed, key, cellX + 1, cellY + 1, cellZ), x);
            double upperSouth = interpolate(lattice(seed, key, cellX, cellY + 1, cellZ + 1),
                    lattice(seed, key, cellX + 1, cellY + 1, cellZ + 1), x);
            double lower = interpolate(lowerNorth, lowerSouth, z);
            double upper = interpolate(upperNorth, upperSouth, z);
            return interpolate(lower, upper, y) >= threshold;
        }

        private static double lattice(long seed, String key, int x, int y, int z) {
            return sample(seed, key, new BlockPosition(x, y, z), "noise", 0);
        }

        private static double smooth(double value) {
            return value * value * (3.0D - 2.0D * value);
        }

        private static double interpolate(double from, double to, double amount) {
            return from + (to - from) * amount;
        }
    }

    static final class ClusterDistribution implements Distribution {
        private final int radius;
        private final double density;
        private final double falloff;

        ClusterDistribution(int radius, double density, double falloff) {
            this.radius = radius;
            this.density = density;
            this.falloff = falloff;
        }

        @Override
        public boolean matches(long seed, String key, BlockPosition position, int discriminator) {
            int spacing = radius * 2 + 1;
            int baseX = Math.floorDiv(position.x, spacing);
            int baseY = Math.floorDiv(position.y, spacing);
            int baseZ = Math.floorDiv(position.z, spacing);
            for (int x = baseX - 1; x <= baseX + 1; x++) {
                for (int y = baseY - 1; y <= baseY + 1; y++) {
                    for (int z = baseZ - 1; z <= baseZ + 1; z++) {
                        BlockPosition cell = new BlockPosition(x, y, z);
                        if (sample(seed, key, cell, "cluster_presence", 0) >= density) {
                            continue;
                        }
                        int centerX = x * spacing + offset(seed, key, cell, "cluster_x", spacing);
                        int centerY = y * spacing + offset(seed, key, cell, "cluster_y", spacing);
                        int centerZ = z * spacing + offset(seed, key, cell, "cluster_z", spacing);
                        double distance = Math.sqrt(square(position.x - centerX) + square(position.y - centerY)
                                + square(position.z - centerZ));
                        if (distance <= radius && sample(seed, key, position, "cluster_falloff", discriminator)
                                < 1.0D - falloff * distance / Math.max(1.0D, radius)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        private static int offset(long seed, String key, BlockPosition cell, String phase, int spacing) {
            return (int) Math.floor(sample(seed, key, cell, phase, 0) * spacing);
        }

        private static double square(int value) {
            return (double) value * value;
        }
    }

    private static final class State {
        private final List<StructureTemplate.PaletteEntry> palette;
        private final Set<Integer> tileEntityStates;
        private final Map<BlockPosition, MutableCell> cells = new LinkedHashMap<BlockPosition, MutableCell>();
        private final List<StructureTemplate.Marker> markers = new ArrayList<StructureTemplate.Marker>();
        private final List<StructureLoot> loots = new ArrayList<StructureLoot>();

        private State(List<StructureTemplate.PaletteEntry> palette) {
            this.palette = palette;
            this.tileEntityStates = findTileEntityStates(palette);
        }

        private Snapshot snapshot() {
            Map<BlockPosition, MutableCell> copied = new LinkedHashMap<BlockPosition, MutableCell>();
            for (Map.Entry<BlockPosition, MutableCell> entry : cells.entrySet()) {
                copied.put(entry.getKey(), entry.getValue().copy());
            }
            return new Snapshot(palette, tileEntityStates, copied, markers, loots);
        }

        private static Set<Integer> findTileEntityStates(List<StructureTemplate.PaletteEntry> palette) {
            Set<Integer> result = new LinkedHashSet<Integer>();
            for (int index = 0; index < palette.size(); index++) {
                for (StructureTemplate.State state : palette.get(index).states) {
                    if (!state.tileData.isEmpty()
                            || state.blockId >= 0 && state.blockId < Block.isBlockContainer.length
                            && Block.isBlockContainer[state.blockId]) {
                        result.add(Integer.valueOf(index));
                        break;
                    }
                }
            }
            return Collections.unmodifiableSet(result);
        }

        private Resolved freeze() {
            List<StructureTemplate.TemplateBlock> blocks = new ArrayList<StructureTemplate.TemplateBlock>();
            for (Map.Entry<BlockPosition, MutableCell> entry : cells.entrySet()) {
                MutableCell cell = entry.getValue();
                blocks.add(new StructureTemplate.TemplateBlock(entry.getKey(), cell.state, cell.elementTags,
                        cell.transform));
            }
            Collections.sort(blocks, BLOCK_ORDER);
            List<StructureTemplate.Marker> frozenMarkers = new ArrayList<StructureTemplate.Marker>(markers);
            Collections.sort(frozenMarkers, MARKER_ORDER);
            Set<BlockPosition> tileDataPositions = new LinkedHashSet<BlockPosition>();
            for (StructureTemplate.Marker marker : frozenMarkers) {
                if ((marker.name.equals("tile_data") || marker.name.equals("data"))
                        && !tileDataPositions.add(marker.position)) {
                    throw new IllegalStateException("duplicate tile-data marker at " + coordinate(marker.position));
                }
            }
            List<StructureLoot> frozenLoots = new ArrayList<StructureLoot>(loots);
            Collections.sort(frozenLoots, LOOT_ORDER);
            return new Resolved(blocks, frozenMarkers, frozenLoots);
        }
    }

    private static final class Snapshot {
        private final List<StructureTemplate.PaletteEntry> palette;
        private final Set<Integer> tileEntityStates;
        private final Map<BlockPosition, MutableCell> cells;
        private final List<StructureTemplate.Marker> markers;
        private final Set<BlockPosition> protectedPositions;

        private Snapshot(List<StructureTemplate.PaletteEntry> palette, Set<Integer> tileEntityStates,
                Map<BlockPosition, MutableCell> cells,
                List<StructureTemplate.Marker> markers, List<StructureLoot> loots) {
            this.palette = palette;
            this.tileEntityStates = tileEntityStates;
            this.cells = cells;
            this.markers = immutable(markers);
            this.protectedPositions = findProtectedPositions(this.markers, loots);
        }

        private static Set<BlockPosition> findProtectedPositions(List<StructureTemplate.Marker> markers,
                List<StructureLoot> loots) {
            Set<BlockPosition> result = new LinkedHashSet<BlockPosition>();
            for (StructureTemplate.Marker marker : markers) {
                if (marker.name.equals("connector") || marker.name.equals("terrain_support")
                        || marker.name.equals("tile_data") || marker.name.equals("data")) {
                    result.add(marker.position);
                }
            }
            for (StructureLoot loot : loots) {
                result.add(loot.position);
            }
            return Collections.unmodifiableSet(result);
        }
    }

    private static final class MutableCell {
        private int state;
        private final Set<String> elementTags;
        private final StructureTransform transform;

        private MutableCell(int state, Set<String> elementTags, StructureTransform transform) {
            this.state = state;
            this.elementTags = new LinkedHashSet<String>(elementTags);
            this.transform = transform;
        }

        private MutableCell copy() {
            return new MutableCell(state, elementTags, transform);
        }
    }

    enum Direction {
        DOWN(0, -1, 0),
        UP(0, 1, 0),
        NORTH(0, 0, -1),
        SOUTH(0, 0, 1),
        WEST(-1, 0, 0),
        EAST(1, 0, 0);

        private final int x;
        private final int y;
        private final int z;

        Direction(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private BlockPosition offset(BlockPosition position, int distance) {
            return position.offset(x * distance, y * distance, z * distance);
        }

        private StructureTransform orientation() {
            StructureTransform.Rotation rotation;
            if (this == EAST) {
                rotation = StructureTransform.Rotation.CLOCKWISE_90;
            } else if (this == SOUTH) {
                rotation = StructureTransform.Rotation.CLOCKWISE_180;
            } else if (this == WEST) {
                rotation = StructureTransform.Rotation.COUNTERCLOCKWISE_90;
            } else {
                rotation = StructureTransform.Rotation.NONE;
            }
            return new StructureTransform(rotation, StructureTransform.Mirror.NONE);
        }
    }

    private static final Comparator<StructureTemplate.TemplateBlock> BLOCK_ORDER =
            new Comparator<StructureTemplate.TemplateBlock>() {
                @Override
                public int compare(StructureTemplate.TemplateBlock left, StructureTemplate.TemplateBlock right) {
                    int y = Integer.compare(left.position.y, right.position.y);
                    int z = Integer.compare(left.position.z, right.position.z);
                    return y != 0 ? y : z != 0 ? z : Integer.compare(left.position.x, right.position.x);
                }
            };

    private static final Comparator<StructureTemplate.Marker> MARKER_ORDER =
            new Comparator<StructureTemplate.Marker>() {
                @Override
                public int compare(StructureTemplate.Marker left, StructureTemplate.Marker right) {
                    int y = Integer.compare(left.position.y, right.position.y);
                    int z = Integer.compare(left.position.z, right.position.z);
                    int x = Integer.compare(left.position.x, right.position.x);
                    int name = left.name.compareTo(right.name);
                    return y != 0 ? y : z != 0 ? z : x != 0 ? x : name;
                }
            };

    private static final Comparator<StructureLoot> LOOT_ORDER = new Comparator<StructureLoot>() {
        @Override
        public int compare(StructureLoot left, StructureLoot right) {
            int y = Integer.compare(left.position.y, right.position.y);
            int z = Integer.compare(left.position.z, right.position.z);
            int x = Integer.compare(left.position.x, right.position.x);
            if (y != 0 || z != 0 || x != 0) {
                return y != 0 ? y : z != 0 ? z : x;
            }
            String leftKey = left.key == null ? "" : left.key;
            String rightKey = right.key == null ? "" : right.key;
            return leftKey.compareTo(rightKey);
        }
    };

    private static final Comparator<DepositCandidate> DEPOSIT_ORDER = new Comparator<DepositCandidate>() {
        @Override
        public int compare(DepositCandidate left, DepositCandidate right) {
            int y = Integer.compare(left.source.y, right.source.y);
            int z = Integer.compare(left.source.z, right.source.z);
            int x = Integer.compare(left.source.x, right.source.x);
            return y != 0 ? y : z != 0 ? z : x != 0 ? x
                    : Integer.compare(left.direction.ordinal(), right.direction.ordinal());
        }
    };

    private static final class DepositCandidate {
        private final BlockPosition source;
        private final Direction direction;
        private final int state;
        private final Set<String> tags;
        private final StructureTransform transform;

        private DepositCandidate(BlockPosition source, Direction direction, int state, Set<String> tags,
                StructureTransform transform) {
            this.source = source;
            this.direction = direction;
            this.state = state;
            this.tags = tags;
            this.transform = transform;
        }
    }

    private static double sample(long seed, String key, BlockPosition position, String phase, int discriminator) {
        long value = SeedMixer.derive(seed, SeedMixer.hash(key));
        value = SeedMixer.derive(value, SeedMixer.hash(phase));
        if (position != null) {
            value = SeedMixer.derive(value, coordinateSalt(position));
        }
        value = SeedMixer.derive(value, discriminator);
        return (value >>> 11) * 0x1.0p-53;
    }

    private static long coordinateSalt(BlockPosition position) {
        long value = ((long) position.x & 0x1fffffL) << 42;
        value ^= ((long) position.y & 0x1fffffL) << 21;
        value ^= (long) position.z & 0x1fffffL;
        return value;
    }

    private static String coordinate(BlockPosition position) {
        return "[" + position.x + ", " + position.y + ", " + position.z + "]";
    }
}

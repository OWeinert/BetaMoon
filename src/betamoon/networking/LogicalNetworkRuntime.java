package betamoon.networking;

import betamoon.BetaMoonCommon;
import betamoon.assets.AssetKey;
import betamoon.capability.CapabilityInstance;
import betamoon.capability.CapabilityTopologyEvents;
import betamoon.data.DataField;
import betamoon.luaapi.capability.LuaCapabilityAccess;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.utils.LuaDeclarationValues;
import betamoon.luaapi.world.LuaWorldActionAccess;
import betamoon.luamodloader.LuaScriptErrors;
import betamoon.system.BetaMoonWorldData;
import betamoon.system.WorldServiceRuntime;
import betamoon.tileentity.LuaTileEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.logging.Level;
import net.minecraft.src.NBTBase;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.NBTTagList;
import net.minecraft.src.NBTTagString;
import net.minecraft.src.TileEntity;
import net.minecraft.src.World;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Loaded-chunk indexes, component rebuilding, scheduling, links, state signals, and bounded pulses. */
public final class LogicalNetworkRuntime {
    private static final int MAX_NODES = 4096;
    private static final int MAX_EDGES = 16384;
    private static final int MAX_PULSES = 1024;
    private static final int MAX_PULSE_HOPS = 16;
    private static final Map<World, RuntimeWorld> WORLDS = new WeakHashMap<>();
    private static final int[][] OFFSETS = {
        {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
    };
    private static final String[] FACES = {"down", "up", "north", "south", "west", "east"};
    private static final String[] OPPOSITE = {"up", "down", "south", "north", "east", "west"};

    static {
        CapabilityTopologyEvents.setListener(new CapabilityTopologyEvents.Listener() {
            public void changed(LuaTileEntity tile, AssetKey capability, String field) {
                LogicalNetworkRuntime.dirty(tile, capability, field);
            }
        });
    }

    private LogicalNetworkRuntime() {
    }

    public static synchronized void load(World world) {
        if (world == null || world.multiplayerWorld || WORLDS.containsKey(world)) {
            return;
        }
        BetaMoonWorldData storage = WorldServiceRuntime.storage(world);
        if (WORLDS.containsKey(world)) {
            return;
        }
        RuntimeWorld runtime = new RuntimeWorld(world, storage);
        WORLDS.put(world, runtime);
        for (Object value : world.loadedTileEntityList) {
            if (value instanceof LuaTileEntity && !((LuaTileEntity) value).func_31006_g()) {
                runtime.add((LuaTileEntity) value);
            }
        }
    }

    public static synchronized void tick(World world) {
        if (world == null || world.multiplayerWorld) {
            return;
        }
        load(world);
        RuntimeWorld runtime = WORLDS.get(world);
        if (runtime != null) {
            runtime.tick();
        }
    }

    public static synchronized void unload(World world) {
        RuntimeWorld runtime = WORLDS.remove(world);
        if (runtime != null) {
            runtime.save();
        }
    }

    public static synchronized void added(LuaTileEntity tile) {
        if (tile == null || tile.worldObj == null || tile.worldObj.multiplayerWorld) {
            return;
        }
        load(tile.worldObj);
        RuntimeWorld runtime = WORLDS.get(tile.worldObj);
        if (runtime != null) {
            runtime.add(tile);
        }
    }

    public static synchronized void removed(LuaTileEntity tile) {
        RuntimeWorld runtime = tile == null ? null : WORLDS.get(tile.worldObj);
        if (runtime != null) {
            runtime.remove(tile, true);
        }
    }

    public static synchronized void destroyed(LuaTileEntity tile) {
        RuntimeWorld runtime = tile == null ? null : WORLDS.get(tile.worldObj);
        if (runtime != null) {
            runtime.remove(tile, false);
        }
    }

    public static synchronized void dirty(LuaTileEntity tile) {
        RuntimeWorld runtime = tile == null ? null : WORLDS.get(tile.worldObj);
        if (runtime != null) {
            runtime.dirty(tile);
        }
    }

    public static void dirtyAt(World world, int x, int y, int z) {
        if (world == null || world.multiplayerWorld) {
            return;
        }
        TileEntity tile = world.getBlockTileEntity(x, y, z);
        if (tile instanceof LuaTileEntity) {
            dirty((LuaTileEntity) tile);
        }
    }

    private static synchronized void dirty(LuaTileEntity tile, AssetKey capability, String field) {
        RuntimeWorld runtime = tile == null ? null : WORLDS.get(tile.worldObj);
        if (runtime != null) {
            runtime.dirty(tile, capability, field);
        }
    }

    public static LuaTable local(final LuaCallbackScope scope, final LuaTileEntity tile) {
        final LuaTable registry = new LuaTable();
        registry.set("get", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                return localHandle(scope, tile, arguments.arg(arguments.arg1() == registry ? 2 : 1), false);
            }
        });
        registry.set("getRequired", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                return localHandle(scope, tile, arguments.arg(arguments.arg1() == registry ? 2 : 1), true);
            }
        });
        return registry;
    }

    private static synchronized LuaValue localHandle(LuaCallbackScope scope, LuaTileEntity tile,
            LuaValue definitionValue, boolean required) {
        scope.requireActive();
        LogicalNetworkDefinition definition = betamoon.luaapi.networking.LogicalNetworksApi.definition(
                definitionValue, "networks");
        load(tile.worldObj);
        RuntimeWorld world = WORLDS.get(tile.worldObj);
        NetworkState state = world == null ? null : world.states.get(definition.key.toString());
        Node node = state == null ? null : state.nodes.get(Position.of(tile));
        if (node == null) {
            if (required) {
                throw new LuaError("Tile is not a node in logical network " + definition.key + ".");
            }
            return LuaValue.NIL;
        }
        return nodeHandle(scope, state, node);
    }

    private static LuaTable nodeHandle(final LuaCallbackScope scope, final NetworkState state, final Node node) {
        final LuaTable handle = new LuaTable();
        handle.set("key", state.definition.key.toString());
        handle.set("position", position(node.position));
        handle.set("capability", LuaCapabilityAccess.networkProxy(scope, node.instance));
        handle.set("markDirty", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireMutable();
                requireNode(state, node);
                state.dirty = true;
                return LuaValue.NIL;
            }
        });
        handle.set("publish", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireMutable();
                requireSignal(state);
                if (!("state".equals(state.definition.signalMode)
                        || "both".equals(state.definition.signalMode))) {
                    throw new LuaError("Logical network does not accept persistent state publications.");
                }
                requireNode(state, node);
                LuaValue value = arguments.arg(arguments.arg1() == handle ? 2 : 1);
                state.publish(node, state.definition.signalValue.fromLua(value, "network signal"));
                return LuaValue.NIL;
            }
        });
        handle.set("pulse", new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireMutable();
                requireSignal(state);
                if (!("pulse".equals(state.definition.signalMode)
                        || "both".equals(state.definition.signalMode))) {
                    throw new LuaError("Logical network does not accept pulses.");
                }
                requireNode(state, node);
                LuaValue value = arguments.arg(arguments.arg1() == handle ? 2 : 1);
                state.pulse(node, state.definition.signalValue.fromLua(value, "network pulse"));
                return LuaValue.NIL;
            }
        });
        handle.set("link", linkFunction(handle, scope, state, node, true));
        handle.set("unlink", linkFunction(handle, scope, state, node, false));
        return handle;
    }

    private static VarArgFunction linkFunction(final LuaTable receiver, final LuaCallbackScope scope,
            final NetworkState state, final Node node, final boolean add) {
        return new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                scope.requireMutable();
                requireNode(state, node);
                if (!(state.definition.topology == LogicalNetworkDefinition.Topology.EXPLICIT
                        || state.definition.topology == LogicalNetworkDefinition.Topology.HYBRID)) {
                    throw new LuaError("Links require explicit or hybrid topology.");
                }
                String source = state.endpoint(node);
                if (source == null) {
                    throw new LuaError("This node has no endpoint identifier.");
                }
                String target = arguments.arg(arguments.arg1() == receiver ? 2 : 1).checkjstring();
                if (target.isEmpty() || target.length() > 128 || target.equals(source)) {
                    throw new LuaError("Link target must be another endpoint identifier of at most 128 characters.");
                }
                boolean changed = add ? state.links.add(Link.of(source, target)) : state.links.remove(Link.of(source, target));
                if (changed) {
                    state.dirty = true;
                    state.storage.markDirty();
                }
                return valueOf(changed);
            }
        };
    }

    private static void requireSignal(NetworkState state) {
        if (state.definition.signalValue == null) {
            throw new LuaError("Logical network does not declare signal values.");
        }
    }

    private static void requireNode(NetworkState state, Node node) {
        if (node.tile.func_31006_g() || node.tile.worldObj != state.world
                || state.nodes.get(node.position) != node
                || node.tile.getCapability(state.definition.capability.key) != node.instance) {
            throw new LuaError("Logical-network node handle is stale.");
        }
    }

    private static final class RuntimeWorld {
        private final World world;
        private final BetaMoonWorldData storage;
        private final Map<String, NetworkState> states = new LinkedHashMap<>();

        private RuntimeWorld(World world, BetaMoonWorldData storage) {
            this.world = world;
            this.storage = storage;
            for (LogicalNetworkDefinition definition : LogicalNetworkRegistry.all()) {
                states.put(definition.key.toString(), new NetworkState(world, storage, definition));
            }
        }

        private void add(LuaTileEntity tile) {
            for (NetworkState state : states.values()) {
                CapabilityInstance instance = tile.getCapability(state.definition.capability.key);
                if (instance != null) {
                    state.nodes.put(Position.of(tile), new Node(tile, instance));
                    state.dirty = true;
                }
            }
        }

        private void remove(LuaTileEntity tile, boolean preservePersistentState) {
            Position position = Position.of(tile);
            for (NetworkState state : states.values()) {
                Node removed = state.nodes.remove(position);
                if (removed != null) {
                    String identity = state.identity(removed);
                    boolean changedPersistentState = false;
                    if (!preservePersistentState || !state.definition.retainWithoutTransmitters) {
                        changedPersistentState = state.signals.remove(identity) != null;
                    }
                    if (!preservePersistentState && state.links.removeIf(link -> link.contains(identity))) {
                        changedPersistentState = true;
                    }
                    if (changedPersistentState) {
                        state.saveSignals();
                    }
                    state.dirty = true;
                }
            }
        }

        private void dirty(LuaTileEntity tile) {
            for (NetworkState state : states.values()) {
                if (tile.getCapability(state.definition.capability.key) != null) {
                    state.dirty = true;
                }
            }
        }

        private void dirty(LuaTileEntity tile, AssetKey capability, String field) {
            for (NetworkState state : states.values()) {
                if (state.definition.capability.key.equals(capability)
                        && state.definition.topologyDependsOn(field)
                        && tile.getCapability(capability) != null) {
                    state.dirty = true;
                }
            }
        }

        private void tick() {
            for (NetworkState state : states.values()) {
                state.tick();
            }
        }

        private void save() {
            for (NetworkState state : states.values()) {
                state.save();
            }
        }
    }

    private static final class NetworkState {
        private final World world;
        private final BetaMoonWorldData storage;
        private final LogicalNetworkDefinition definition;
        private final Map<Position, Node> nodes = new LinkedHashMap<>();
        private final Set<Link> links = new LinkedHashSet<>();
        private final Map<String, SignalRecord> signals = new LinkedHashMap<>();
        private final Deque<Pulse> pulses = new ArrayDeque<>();
        private List<Component> components = Collections.emptyList();
        private boolean dirty = true;
        private boolean disabled;
        private int sequence;
        private long signalSequence;
        private int activePulseHop = -1;

        private NetworkState(World world, BetaMoonWorldData storage, LogicalNetworkDefinition definition) {
            this.world = world;
            this.storage = storage;
            this.definition = definition;
            load();
        }

        private void tick() {
            if (disabled) {
                return;
            }
            if (dirty) {
                try {
                    rebuild();
                } catch (Throwable error) {
                    fail("topology rebuild failed", error);
                    return;
                }
            }
            dispatchPulses();
            if (definition.tickInterval > 0 && world.getWorldTime() % definition.tickInterval == 0) {
                for (Component component : components) {
                    invoke(component, definition.onTick, null, null, "onTick");
                }
            }
        }

        private void rebuild() {
            dirty = false;
            if (nodes.size() > MAX_NODES) {
                fail("topology exceeds the node limit of " + MAX_NODES, null);
                return;
            }
            Map<Node, Set<Node>> edges = new LinkedHashMap<>();
            for (Node node : nodes.values()) {
                edges.put(node, new LinkedHashSet<Node>());
            }
            EdgeBudget budget = new EdgeBudget();
            if (definition.topology == LogicalNetworkDefinition.Topology.ADJACENT
                    || definition.topology == LogicalNetworkDefinition.Topology.HYBRID) {
                adjacent(edges, budget);
            }
            if (definition.topology == LogicalNetworkDefinition.Topology.WIRELESS
                    || definition.topology == LogicalNetworkDefinition.Topology.HYBRID) {
                wireless(edges, budget);
            }
            if (definition.topology == LogicalNetworkDefinition.Topology.EXPLICIT
                    || definition.topology == LogicalNetworkDefinition.Topology.HYBRID) {
                explicit(edges, budget);
            }
            components = components(edges);
        }

        private void adjacent(Map<Node, Set<Node>> edges, EdgeBudget budget) {
            for (Node first : nodes.values()) {
                for (int direction = 0; direction < OFFSETS.length; direction++) {
                    Position next = first.position.offset(OFFSETS[direction]);
                    Node second = nodes.get(next);
                    if (second == null || first.position.compareTo(second.position) >= 0) {
                        continue;
                    }
                    Object firstPort = first.instance.attachment.port(FACES[direction], first.tile);
                    Object secondPort = second.instance.attachment.port(OPPOSITE[direction], second.tile);
                    if (!Boolean.FALSE.equals(firstPort) && !Boolean.FALSE.equals(secondPort)
                            && compatible(first, second, firstPort, secondPort)) {
                        connect(edges, first, second, budget);
                    }
                }
            }
        }

        private void wireless(Map<Node, Set<Node>> edges, EdgeBudget budget) {
            Map<Object, List<Node>> channels = new LinkedHashMap<>();
            for (Node node : nodes.values()) {
                if (!enabled(node)) {
                    continue;
                }
                Object channel = field(node, definition.channelField);
                List<Node> entries = channels.get(channel);
                if (entries == null) {
                    entries = new ArrayList<>();
                    channels.put(channel, entries);
                }
                entries.add(node);
            }
            for (List<Node> entries : channels.values()) {
                for (int firstIndex = 0; firstIndex < entries.size(); firstIndex++) {
                    Node first = entries.get(firstIndex);
                    for (int secondIndex = firstIndex + 1; secondIndex < entries.size(); secondIndex++) {
                        Node second = entries.get(secondIndex);
                        if (rolesConnect(first, second) && fieldsMatch(first, second) && inRange(first, second)
                                && compatible(first, second, "wireless", "wireless")) {
                            connect(edges, first, second, budget);
                        }
                    }
                }
            }
        }

        private void explicit(Map<Node, Set<Node>> edges, EdgeBudget budget) {
            Map<String, Node> endpoints = new HashMap<>();
            for (Node node : nodes.values()) {
                String endpoint = endpoint(node);
                if (endpoint != null) {
                    Node previous = endpoints.put(endpoint, node);
                    if (previous != null) {
                        throw new LuaError("Logical-network endpoint ID is used by more than one loaded node: "
                                + endpoint);
                    }
                }
            }
            for (Link link : links) {
                Node first = endpoints.get(link.first);
                Node second = endpoints.get(link.second);
                if (first != null && second != null && compatible(first, second, "explicit", "explicit")) {
                    connect(edges, first, second, budget);
                }
            }
        }

        private boolean compatible(Node first, Node second, Object firstPort, Object secondPort) {
            if (definition.canConnect.isnil()) {
                return true;
            }
            try (LuaCallbackScope scope = new LuaCallbackScope(false)) {
                LuaTable context = new LuaTable();
                context.set("first", connectionView(scope, first, firstPort));
                context.set("second", connectionView(scope, second, secondPort));
                LuaValue result = definition.canConnect.call(context);
                if (!result.isboolean()) {
                    throw new LuaError("canConnect must return a boolean.");
                }
                return result.toboolean();
            } catch (Throwable error) {
                fail("canConnect failed", error);
                return false;
            }
        }

        private LuaTable connectionView(LuaCallbackScope scope, Node node, Object port) {
            LuaTable result = LuaCapabilityAccess.networkProxy(scope, node.instance);
            result.set("port", port instanceof String ? LuaValue.valueOf((String) port) : LuaValue.FALSE);
            return result;
        }

        private List<Component> components(Map<Node, Set<Node>> edges) {
            List<Node> ordered = new ArrayList<>(nodes.values());
            Collections.sort(ordered, Comparator.comparing(node -> node.position));
            Set<Node> visited = new HashSet<>();
            List<Component> result = new ArrayList<>();
            for (Node start : ordered) {
                if (!visited.add(start)) {
                    continue;
                }
                List<Node> members = new ArrayList<>();
                Deque<Node> queue = new ArrayDeque<>();
                queue.add(start);
                while (!queue.isEmpty()) {
                    Node node = queue.removeFirst();
                    members.add(node);
                    List<Node> neighbors = new ArrayList<>(edges.get(node));
                    Collections.sort(neighbors, Comparator.comparing(value -> value.position));
                    for (Node neighbor : neighbors) {
                        if (visited.add(neighbor)) {
                            queue.addLast(neighbor);
                        }
                    }
                }
                Collections.sort(members, Comparator.comparing(node -> node.position));
                result.add(new Component(++sequence, members));
            }
            return Collections.unmodifiableList(result);
        }

        private void publish(Node node, Object value) {
            signals.put(identity(node), SignalRecord.capture(this, node, value, ++signalSequence));
            saveSignals();
        }

        private void pulse(Node node, Object value) {
            if (pulses.size() >= MAX_PULSES) {
                throw new LuaError("Logical-network pulse queue is full.");
            }
            int hop = activePulseHop + 1;
            if (hop > MAX_PULSE_HOPS) {
                throw new LuaError("Logical-network pulse exceeded the forwarding limit of "
                        + MAX_PULSE_HOPS + ".");
            }
            pulses.addLast(new Pulse(node.position, value, world.getWorldTime(), hop));
        }

        private void dispatchPulses() {
            int remaining = pulses.size();
            while (remaining-- > 0) {
                Pulse pulse = pulses.removeFirst();
                if (world.getWorldTime() - pulse.created > 200) {
                    continue;
                }
                Component component = component(pulse.source);
                if (component != null) {
                    int previousHop = activePulseHop;
                    activePulseHop = pulse.hop;
                    try {
                        invoke(component, definition.onPulse, pulse.value, pulse.source, "onPulse");
                    } finally {
                        activePulseHop = previousHop;
                    }
                }
            }
        }

        private Component component(Position position) {
            for (Component component : components) {
                for (Node node : component.nodes) {
                    if (node.position.equals(position)) {
                        return component;
                    }
                }
            }
            return null;
        }

        private void invoke(Component component, LuaValue callback, Object pulse, Position source, String name) {
            if (callback.isnil() || disabled) {
                return;
            }
            try (LuaCallbackScope scope = new LuaCallbackScope(true)) {
                LuaTable context = new LuaTable();
                LuaTable network = new LuaTable();
                network.set("key", definition.key.toString());
                network.set("topology", definition.topology.name().toLowerCase());
                network.set("id", component.id);
                network.set("size", component.nodes.size());
                context.set("network", network);
                context.set("nodes", nodeViews(scope, component.nodes));
                Node origin = component.nodes.get(0);
                context.set("world", LuaWorldActionAccess.create(scope, world,
                        origin.position.x, origin.position.y, origin.position.z));
                Object signal = aggregate(component.nodes);
                if (signal != null && definition.signalValue != null) {
                    context.set("signal", definition.signalValue.toLua(signal));
                }
                if (pulse != null) {
                    context.set("pulse", definition.signalValue.toLua(pulse));
                    context.set("source", position(source));
                }
                callback.call(context);
            } catch (Throwable error) {
                fail(name + " failed", error);
            }
        }

        private LuaTable nodeViews(LuaCallbackScope scope, List<Node> members) {
            final LuaTable result = new LuaTable();
            int index = 0;
            for (Node node : members) {
                result.set(++index, nodeView(scope, node));
            }
            result.set("find", new VarArgFunction() {
                public Varargs invoke(Varargs arguments) {
                    scope.requireActive();
                    LuaValue query = arguments.arg(arguments.arg1() == result ? 2 : 1);
                    if (query.isnil()) {
                        query = new LuaTable();
                    }
                    LuaDeclarationValues.fields(query, "network nodes query", "config", "endpoint");
                    LuaValue config = query.get("config");
                    if (!config.isnil() && !config.istable()) {
                        throw new LuaError("network nodes query.config must be a table.");
                    }
                    String endpoint = query.get("endpoint").isnil()
                            ? null : query.get("endpoint").checkjstring();
                    LuaTable found = new LuaTable();
                    int foundIndex = 0;
                    for (Node node : members) {
                        if ((endpoint == null || endpoint.equals(endpoint(node)))
                                && configMatches(node, config)) {
                            found.set(++foundIndex, nodeView(scope, node));
                        }
                    }
                    return found;
                }
            });
            return result;
        }

        private boolean configMatches(Node node, LuaValue query) {
            if (query.isnil()) {
                return true;
            }
            LuaValue key = LuaValue.NIL;
            for (;;) {
                Varargs next = query.next(key);
                key = next.arg1();
                if (key.isnil()) {
                    return true;
                }
                Object actual = node.instance.attachment.config.get(key.checkjstring());
                DataField schema = node.instance.attachment.capability.config.get(key.checkjstring());
                if (schema == null || !schema.toLua(actual).eq_b(next.arg(2))) {
                    return false;
                }
            }
        }

        private LuaTable nodeView(LuaCallbackScope scope, Node node) {
            LuaTable result = LuaCapabilityAccess.networkProxy(scope, node.instance);
            result.set("network", nodeHandle(scope, this, node));
            String endpoint = endpoint(node);
            if (endpoint != null) {
                result.set("endpoint", endpoint);
            }
            return result;
        }

        private Object aggregate(List<Node> members) {
            if (definition.signalValue == null) {
                return null;
            }
            List<SignalRecord> records = new ArrayList<>();
            Set<String> liveIdentities = new HashSet<>();
            for (Node node : members) {
                String identity = identity(node);
                liveIdentities.add(identity);
                SignalRecord record = signals.get(identity);
                if (record != null && canTransmit(node)) {
                    records.add(record);
                }
            }
            if (definition.retainWithoutTransmitters
                    && (definition.topology == LogicalNetworkDefinition.Topology.WIRELESS
                            || definition.topology == LogicalNetworkDefinition.Topology.HYBRID)) {
                for (Map.Entry<String, SignalRecord> entry : signals.entrySet()) {
                    SignalRecord record = entry.getValue();
                    if (!liveIdentities.contains(entry.getKey()) && record.transmitter
                            && retainedMatches(record, members)) {
                        records.add(record);
                    }
                }
            }
            if (records.isEmpty()) {
                return null;
            }
            Collections.sort(records, Comparator.comparingLong(record -> record.sequence));
            if ("latest".equals(definition.aggregate)) {
                return records.get(records.size() - 1).value;
            }
            if ("any".equals(definition.aggregate) || "all".equals(definition.aggregate)) {
                boolean result = "all".equals(definition.aggregate);
                for (SignalRecord record : records) {
                    Object value = record.value;
                    result = "any".equals(definition.aggregate)
                            ? result || ((Boolean) value).booleanValue()
                            : result && ((Boolean) value).booleanValue();
                }
                return Boolean.valueOf(result);
            }
            double result = "minimum".equals(definition.aggregate)
                    ? Double.POSITIVE_INFINITY : "maximum".equals(definition.aggregate)
                            ? Double.NEGATIVE_INFINITY : 0.0D;
            for (SignalRecord record : records) {
                Object value = record.value;
                double number = ((Number) value).doubleValue();
                result = "minimum".equals(definition.aggregate) ? Math.min(result, number)
                        : "maximum".equals(definition.aggregate) ? Math.max(result, number) : result + number;
            }
            if (definition.signalValue.type == DataField.Type.INTEGER) {
                return Integer.valueOf((int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, result)));
            }
            return Double.valueOf(result);
        }

        private boolean retainedMatches(SignalRecord record, List<Node> members) {
            for (Node node : members) {
                if (canReceive(node) && record.matches(this, node)) {
                    return true;
                }
            }
            return false;
        }

        private boolean enabled(Node node) {
            Object enabled = field(node, definition.enabledField);
            return !(enabled instanceof Boolean) || ((Boolean) enabled).booleanValue();
        }

        private boolean fieldsMatch(Node first, Node second) {
            for (String field : definition.compatibilityFields) {
                if (!Objects.equals(field(first, field), field(second, field))) {
                    return false;
                }
            }
            return true;
        }

        private boolean rolesConnect(Node first, Node second) {
            return canTransmit(first) && canReceive(second) || canTransmit(second) && canReceive(first);
        }

        private boolean canTransmit(Node node) {
            Object role = field(node, definition.roleField);
            return !(role instanceof String) || "transmitter".equals(role) || "transceiver".equals(role);
        }

        private boolean canReceive(Node node) {
            Object role = field(node, definition.roleField);
            return !(role instanceof String) || "receiver".equals(role) || "transceiver".equals(role);
        }

        private boolean inRange(Node first, Node second) {
            double distanceSquared = first.position.distanceSquared(second.position);
            return transmitterReaches(first, second, distanceSquared)
                    || transmitterReaches(second, first, distanceSquared);
        }

        private boolean transmitterReaches(Node transmitter, Node receiver, double distanceSquared) {
            if (!canTransmit(transmitter) || !canReceive(receiver)) {
                return false;
            }
            Object configured = field(transmitter, definition.rangeField);
            double range = configured instanceof Number ? ((Number) configured).doubleValue() : definition.range;
            return range < 0.0D || distanceSquared <= range * range;
        }

        private Object field(Node node, String name) {
            if (name == null) {
                return null;
            }
            if (node.instance.attachment.config.containsKey(name)) {
                return node.instance.attachment.config.get(name);
            }
            if (node.instance.attachment.capability.state.get(name) != null) {
                return node.instance.data.getValue(name);
            }
            return null;
        }

        private String endpoint(Node node) {
            Object value = field(node, definition.endpointField);
            if (!(value instanceof String) || ((String) value).isEmpty()) {
                return null;
            }
            String endpoint = (String) value;
            if (endpoint.length() > 128 || endpoint.indexOf('\n') >= 0 || endpoint.indexOf('\r') >= 0) {
                throw new LuaError("Logical-network endpoint IDs must contain 1 to 128 characters without newlines.");
            }
            return endpoint;
        }

        private String identity(Node node) {
            String endpoint = endpoint(node);
            return endpoint == null ? node.position.token() : endpoint;
        }

        private void connect(Map<Node, Set<Node>> edges, Node first, Node second, EdgeBudget budget) {
            if (edges.get(first).add(second)) {
                if (++budget.edges > MAX_EDGES) {
                    throw new LuaError("Logical network exceeds the edge limit of " + MAX_EDGES + ".");
                }
                edges.get(second).add(first);
            }
        }

        private void fail(String message, Throwable error) {
            disabled = true;
            String detail = error == null ? message
                    : message + ": " + (error.getMessage() == null ? error.toString() : error.getMessage());
            String report = "logical network " + definition.key + " disabled: " + detail;
            LuaScriptErrors.add(definition.owner, report);
            BetaMoonCommon.LOGGER.log(Level.WARNING, definition.owner + ": " + report, error);
        }

        private void load() {
            if (storage == null || !storage.networks().hasKey(definition.key.toString())) {
                return;
            }
            NBTTagCompound root = storage.networks().getCompoundTag(definition.key.toString());
            NBTTagList savedLinks = root.getTagList("Links");
            for (int index = 0; index < savedLinks.tagCount(); index++) {
                NBTBase tag = savedLinks.tagAt(index);
                if (tag instanceof NBTTagString) {
                    Link link = Link.parse(((NBTTagString) tag).stringValue);
                    if (link != null) {
                        links.add(link);
                    }
                }
            }
            if (definition.signalValue != null && root.hasKey("Signals")) {
                NBTTagCompound savedSignals = root.getCompoundTag("Signals");
                for (Object value : savedSignals.func_28110_c()) {
                    NBTBase tag = (NBTBase) value;
                    try {
                        SignalRecord record = SignalRecord.load(definition, tag);
                        if (record != null) {
                            signals.put(tag.getKey(), record);
                            signalSequence = Math.max(signalSequence, record.sequence);
                        }
                    } catch (IllegalArgumentException ignored) {
                        // Preserve the raw world payload; one invalid retained value is skipped at runtime.
                    }
                }
            }
        }

        private void saveSignals() {
            save();
            if (storage != null) {
                storage.markDirty();
            }
        }

        private void save() {
            if (storage == null) {
                return;
            }
            NBTTagCompound root = new NBTTagCompound();
            NBTTagList savedLinks = new NBTTagList();
            for (Link link : links) {
                savedLinks.setTag(new NBTTagString(link.token()));
            }
            root.setTag("Links", savedLinks);
            if (definition.signalValue != null) {
                NBTTagCompound savedSignals = new NBTTagCompound();
                for (Map.Entry<String, SignalRecord> entry : signals.entrySet()) {
                    savedSignals.setCompoundTag(entry.getKey(), entry.getValue().save(definition));
                }
                root.setCompoundTag("Signals", savedSignals);
            }
            storage.networks().setCompoundTag(definition.key.toString(), root);
            storage.markDirty();
        }
    }

    private static LuaTable position(Position position) {
        LuaTable result = new LuaTable();
        result.set("x", position.x);
        result.set("y", position.y);
        result.set("z", position.z);
        return result;
    }

    private static final class Node {
        private final LuaTileEntity tile;
        private final CapabilityInstance instance;
        private final Position position;
        private Node(LuaTileEntity tile, CapabilityInstance instance) {
            this.tile = tile;
            this.instance = instance;
            this.position = Position.of(tile);
        }
    }

    private static final class Position implements Comparable<Position> {
        private final int x;
        private final int y;
        private final int z;
        private Position(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
        private static Position of(LuaTileEntity tile) {
            return new Position(tile.xCoord, tile.yCoord, tile.zCoord);
        }
        private Position offset(int[] offset) {
            return new Position(x + offset[0], y + offset[1], z + offset[2]);
        }
        private double distanceSquared(Position other) {
            double dx = x - other.x;
            double dy = y - other.y;
            double dz = z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }
        private String token() {
            return x + "," + y + "," + z;
        }
        public int compareTo(Position other) {
            int xResult = Integer.compare(x, other.x);
            if (xResult != 0) {
                return xResult;
            }
            int yResult = Integer.compare(y, other.y);
            return yResult != 0 ? yResult : Integer.compare(z, other.z);
        }
        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Position)) {
                return false;
            }
            Position position = (Position) other;
            return x == position.x && y == position.y && z == position.z;
        }
        @Override
        public int hashCode() {
            return 31 * (31 * x + y) + z;
        }
    }

    private static final class Link {
        private final String first;
        private final String second;
        private Link(String first, String second) {
            this.first = first;
            this.second = second;
        }
        private static Link of(String first, String second) {
            return first.compareTo(second) <= 0 ? new Link(first, second) : new Link(second, first);
        }
        private static Link parse(String token) {
            int separator = token.indexOf('\n');
            return separator <= 0 || separator == token.length() - 1 ? null
                    : of(token.substring(0, separator), token.substring(separator + 1));
        }
        private String token() {
            return first + "\n" + second;
        }
        private boolean contains(String endpoint) {
            return first.equals(endpoint) || second.equals(endpoint);
        }
        @Override
        public boolean equals(Object other) {
            return other instanceof Link && first.equals(((Link) other).first) && second.equals(((Link) other).second);
        }
        @Override
        public int hashCode() {
            return 31 * first.hashCode() + second.hashCode();
        }
    }

    private static final class Component {
        private final int id;
        private final List<Node> nodes;
        private Component(int id, List<Node> nodes) {
            this.id = id;
            this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
        }
    }

    /** Persistent publication plus the wireless membership facts captured when it was sent. */
    private static final class SignalRecord {
        private final Object value;
        private final long sequence;
        private final Position position;
        private final String channel;
        private final List<String> compatibility;
        private final double range;
        private final boolean transmitter;

        private SignalRecord(Object value, long sequence, Position position, String channel,
                List<String> compatibility, double range, boolean transmitter) {
            this.value = value;
            this.sequence = sequence;
            this.position = position;
            this.channel = channel;
            this.compatibility = Collections.unmodifiableList(new ArrayList<>(compatibility));
            this.range = range;
            this.transmitter = transmitter;
        }

        private static SignalRecord capture(NetworkState state, Node node, Object value, long sequence) {
            List<String> compatibility = new ArrayList<>();
            for (String field : state.definition.compatibilityFields) {
                compatibility.add(token(state.field(node, field)));
            }
            Object configuredRange = state.field(node, state.definition.rangeField);
            double range = configuredRange instanceof Number
                    ? ((Number) configuredRange).doubleValue() : state.definition.range;
            return new SignalRecord(value, sequence, node.position,
                    token(state.field(node, state.definition.channelField)), compatibility, range,
                    state.canTransmit(node));
        }

        private boolean matches(NetworkState state, Node receiver) {
            if (!state.enabled(receiver) || !channel.equals(token(state.field(receiver,
                    state.definition.channelField)))) {
                return false;
            }
            for (int index = 0; index < state.definition.compatibilityFields.size(); index++) {
                if (!compatibility.get(index).equals(token(state.field(receiver,
                        state.definition.compatibilityFields.get(index))))) {
                    return false;
                }
            }
            return range < 0.0D || position.distanceSquared(receiver.position) <= range * range;
        }

        private NBTTagCompound save(LogicalNetworkDefinition definition) {
            NBTTagCompound result = new NBTTagCompound();
            definition.signalValue.write(result, "Value", value);
            result.setLong("Sequence", sequence);
            result.setInteger("X", position.x);
            result.setInteger("Y", position.y);
            result.setInteger("Z", position.z);
            result.setString("Channel", channel);
            result.setDouble("Range", range);
            result.setBoolean("Transmitter", transmitter);
            NBTTagList fields = new NBTTagList();
            for (String entry : compatibility) {
                fields.setTag(new NBTTagString(entry));
            }
            result.setTag("Compatibility", fields);
            return result;
        }

        private static SignalRecord load(LogicalNetworkDefinition definition, NBTBase saved) {
            if (!(saved instanceof NBTTagCompound)) {
                return null;
            }
            NBTTagCompound value = (NBTTagCompound) saved;
            Object signal = definition.signalValue.read(value, "Value", "saved network signal");
            NBTTagList savedCompatibility = value.getTagList("Compatibility");
            if (savedCompatibility.tagCount() != definition.compatibilityFields.size()) {
                return null;
            }
            List<String> compatibility = new ArrayList<>();
            for (int index = 0; index < savedCompatibility.tagCount(); index++) {
                NBTBase entry = savedCompatibility.tagAt(index);
                if (!(entry instanceof NBTTagString)) {
                    return null;
                }
                compatibility.add(((NBTTagString) entry).stringValue);
            }
            double range = value.getDouble("Range");
            if (!Double.isFinite(range) || range == 0.0D || range < -1.0D || range > 1024.0D) {
                return null;
            }
            return new SignalRecord(signal, value.getLong("Sequence"),
                    new Position(value.getInteger("X"), value.getInteger("Y"), value.getInteger("Z")),
                    value.getString("Channel"), compatibility, range, value.getBoolean("Transmitter"));
        }

        private static String token(Object value) {
            if (value == null) {
                return "null:";
            }
            if (value instanceof Boolean) {
                return "boolean:" + value;
            }
            if (value instanceof Number) {
                return "number:" + value;
            }
            return "string:" + value;
        }
    }

    private static final class Pulse {
        private final Position source;
        private final Object value;
        private final long created;
        private final int hop;
        private Pulse(Position source, Object value, long created, int hop) {
            this.source = source;
            this.value = value;
            this.created = created;
            this.hop = hop;
        }
    }

    private static final class EdgeBudget {
        private int edges;
    }
}

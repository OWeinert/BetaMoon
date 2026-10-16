package betamoon.luaapi.multiblock;

import betamoon.content.ContentKey;
import betamoon.luaapi.block.BlockCallbackRegistry;
import betamoon.luaapi.block.BlockDefinition;
import betamoon.content.ContentType;
import betamoon.instrumentation.agent.AgentRuntime;
import betamoon.instrumentation.diagnostics.HookDiagnostic;
import betamoon.instrumentation.diagnostics.HookStatus;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.multiblock.MultiblockDefinition;
import betamoon.multiblock.MultiblockMatchJob;
import betamoon.multiblock.MultiblockOrientation;
import betamoon.multiblock.MultiblockPosition;
import betamoon.multiblock.MultiblockRegistry;
import betamoon.multiblock.MultiblockReport;
import betamoon.multiblock.MultiblockRuntime;
import betamoon.multiblock.ControllerlessMultiblockRuntime;
import betamoon.multiblock.MultiblockInstance;
import betamoon.runtime.RuntimeEnvironment;
import net.minecraft.src.World;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import static betamoon.luaapi.utils.LuaDeclarationValues.bool;
import static betamoon.luaapi.utils.LuaDeclarationValues.fields;
import static betamoon.luaapi.utils.LuaDeclarationValues.integer;
import static betamoon.luaapi.utils.LuaDeclarationValues.string;

/** Public declarations, pure queries and callback-scoped controller access. */
public final class MultiblocksApi {
    private MultiblocksApi() {
    }

    private interface Call {
        Varargs invoke(Varargs arguments);
    }

    private static void method(LuaTable table, String name, Call call) {
        table.set(name, new VarArgFunction() {
            public Varargs invoke(Varargs arguments) {
                try {
                    return call.invoke(arguments.arg1() == table ? arguments.subargs(2) : arguments);
                } catch (IllegalArgumentException | IllegalStateException invalid) {
                    throw new LuaError(invalid.getMessage());
                }
            }
        });
    }

    public static void attach(LuaTable module) {
        LuaTable registry = new LuaTable();
        method(registry, "add", args -> {
            MultiblockDefinition definition = MultiblockDeclarations.parse(args.arg1());
            if (definition.controller >= 0 || definition.management != null) {
                requireHooks();
            }
            MultiblockRegistry.stage(definition);
            return new Reference(definition);
        });
        for (String name : new String[]{"get", "getRequired"}) {
            method(registry, name, args -> {
                ContentKey key = ContentKey.parseForType(string(args.arg1(), "key"), ContentType.of("multiblock"));
                MultiblockDefinition definition = MultiblockRegistry.find(key);
                if (definition == null && name.equals("getRequired")) {
                    throw new LuaError("Multiblock is not registered: " + key);
                }
                return definition == null ? LuaValue.NIL : new Reference(definition);
            });
        }
        module.set("multiblocks", registry);
    }

    public static void attachWorld(LuaTable api, LuaCallbackScope scope, World world) {
        MultiblockQueryApi.attach(api, scope, world);
        method(api, "checkMultiblock", args -> {
            scope.requireActive();
            MultiblockDefinition definition = definition(args.arg1());
            int x = coordinate(args.arg(2), "x");
            int y = coordinate(args.arg(3), "y");
            int z = coordinate(args.arg(4), "z");
            LuaValue options = args.arg(5);
            if (!options.isnil()) {
                fields(options, "checkMultiblock.options", "facing", "mirror", "detailed", "repeats");
            }
            LuaValue selectedFacing = field(options, "facing");
            MultiblockMatchJob.Reader reader = MultiblockRuntime.reader(world);
            int facing;
            if (!selectedFacing.isnil()) {
                facing = MultiblockDeclarations.direction(selectedFacing, "options.facing");
            } else if (definition.fixedFacing != null) {
                facing = definition.fixedFacing;
            } else if (definition.controller >= 0 && !MultiblockMatchJob.inBounds(x, y, z)) {
                facing = 0; // An invalid anchor is a mismatch without reading its orientation.
            } else if (definition.controller >= 0 && MultiblockMatchJob.inBounds(x, y, z) && reader.loaded(x, z)) {
                facing = MultiblockRuntime.facing(definition, reader, x, y, z);
            } else if (definition.controller < 0) {
                throw new LuaError("Standalone checks require options.facing or a fixed definition facing");
            } else {
                MultiblockReport unavailable = new MultiblockReport();
                unavailable.reason = "chunk_unavailable";
                return report(definition, new MultiblockPosition(x, y, z), 0, false, unavailable);
            }
            boolean mirror = bool(field(options, "mirror"), "options.mirror", false);
            boolean detailed = bool(field(options, "detailed"), "options.detailed", true);
            Integer repeats = field(options, "repeats").isnil()
                    ? null
                    : integer(field(options, "repeats"), "options.repeats", 0, 32);
            reader = ControllerlessMultiblockRuntime.checkReader(world, definition, x, y, z, facing, mirror, repeats);
            MultiblockMatchJob job = new MultiblockMatchJob(definition, reader, x, y, z, facing, mirror, detailed,
                    repeats);
            scope.useMultiblockWork(job.advance(scope.multiblockWorkRemaining()));
            if (!job.done()) {
                job.report().reason = "diagnostic_budget";
                job.report().status = "unavailable";
                job.report().truncated = true;
            }
            return report(definition, new MultiblockPosition(x, y, z), facing, mirror, job.report());
        });
        method(api, "getMultiblock", args -> {
            scope.requireActive();
            MultiblockRuntime.Instance found = MultiblockRuntime.get(world, coordinate(args.arg1(), "x"),
                    coordinate(args.arg(2), "y"), coordinate(args.arg(3), "z"));
            return found == null ? LuaValue.NIL : instance(scope, found, false);
        });
    }

    public static LuaValue local(LuaCallbackScope scope, World world, int x, int y, int z) {
        MultiblockRuntime.Instance found = MultiblockRuntime.get(world, x, y, z);
        return found == null ? LuaValue.NIL : instance(scope, found, false);
    }

    public static LuaTable instance(LuaCallbackScope scope, MultiblockInstance instance, boolean removed) {
        LuaTable access = new LuaTable();
        method(access, "isActive", args -> {
            current(scope, instance, removed);
            return LuaValue.valueOf(instance.isActive());
        });
        for (String operation : new String[]{"activate", "deactivate"}) {
            method(access, operation, args -> {
                scope.requireMutable();
                current(scope, instance, removed);
                return MultiblockQueryApi.result(scope,
                        operation.equals("activate") ? instance.activate() : instance.deactivate());
            });
        }
        method(access, "isFormed", args -> {
            current(scope, instance, removed);
            return LuaValue.valueOf(instance.isFormed());
        });
        method(access, "getStatus", args -> {
            current(scope, instance, removed);
            LuaTable result = report(instance.definition, instance.position, instance.facing, instance.mirror,
                    instance.report);
            result.set("checkStatus", result.get("status"));
            result.set("status", instance.expired ? "removed" : instance.dirty ? "checking" : instance.status);
            result.set("isFormed", LuaValue.valueOf(instance.isFormed()));
            result.set("isActive", LuaValue.valueOf(instance.isActive()));
            result.set("activationSaved", LuaValue.valueOf(instance.activationSaved()));
            result.set("manuallyInhibited", LuaValue.valueOf(instance.manuallyInhibited()));
            result.set("identity", instance.identity);
            if (instance.data.error() != null) {
                result.set("dataError", instance.data.error());
            }
            if (instance.callbackError != null) {
                result.set("callbackError", instance.callbackError);
            }
            if (instance.conflict != null) {
                result.set("conflictingController",
                        position(instance.conflict.x, instance.conflict.y, instance.conflict.z));
            }
            return result;
        });
        method(access, "getMatches", args -> {
            current(scope, instance, removed);
            String symbol = string(args.arg1(), "getMatches.symbol");
            String selected = instance.definition.captures.get(symbol);
            if (selected == null) {
                if (!instance.definition.symbols.contains(symbol) || symbol.equals("?")) {
                    throw new LuaError("Unknown match symbol or capture: " + symbol);
                }
                selected = symbol;
            }
            if (!instance.isFormed()) {
                return LuaValue.varargsOf(LuaValue.NIL,
                        LuaValue.valueOf(instance.dirty ? "checking" : instance.status));
            }
            LuaTable result = new LuaTable();
            int index = 0;
            for (MultiblockReport.Match match : instance.report.matches) {
                if (selected.contains(match.symbol)) {
                    result.set(++index, position(match.x, match.y, match.z));
                }
            }
            return result;
        });
        method(access, "requestCheck", args -> {
            scope.requireMutable();
            current(scope, instance, removed);
            instance.requestCheck();
            return LuaValue.NONE;
        });
        access.set("data", data(scope, instance, removed));
        return access;
    }

    public static LuaTable data(LuaCallbackScope scope, MultiblockInstance instance, boolean removed) {
        LuaTable api = new LuaTable();
        method(api, "get", args -> {
            current(scope, instance, removed);
            return instance.data.get(string(args.arg1(), "data field"));
        });
        method(api, "set", args -> {
            scope.requireMutable();
            current(scope, instance, false);
            instance.data.set(string(args.arg1(), "data field"), args.arg(2), true);
            return LuaValue.NONE;
        });
        method(api, "snapshot", args -> {
            current(scope, instance, removed);
            return instance.data.snapshot();
        });
        return api;
    }

    private static void current(LuaCallbackScope scope, MultiblockInstance instance, boolean removed) {
        scope.requireActive();
        if (!removed) {
            instance.requireCurrent();
        }
    }

    public static LuaTable report(MultiblockDefinition definition, MultiblockPosition anchor, int facing,
            boolean mirror, MultiblockReport report) {
        LuaTable result = new LuaTable();
        result.set("key", definition.key.toString());
        result.set("anchor", position(anchor.x, anchor.y, anchor.z));
        result.set("facing", MultiblockOrientation.name(facing));
        result.set("mirror", LuaValue.valueOf(mirror));
        result.set("status", report.status);
        result.set("reason", report.reason);
        result.set("checked", report.checked);
        result.set("required", report.required);
        result.set("repeats", report.repeats);
        result.set("width", report.width);
        result.set("height", report.height);
        result.set("depth", report.depth);
        result.set("truncated", LuaValue.valueOf(report.truncated));
        LuaTable issues = new LuaTable();
        int index = 0;
        for (MultiblockReport.Issue issue : report.issues) {
            LuaTable entry = position(issue.x, issue.y, issue.z);
            entry.set("layer", issue.cell.layer);
            entry.set("row", issue.cell.row);
            entry.set("column", issue.cell.column);
            entry.set("symbol", issue.cell.symbol);
            entry.set("expected", issue.cell.rule.describe());
            entry.set("reason", issue.reason);
            if (issue.blockId >= 0) {
                entry.set("blockId", issue.blockId);
                entry.set("metadata", issue.metadata);
                BlockDefinition block = BlockCallbackRegistry.get(issue.blockId);
                if (block != null) {
                    entry.set("state", block.state.snapshot(issue.metadata));
                }
            }
            issues.set(++index, entry);
        }
        result.set("issues", issues);
        LuaTable constraints = new LuaTable();
        index = 0;
        for (MultiblockReport.Count count : report.constraints) {
            LuaTable entry = new LuaTable();
            entry.set("label", count.constraint.label);
            entry.set("found", count.found);
            entry.set("min", count.constraint.min);
            entry.set("max", count.constraint.max);
            entry.set("passed", LuaValue.valueOf(count.passed));
            constraints.set(++index, entry);
        }
        result.set("constraints", constraints);
        return result;
    }

    private static LuaTable position(int x, int y, int z) {
        LuaTable value = new LuaTable();
        value.set("x", x);
        value.set("y", y);
        value.set("z", z);
        return value;
    }

    private static int coordinate(LuaValue value, String path) {
        return integer(value, path, -32000032, 32000032);
    }

    private static LuaValue field(LuaValue table, String name) {
        return table.isnil() ? LuaValue.NIL : table.get(name);
    }

    static MultiblockDefinition definition(LuaValue value) {
        if (!(value instanceof Reference)) {
            throw new LuaError("Expected a multiblock definition reference");
        }
        MultiblockDefinition definition = ((Reference) value).definition;
        if (MultiblockRegistry.find(definition.key) != definition) {
            throw new LuaError("Multiblock definition reference expired after reload or owner removal");
        }
        return definition;
    }

    private static void requireHooks() {
        if (!RuntimeEnvironment.isClient()) {
            return;
        }
        if (!AgentRuntime.isRegistered()) {
            throw new LuaError("Managed multiblocks require the BetaMoon Java agent");
        }
        for (String operation : new String[]{"id", "state", "metadata", "loaded", "unloaded"}) {
            boolean available = false;
            for (HookDiagnostic diagnostic : AgentRuntime.getReport().snapshot()) {
                if (("betamoon:multiblock:" + operation).equals(diagnostic.getHookId())) {
                    HookStatus status = diagnostic.getStatus();
                    available = status == HookStatus.APPLIED || status == HookStatus.ALREADY_APPLIED
                            || status == HookStatus.WAITING_FOR_TARGET;
                }
            }
            if (!available) {
                throw new LuaError("Managed multiblocks require unavailable hook betamoon:multiblock:" + operation);
            }
        }
    }

    private static final class Reference extends LuaTable {
        private final MultiblockDefinition definition;

        private Reference(MultiblockDefinition definition) {
            this.definition = definition;
            MultiblocksApi.method(this, "getKey", args -> {
                definition(this);
                return LuaValue.valueOf(definition.key.toString());
            });
        }
    }
}

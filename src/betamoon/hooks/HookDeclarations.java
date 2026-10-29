package betamoon.hooks;

import betamoon.interop.catalog.CatalogFingerprint;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.LuaClosure;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaString;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.BaseLib;
import org.luaj.vm2.lib.DebugLib;
import org.luaj.vm2.lib.MathLib;
import org.luaj.vm2.lib.PackageLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.VarArgFunction;

/**
 * Interpreted, bounded declaration evaluation; neither a normal mod VM nor a
 * hostile-code sandbox.
 */
public final class HookDeclarations {
    private HookDeclarations() {
    }

    public static final class Budget {
        private int remaining = 500000;

        void consume() {
            if (--remaining < 0) {
                throw new BudgetExceeded("package instruction budget exhausted");
            }
        }
    }

    public static List<HookFileCollectionContext.Registration> evaluate(String owner, String file, String text,
            long attempt, Globals runtime, Budget budget) {
        String hash = CatalogFingerprint.sha256(text.getBytes(StandardCharsets.UTF_8));
        try (HookFileCollectionContext context = new HookFileCollectionContext(owner, file, attempt, runtime != null,
                hash)) {
            Globals globals = pureGlobals();
            HookSymbolicReferences references = new HookSymbolicReferences();
            DeclarationRoot root = new DeclarationRoot();
            root.properties.put("hooks", HookLuaValue.facade(context));
            root.properties.put("native", references);
            globals.set("betamoon", root);
            BudgetDebug debug = new BudgetDebug(budget);
            LuaTable internalPackage = new LuaTable();
            internalPackage.set("loaded", new LuaTable());
            globals.set("package", internalPackage);
            globals.load(debug);
            globals.set("package", LuaValue.NIL);
            globals.set("debug", LuaValue.NIL);
            try {
                globals.load(text, "@" + file).call();
                List<HookFileCollectionContext.Registration> registrations = context.seal();
                if (runtime != null) {
                    // Closures keep this Globals identity; they see the owner's normal module cache
                    // only after sealing.
                    for (LuaValue key : globals.keys()) {
                        globals.rawset(key, LuaValue.NIL);
                    }
                    LuaTable ownerEnvironment = new LuaTable();
                    ownerEnvironment.set("__index", runtime);
                    ownerEnvironment.set("__newindex", runtime);
                    globals.setmetatable(ownerEnvironment);
                    globals.debuglib = null;
                    globals.baselib = runtime.baselib;
                    globals.package_ = runtime.package_;
                    globals.finder = runtime.finder;
                    references.activate(runtime.get("betamoon").get("native"));
                    root.runtime = runtime.get("betamoon");
                }
                return registrations;
            } catch (BudgetExceeded error) {
                throw new HookFailure("preparation_budget_exceeded", file + ": " + error.getMessage(), error);
            } catch (StackOverflowError error) {
                throw new HookFailure("preparation_budget_exceeded", file + ": declaration/compiler recursion", error);
            } catch (RuntimeException error) {
                String reason = "declaration_failed";
                Throwable cause = error;
                for (int depth = 0; cause != null && depth < 16; depth++, cause = cause.getCause()) {
                    if (cause instanceof HookFailure) {
                        reason = ((HookFailure) cause).reason;
                        break;
                    }
                }
                throw new HookFailure(reason, file + " [" + (runtime == null ? "preparation" : "runtime") + ", attempt "
                        + attempt + "]: " + error.getMessage(), error);
            }
        }
    }

    private static Globals pureGlobals() {
        Globals globals = new Globals();
        globals.load(new BaseLib());
        globals.load(new PackageLib());
        globals.load(new MathLib());
        LuaValue previousStringMetatable = LuaString.s_metatable;
        try {
            globals.load(new StringLib());
        } finally {
            LuaString.s_metatable = previousStringMetatable;
        }
        globals.load(new TableLib());
        List<String> allowed = Arrays.asList("assert", "error", "ipairs", "next", "pairs", "pcall", "select",
                "tonumber", "tostring", "type", "xpcall", "math", "string", "table", "_VERSION");
        for (LuaValue key : globals.keys()) {
            if (!allowed.contains(key.tojstring())) {
                globals.rawset(key, LuaValue.NIL);
            }
        }
        LuaTable strings = globals.get("string").checktable();
        for (LuaValue key : strings.keys()) {
            if (!Arrays.asList("byte", "char", "len", "lower", "upper", "reverse", "sub").contains(key.tojstring())) {
                strings.rawset(key, LuaValue.NIL);
            }
        }
        // Java helpers run between VM instructions, so bound their loops before
        // entering LuaJ.
        LuaTable tables = globals.get("table").checktable();
        tables.set("sort", LuaValue.NIL);
        tables.set("unpack", LuaValue.NIL);
        LuaValue insert = tables.get("insert");
        tables.set("insert", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaTable values = boundedTable(args);
                if (args.narg() < 2 || args.narg() > 3 || values.rawlen() >= 4096) {
                    throw new BudgetExceeded("insert helper bound exceeded");
                }
                if (args.narg() == 3) {
                    boundedPosition(args.arg(2), 1, values.rawlen() + 1);
                }
                return insert.invoke(args);
            }
        });
        LuaValue remove = tables.get("remove");
        tables.set("remove", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaTable values = boundedTable(args);
                if (args.narg() > 2) {
                    throw new BudgetExceeded("remove helper bound exceeded");
                }
                if (!args.arg(2).isnil()) {
                    boundedPosition(args.arg(2), values.rawlen() == 0 ? 0 : 1, values.rawlen() + 1);
                }
                return remove.invoke(args);
            }
        });
        LuaValue pack = tables.get("pack");
        tables.set("pack", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                if (args.narg() > 4096) {
                    throw new BudgetExceeded("pack helper bound exceeded");
                }
                return pack.invoke(args);
            }
        });
        LuaValue concat = tables.get("concat");
        tables.set("concat", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaTable values = boundedTable(args);
                long bytes = 0;
                if (args.narg() > 2) {
                    throw new BudgetExceeded("declaration concat accepts table and optional separator only");
                }
                for (int index = 1; index <= values.rawlen(); index++) {
                    bytes += values.rawget(index).checkstring().rawlen();
                }
                bytes += (long) (args.arg(2).isnil() ? 0 : args.checkstring(2).rawlen()) * values.rawlen();
                if (bytes > 262144) {
                    throw new BudgetExceeded("concat helper bound exceeded");
                }
                return concat.invoke(args);
            }
        });
        globals.get("math").set("random", LuaValue.NIL);
        globals.get("math").set("randomseed", LuaValue.NIL);
        LoadState.install(globals);
        LuaC.install(globals);
        return globals;
    }

    private static LuaTable boundedTable(Varargs args) {
        LuaTable values = args.checktable(1);
        if (values.rawlen() > 4096 || values.keyCount() > 4096) {
            throw new BudgetExceeded("table helper bound exceeded");
        }
        return values;
    }

    private static void boundedPosition(LuaValue position, int minimum, int maximum) {
        double value = position.checkdouble();
        if (position.type() != LuaValue.TNUMBER || value != Math.rint(value) || value < minimum || value > maximum) {
            throw new BudgetExceeded("table helper position is outside the bounded sequence");
        }
    }

    private static final class DeclarationRoot extends HookLuaValue {
        private LuaValue runtime;

        @Override
        public LuaValue get(LuaValue key) {
            return runtime == null ? super.get(key) : runtime.get(key);
        }
    }

    private static final class BudgetDebug extends DebugLib {
        private final Budget aggregate;
        private final List<LuaValue[]> frames = new ArrayList<>();
        private final Deque<Boolean> calls = new ArrayDeque<>();
        private final long deadline = System.nanoTime() + 2000000000L;
        private int remaining = 100000;

        BudgetDebug(Budget aggregate) {
            this.aggregate = aggregate;
        }

        @Override
        public void onCall(LuaClosure closure, Varargs args, LuaValue[] stack) {
            if (frames.size() >= 32) {
                throw new BudgetExceeded("declaration recursion exceeds 32");
            }
            frames.add(stack);
            calls.push(Boolean.TRUE);
            super.onCall(closure, args, stack);
        }

        @Override
        public void onCall(LuaFunction function) {
            calls.push(Boolean.FALSE);
            super.onCall(function);
        }

        @Override
        public void onInstruction(int pc, Varargs args, int top) {
            aggregate.consume();
            if (--remaining < 0 || System.nanoTime() > deadline) {
                throw new BudgetExceeded("file instruction/deadline budget exhausted");
            }
            if (!frames.isEmpty()) {
                for (LuaValue value : frames.get(frames.size() - 1)) {
                    if (value != null && value.isfunction()
                            && value.getClass().getName().startsWith("org.luaj.vm2.lib.StringLib$")
                            && !Arrays.asList("byte", "char", "len", "lower", "upper", "reverse", "sub")
                                    .contains(value.getClass().getSimpleName())) {
                        throw new BudgetExceeded("unaudited string helper is unavailable during declaration");
                    }
                    if (value != null && (value.type() == LuaValue.TSTRING && value.rawlen() > 262144
                            || value.istable() && (value.rawlen() > 4096 || value.checktable().keyCount() > 4096))) {
                        throw new BudgetExceeded("declaration helper/value allocation bound exceeded");
                    }
                }
            }
            super.onInstruction(pc, args, top);
        }

        @Override
        public void onReturn() {
            if (!calls.isEmpty() && calls.pop()) {
                frames.remove(frames.size() - 1);
            }
            super.onReturn();
        }
    }

    private static final class BudgetExceeded extends Error {
        BudgetExceeded(String message) {
            super(message);
        }
    }
}

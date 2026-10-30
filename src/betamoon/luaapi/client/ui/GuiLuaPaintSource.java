package betamoon.luaapi.client.ui;

import betamoon.gui.drawing.GuiPaintProgram;
import betamoon.gui.drawing.GuiPaintView;
import betamoon.gui.presentation.GuiPresentationRuntime;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luamodloader.ScriptExecution;
import betamoon.luamodloader.ScriptResourceTracker;
import betamoon.interop.runtime.NativeContext;
import betamoon.interop.runtime.NativeEntry;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/**
 * Owner-attributed read-only builder. The resulting program retains no Lua
 * command tables.
 */
public final class GuiLuaPaintSource implements GuiPaintView.Source {
    private final String owner;
    private final String path;
    private final LuaValue paint;
    private final GuiPaintCompiler compiler;
    private final Function<LuaCallbackScope, LuaTable> context;
    private final ScriptResourceTracker.OwnerGeneration ownerState;
    private final long generation;

    public GuiLuaPaintSource(String owner, String path, LuaValue paint, GuiPaintCompiler compiler,
            Function<LuaCallbackScope, LuaTable> context) {
        if (paint == null || !paint.isfunction()) {
            throw new IllegalArgumentException("Prepared paint requires a function");
        }
        this.owner = owner;
        this.path = Objects.requireNonNull(path, "paint path");
        this.paint = paint;
        this.compiler = Objects.requireNonNull(compiler, "paint compiler");
        this.context = Objects.requireNonNull(context, "paint binding context");
        ownerState = ScriptResourceTracker.ownerToken(owner);
        generation = ownerState.current();
    }

    @Override
    public GuiPaintProgram prepare(int width, int height, Map<String, Object> properties) {
        if (!ownerState.matches(generation)) {
            throw new IllegalStateException(path + ": paint owner generation has expired");
        }
        try (LuaCallbackScope scope = LuaCallbackScope.binding()) {
            LuaTable view = context.apply(scope);
            view.set("width", width);
            view.set("height", height);
            view.set("props", GuiPresentationRuntime.lua(properties));
            LuaValue commands = ScriptExecution.invoke(owner, "ui.paint." + path, paint, view).arg1();
            scope.requireActive();
            if (!ownerState.matches(generation)) {
                throw new IllegalStateException(path + ": paint owner changed during preparation");
            }
            NativeEntry entry = scope.nativeEntry();
            try (NativeContext nativeContext = entry == null ? NativeContext.denied(owner) : entry.open(owner)) {
                return compiler.compile(commands, path + ".paint");
            }
        }
    }
}

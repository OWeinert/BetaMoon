package betamoon.utils;

import betamoon.io.FileIo;
import betamoon.luaapi.LuaApiUtils;
import betamoon.luaapi.utils.LuaCallbackScope;
import betamoon.luaapi.utils.LuaDeclarationValues;
import betamoon.luaapi.utils.LuaPositionArguments;
import betamoon.luaapi.utils.PositionF;
import betamoon.luaapi.utils.PositionI;
import betamoon.query.RecipeQueryUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import net.minecraft.src.ItemStack;
import net.minecraft.src.ShapedRecipes;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.lwjgl.input.Keyboard;

/** Direct checks for shared utility behavior and compatibility contracts. */
public final class UtilityContractsTest {
    private UtilityContractsTest() {
    }

    public static void main(String[] arguments) throws Exception {
        verifyNormalizedFileReading();
        verifyLuaArgumentResolution();
        verifyFiniteNumbers();
        verifyPositionArguments();
        verifyCallbackPermissions();
        verifyKeyLayouts();
        verifyReflectionFallbacks();
        verifyRecipeInputDelegation();
        System.out.println("Utility contracts passed: files, Lua arguments, callback permissions, "
                + "key layouts, reflection and recipes.");
    }

    private static void verifyNormalizedFileReading() throws IOException {
        Path file = Files.createTempFile("betamoon-file-io", ".lua");
        try {
            String source = "\uFEFFfirst\r\nsecond\rthird\n";
            Files.write(file, source.getBytes(StandardCharsets.UTF_8));
            require("first\nsecond\nthird\n".equals(FileIo.readUtf8Normalized(file.toFile())),
                    "UTF-8 BOM or line-ending normalization changed");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static void verifyLuaArgumentResolution() {
        Varargs direct = LuaValue.varargsOf(new LuaValue[]{LuaValue.valueOf(42), LuaValue.valueOf("direct")});
        require(LuaApiUtils.getNumberArg(direct, 1) == 42, "Direct numeric argument moved");
        require("direct".equals(LuaApiUtils.getStringArg(direct, 2)), "Direct string argument moved");

        LuaTable receiver = new LuaTable();
        Varargs method = LuaValue.varargsOf(new LuaValue[]{receiver, LuaValue.valueOf(17), LuaValue.valueOf("method")});
        require(LuaApiUtils.getVarArg(method, 1).checkint() == 17, "Method receiver was not skipped");
        require("method".equals(LuaApiUtils.getStringArg(method, 2)), "Method string argument moved");
    }

    private static void verifyFiniteNumbers() {
        require(LuaDeclarationValues.number(LuaValue.valueOf(1.25), "number") == 1.25,
                "Finite declaration number changed");
        boolean rejected = false;
        try {
            LuaDeclarationValues.number(LuaValue.valueOf(Double.NaN), "number");
        } catch (LuaError expected) {
            rejected = true;
        }
        require(rejected, "Non-finite declaration number was accepted");
    }

    private static void verifyPositionArguments() {
        LuaTable receiver = new LuaTable();
        LuaTable plain = new LuaTable();
        plain.set("x", 10);
        plain.set("y", 64);
        plain.set("z", 20);
        LuaPositionArguments.Position numeric = LuaPositionArguments.cell(
                LuaValue.varargsOf(new LuaValue[]{receiver, LuaValue.valueOf(10), LuaValue.valueOf(64),
                        LuaValue.valueOf(20), LuaValue.valueOf("tail")}), receiver, 1, "block");
        LuaPositionArguments.Position named = LuaPositionArguments.cell(
                LuaValue.varargsOf(new LuaValue[]{receiver, plain, LuaValue.valueOf("tail")}), receiver, 1, "block");
        require(numeric.x == named.x && numeric.y == named.y && numeric.z == named.z,
                "Numeric and named cell arguments differ");
        require(numeric.nextArgument == 4 && named.nextArgument == 2, "Trailing argument index was not shifted");
        require(LuaPositionArguments.cell(new PositionI(10, 64, 20), null, 1, "block").xInt() == 10,
                "Integer position object was not accepted");
        LuaTable legacy = new LuaTable();
        legacy.set("x", 10);
        legacy.set("z", 20);
        legacy.set("y", "ignored by X/Z lookup");
        legacy.set("extra", LuaValue.TRUE);
        require(LuaPositionArguments.horizontalCompatible(legacy, null, 1, "getChunk").zInt() == 20,
                "Existing X/Z table fields changed meaning");
        legacy.set("y", LuaValue.NIL);
        require(LuaPositionArguments.cellCompatibleDefaultY(legacy, null, 1, "getTile").yInt() == 0,
                "Existing world-data Y default changed");
        require(LuaPositionArguments.vector(new PositionF(0.25, 0.5, 1), null, 1, "motion").y == 0.5,
                "Float position object was not accepted");
        plain.set("x", 11);
        require(LuaPositionArguments.cell(plain, null, 1, "block").xInt() == 11,
                "Visible table mutation was ignored");
        plain.set("x", 11.5);
        rejectPosition(plain, "Fractional block coordinate was accepted");
        plain.set("x", 11);
        plain.set("unexpected", LuaValue.TRUE);
        rejectPosition(plain, "Unknown position field was accepted");
        plain.set("unexpected", LuaValue.NIL);
        plain.set("z", Double.POSITIVE_INFINITY);
        rejectPosition(plain, "Non-finite position coordinate was accepted");
    }

    private static void rejectPosition(LuaValue input, String message) {
        boolean rejected = false;
        try {
            LuaPositionArguments.cell(input, null, 1, "block");
        } catch (LuaError expected) {
            rejected = true;
        }
        require(rejected, message);
    }

    private static void verifyCallbackPermissions() {
        try (LuaCallbackScope input = LuaCallbackScope.withQueuedTeleport(false, true)) {
            input.requireQueuedTeleport();
            requireDenied(input::requireMutable, "Input scope gained immediate gameplay mutation");
            requireDenied(input::requireUiStateMutable, "Teleport permission granted UI state writes");

            try (LuaCallbackScope binding = LuaCallbackScope.binding()) {
                requireDenied(input::requireQueuedTeleport, "Binding queued a teleport through a captured handle");
                try (LuaCallbackScope nested = LuaCallbackScope.withQueuedTeleport(true, true)) {
                    requireDenied(nested::requireQueuedTeleport, "Nested access escaped binding permissions");
                }
            }
            input.requireQueuedTeleport();

            try (LuaCallbackScope query = new LuaCallbackScope(false)) {
                requireDenied(input::requireQueuedTeleport, "Query queued a teleport through a captured handle");
                try (LuaCallbackScope nested = LuaCallbackScope.withQueuedTeleport(true, true)) {
                    requireDenied(nested::requireQueuedTeleport, "Nested access escaped query permissions");
                }
            }
            input.requireQueuedTeleport();
        }

        try (LuaCallbackScope uiEvent = new LuaCallbackScope(false, true)) {
            uiEvent.requireUiStateMutable();
            requireDenied(uiEvent::requireMutable, "UI state permission granted gameplay writes");
            requireDenied(uiEvent::requireQueuedTeleport, "UI state permission granted teleportation");
        }

        LuaCallbackScope expired = new LuaCallbackScope(true);
        expired.requireMutable();
        expired.requireUiStateMutable();
        expired.requireQueuedTeleport();
        expired.close();
        expired.close();
        requireDenied(expired::requireQueuedTeleport, "Expired scope retained teleport permission");

        try (LuaCallbackScope gameplay = new LuaCallbackScope(true)) {
            gameplay.requireMutable();
            gameplay.requireUiStateMutable();
            gameplay.requireQueuedTeleport();
        }
    }

    private static void requireDenied(Runnable action, String message) {
        try {
            action.run();
        } catch (LuaError expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void verifyKeyLayouts() {
        KeyInputMapper.setLayout(KeyInputMapper.Layout.QWERTY);
        require(KeyInputMapper.map(Keyboard.KEY_Y, false, false, false) == 'y', "QWERTY mapping changed");

        KeyInputMapper.setLayout(KeyInputMapper.Layout.QWERTZ);
        require(KeyInputMapper.map(Keyboard.KEY_Y, true, false, false) == 'Z', "QWERTZ mapping changed");

        KeyInputMapper.setLayout(KeyInputMapper.Layout.AZERTY);
        require(KeyInputMapper.map(Keyboard.KEY_A, false, false, false) == 'q', "AZERTY mapping changed");
        KeyInputMapper.setLayout(null);
        require(KeyInputMapper.map(Keyboard.KEY_A, false, false, false) == 'q', "Null layout changed selection");

        KeyInputMapper.setLayout(KeyInputMapper.Layout.QWERTY);
    }

    private static void verifyReflectionFallbacks() {
        require("value".equals(ClassUtils.tryInvokeStatic(ReflectionTarget.class, "value")),
                "Class-based static invocation failed");
        require("value".equals(ClassUtils.tryInvokeStaticClass(ReflectionTarget.class.getName(), "value")),
                "Name-based static invocation failed");
        require(ClassUtils.tryInvokeStatic(ReflectionTarget.class, "missing") == null,
                "Missing methods must retain the null fallback");
        require(ClassUtils.tryInvokeStaticClass("missing.Type", "value") == null,
                "Missing classes must retain the null fallback");
    }

    private static void verifyRecipeInputDelegation() {
        ItemStack first = new ItemStack(1, 1, 0);
        ItemStack second = new ItemStack(2, 1, 0);
        ItemStack[] grid = {first, first.copy(), second};
        ShapedRecipes recipe = new ShapedRecipes(3, 1, grid, new ItemStack(3, 1, 0));

        require(RecipeQueryUtils.matchesInputs(recipe, Arrays.asList(first.copy(), first.copy())),
                "Duplicate recipe inputs no longer match independently");
        require(!RecipeQueryUtils.matchesInputs(recipe, Arrays.asList(first.copy(), first.copy(), first.copy())),
                "One recipe input matched more than once");
        require(RecipeQueryUtils.getRecipeOutput(recipe).itemID == 3, "Typed recipe output lookup changed");
        require(RecipeQueryUtils.getRecipeOutput(new Object()) == null, "Unknown recipe output must remain absent");
    }

    public static final class ReflectionTarget {
        private ReflectionTarget() {
        }

        public static String value() {
            return "value";
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

package betamoon.utils;

import betamoon.io.FileIo;
import betamoon.gui.api.util.GuiUtils;
import betamoon.luaapi.LuaApiUtils;
import betamoon.luaapi.utils.LuaDeclarationValues;
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
        verifyKeyLayouts();
        verifyReflectionFallbacks();
        verifyRecipeInputDelegation();
        verifyCompatibilityAlias();
        System.out.println("Utility contracts passed: files, Lua arguments, key layouts, reflection and recipes.");
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

    @SuppressWarnings("deprecation")
    private static void verifyCompatibilityAlias() {
        require(GuiUtils.COLOR_LIST_SEPARATOR == GuiUtils.COLOR_LIST_SEPERATOR,
                "Deprecated separator-color alias changed value");
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

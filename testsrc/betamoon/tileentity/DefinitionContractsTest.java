package betamoon.tileentity;

import betamoon.recipes.custom.RecipeDefinitionIsolationTest;
import betamoon.recipes.NativeRecipeInspectorTest;
import betamoon.luaapi.block.BlockShapeDefinition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.src.ItemStack;
import net.minecraft.src.NBTTagCompound;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Verifies that compiled definitions cannot be changed through their inputs or
 * views.
 */
public final class DefinitionContractsTest {
    private DefinitionContractsTest() {
    }

    public static void main(String[] arguments) {
        verifyTileAndContainerCollections();
        verifyTileDataPersistence();
        verifyGuiCollectionsAndStacks();
        verifyGuiRuntimePolicies();
        RecipeDefinitionIsolationTest.run();
        NativeRecipeInspectorTest.run();
        verifyBlockGeometry();
        System.out.println("Definition contracts passed: copied inputs, read-only views, ordering and item isolation.");
    }

    private static void verifyGuiRuntimePolicies() {
        require(GuiAnchor.TOP_LEFT.positionX(3, 20, 100) == 3 && GuiAnchor.TOP_LEFT.positionY(4, 10, 80) == 4,
                "Top-left anchoring must preserve offsets");
        require(GuiAnchor.CENTER.positionX(3, 20, 100) == 43 && GuiAnchor.CENTER.positionY(4, 10, 80) == 39,
                "Center anchoring must offset from the midpoint");
        require(GuiAnchor.BOTTOM_RIGHT.positionX(-3, 20, 100) == 77
                && GuiAnchor.BOTTOM_RIGHT.positionY(-4, 10, 80) == 66,
                "Bottom-right anchoring must offset from the far edges");

        assertSlice(ProgressDirection.LEFT_TO_RIGHT.slice(100, 12, 1, 4, 0), 0, 0, 25, 12, "Left-to-right progress");
        assertSlice(ProgressDirection.RIGHT_TO_LEFT.slice(100, 12, 1, 4, 0), 75, 0, 25, 12, "Right-to-left progress");
        assertSlice(ProgressDirection.TOP_TO_BOTTOM.slice(10, 80, 1, 4, 0), 0, 0, 10, 20, "Top-to-bottom progress");
        assertSlice(ProgressDirection.BOTTOM_TO_TOP.slice(10, 80, 1, 4, 0), 0, 60, 10, 20, "Bottom-to-top progress");
        assertSlice(ProgressDirection.LEFT_TO_RIGHT.slice(100, 12, 1, 100, 6), 0, 0, 6, 12, "Minimum progress pixels");
        require(ProgressDirection.LEFT_TO_RIGHT.slice(100, 12, 0, 100, 6).isEmpty(),
                "Minimum pixels must not make empty progress visible");

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("count", Integer.valueOf(3));
        values.put("active", Boolean.TRUE);
        ContainerGuiDefinition.Condition countEquals = condition("count", GuiConditionOperator.EQUALS,
                Double.valueOf(3.0D));
        ContainerGuiDefinition.Condition countGreater = condition("count", GuiConditionOperator.GREATER_THAN,
                Integer.valueOf(2));
        ContainerGuiDefinition.Condition inactive = condition("active", GuiConditionOperator.NOT_EQUALS, Boolean.TRUE);
        require(GuiConditionEvaluator.evaluate(countEquals, values::get),
                "Numeric equality must compare values across number wrappers");
        require(GuiConditionEvaluator.evaluate(countGreater, values::get),
                "Ordered conditions must compare current tile values");
        require(!GuiConditionEvaluator.evaluate(inactive, values::get),
                "Not-equals must invert the same equality policy");

        ContainerGuiDefinition.Condition all = new ContainerGuiDefinition.Condition(null, GuiConditionOperator.ALL,
                null, Arrays.asList(countEquals, countGreater));
        ContainerGuiDefinition.Condition any = new ContainerGuiDefinition.Condition(null, GuiConditionOperator.ANY,
                null, Arrays.asList(inactive, countGreater));
        require(GuiConditionEvaluator.evaluate(all, values::get), "All groups must require every child");
        require(GuiConditionEvaluator.evaluate(any, values::get), "Any groups must accept one matching child");
    }

    private static ContainerGuiDefinition.Condition condition(String field, GuiConditionOperator operator,
            Object expected) {
        return new ContainerGuiDefinition.Condition(field, operator, expected, null);
    }

    private static void assertSlice(ProgressDirection.Slice slice, int x, int y, int width, int height,
            String description) {
        require(slice.x == x && slice.y == y && slice.width == width && slice.height == height,
                description + " produced unexpected geometry");
    }

    private static void verifyTileAndContainerCollections() {
        Map<String, Integer> slots = new LinkedHashMap<>();
        slots.put("input", 0);
        slots.put("output", 1);
        Map<String, TileEntityDefinition.Field> fields = new LinkedHashMap<>();
        fields.put("progress", new TileEntityDefinition.Field("progress", TileDataType.INTEGER, 0, true));
        TileEntityDefinition tile = new TileEntityDefinition("test", "test.lua", "Test", slots, fields, LuaValue.NIL,
                LuaValue.NIL, 0, 0, false, 0);
        slots.clear();
        fields.clear();
        require(new ArrayList<>(tile.slots.keySet()).equals(Arrays.asList("input", "output")),
                "Tile slots must preserve the declaration order independently of the source map");
        require(tile.fields.containsKey("progress"), "Tile fields must be copied");
        expectReadOnly(() -> tile.slots.put("extra", 2));
        expectReadOnly(() -> tile.fields.clear());

        List<ContainerDefinition.SlotDefinition> visible = new ArrayList<>();
        visible.add(new ContainerDefinition.SlotDefinition("input", 0, 8, 18, false));
        ContainerDefinition container = new ContainerDefinition("test", "test.lua", tile, visible, 8, 84, true);
        visible.clear();
        require(container.slots.size() == 1, "Container slots must be copied");
        expectReadOnly(() -> container.slots.clear());
    }

    private static void verifyGuiCollectionsAndStacks() {
        List<String> tooltip = new ArrayList<>(Collections.singletonList("Original"));
        ItemStack source = new ItemStack(1, 2, 0);
        ContainerGuiDefinition.ItemElement item = new ContainerGuiDefinition.ItemElement(0, 0, 0, GuiAnchor.TOP_LEFT,
                null, tooltip, null, source, true);
        source.stackSize = 10;
        tooltip.set(0, "Changed");
        require(item.getItem().stackSize == 2, "GUI item must copy its input stack");
        ItemStack display = item.getItem();
        display.stackSize = 20;
        require(item.getItem().stackSize == 2, "Returned display stack must not mutate the definition");
        require(item.tooltip.equals(Collections.singletonList("Original")), "Tooltip must copy its input");
        expectReadOnly(() -> item.tooltip.clear());

        List<ContainerGuiDefinition.Element> elements = new ArrayList<>();
        elements.add(item);
        ContainerGuiDefinition gui = new ContainerGuiDefinition("test", "test.lua", null, 176, 166, null, null, null,
                false, elements);
        elements.clear();
        require(gui.elements.size() == 1 && gui.elements.get(0) == item, "GUI elements must be copied in order");
        expectReadOnly(() -> gui.elements.clear());

        List<ContainerGuiDefinition.Condition> children = new ArrayList<>();
        children.add(new ContainerGuiDefinition.Condition("progress", GuiConditionOperator.EQUALS, 1, null));
        ContainerGuiDefinition.Condition condition = new ContainerGuiDefinition.Condition(null,
                GuiConditionOperator.ALL, null, children);
        children.clear();
        require(condition.children.size() == 1, "Conditions must copy child lists");
        expectReadOnly(() -> condition.children.clear());

        Map<String, ContainerGuiDefinition.Texture> states = new LinkedHashMap<>();
        ContainerGuiDefinition.Texture texture = new ContainerGuiDefinition.Texture("test", 0, 0, 16, 16, 16, 16);
        states.put("true", texture);
        ContainerGuiDefinition.StateImageElement state = new ContainerGuiDefinition.StateImageElement(0, 0, 0,
                GuiAnchor.TOP_LEFT, null, null, "active", states, null);
        states.clear();
        require(state.states.get("true") == texture, "State textures must be copied");
        require(state.tooltip.isEmpty(), "An omitted tooltip must compile to an empty input-safe list");
        expectReadOnly(() -> state.states.clear());
    }

    private static void verifyTileDataPersistence() {
        Map<String, TileEntityDefinition.Field> fields = new LinkedHashMap<>();
        fields.put("count", new TileEntityDefinition.Field("count", TileDataType.INTEGER, Integer.valueOf(3), true));
        fields.put("ratio", new TileEntityDefinition.Field("ratio", TileDataType.NUMBER, Double.valueOf(1.5D), false));
        fields.put("active", new TileEntityDefinition.Field("active", TileDataType.BOOLEAN, Boolean.FALSE, true));
        fields.put("label", new TileEntityDefinition.Field("label", TileDataType.STRING, "default", false));
        TileEntityDefinition definition = new TileEntityDefinition("data", "test.lua", "Data",
                Collections.<String, Integer>emptyMap(), fields, LuaValue.NIL, LuaValue.NIL, 0, 0, false, 0.0D);

        TileDataStore source = new TileDataStore();
        source.initialize(definition);
        require(Integer.valueOf(3).equals(source.get("count")) && "default".equals(source.get("label")),
                "Tile data defaults were not initialized");
        source.set(definition, "count", Integer.valueOf(9));
        source.set(definition, "ratio", Double.valueOf(2.25D));
        source.set(definition, "active", Boolean.TRUE);
        source.set(definition, "label", "saved");

        NBTTagCompound tag = new NBTTagCompound();
        source.write(tag, definition);
        require(tag.hasKey("Data_count") && tag.hasKey("Data_ratio") && tag.hasKey("Data_active")
                && tag.hasKey("Data_label"), "Tile data NBT keys changed");

        TileDataStore restored = new TileDataStore();
        restored.initialize(definition);
        restored.read(tag, definition);
        require(Integer.valueOf(9).equals(restored.get("count")), "Integer tile data did not round-trip");
        require(Double.valueOf(2.25D).equals(restored.get("ratio")), "Number tile data did not round-trip");
        require(Boolean.TRUE.equals(restored.get("active")), "Boolean tile data did not round-trip");
        require("saved".equals(restored.get("label")), "String tile data did not round-trip");
        require(restored.getSyncValue("active") == 1, "Boolean synchronization value changed");
        restored.setSyncValue(definition, "active", 0);
        require(Boolean.FALSE.equals(restored.get("active")), "Boolean synchronization conversion changed");

        expectLuaError(() -> restored.set(definition, "count", Double.valueOf(1.0D)));
        expectLuaError(() -> restored.setSyncValue(definition, "label", 1));
        require(TileDataType.parse("INTEGER") == TileDataType.INTEGER, "Tile data type parsing must ignore case");
        expectLuaError(() -> TileDataType.parse("object"));
    }

    private static void expectReadOnly(Runnable mutation) {
        try {
            mutation.run();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("Definition view allowed mutation");
    }

    private static void expectLuaError(Runnable action) {
        try {
            action.run();
        } catch (LuaError expected) {
            return;
        }
        throw new AssertionError("Expected a LuaError");
    }

    private static void verifyBlockGeometry() {
        LuaValue declaration = JsePlatform.standardGlobals()
                .load("local box={min={0,0,0}, max={1,1,1}}; return {collision={boxes={box}}, selection=box}").call();
        BlockShapeDefinition shape = new BlockShapeDefinition(declaration);
        declaration.get("selection").get("max").set(1, LuaValue.valueOf(0.5));
        require(shape.selection.isFullCube() && shape.hasFullCubeCollision(),
                "Compiled geometry must remain independent of Lua coordinate arrays");
        expectReadOnly(() -> shape.boxes.clear());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

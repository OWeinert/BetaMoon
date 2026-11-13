package betamoon.gui.control;

import betamoon.gui.graphics.GuiResourceDefinitions;
import betamoon.luaapi.client.ui.GuiResourceParser;
import java.util.LinkedHashMap;
import java.util.Map;
import org.luaj.vm2.lib.jse.JsePlatform;

/** Value/geometry and symbolic skin admission, without a native window. */
public final class GuiIconMeterTest {
    public static void run() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("maximum", 20);
        values.put("unitsPerIcon", 2);
        values.put("maxIcons", 10);
        GuiIconMeterModel hearts = model(values);
        require(hearts.capacity == 10 && hearts.measuredWidth() == 81 && hearts.measuredHeight() == 9,
                "Native icon overlap/extent changed");
        for (int value = -1; value <= 21; value++) {
            double sum = 0;
            for (int index = 0; index < hearts.capacity; index++) {
                sum += hearts.fraction(index, value) * hearts.units;
            }
            require(sum == Math.max(0, Math.min(20, value)), "Half/full/clamped icon fractions lost a unit");
        }
        values.put("maximum", 5);
        values.put("fillOrder", "right_to_left");
        GuiIconMeterModel odd = model(values);
        require(odd.capacity == 3 && odd.fraction(2, 5) == 0.5 && odd.column(0) == 2,
                "Odd maximum rounded up or reverse fill lost its anchor");
        values.put("columns", 2);
        values.put("fillOrder", "bottom_to_top");
        GuiIconMeterModel vertical = model(values);
        require(vertical.rows() == 2 && vertical.row(0) == 1 && vertical.column(2) == 1,
                "Vertical/wrapped icon geometry is inconsistent");
        for (String invalid : new String[]{"unitsPerIcon", "maximum", "maxIcons"}) {
            Map<String, Object> bad = new LinkedHashMap<>(values);
            bad.put(invalid, 0);
            try {
                model(bad);
            } catch (IllegalArgumentException expected) {
                continue;
            }
            throw new AssertionError("Invalid icon range/capacity accepted: " + invalid);
        }
        GuiResourceDefinitions resources = parse("drawables={full={color='#00FF00'},half={color='#FFFF00'}},"
                + "iconSkins={custom={full='full',partials={{fraction=0.5,drawable='half'}}}}");
        require(resources.entries.size() == 3, "Icon skins were not parsed as bounded named resources");
        for (String invalid : new String[]{"iconSkins={custom={full='missing'}}",
                "drawables={full={color='#FFFFFF'}},iconSkins={custom={full='full',"
                        + "partials={{fraction=0.7,drawable='full'},{fraction=0.5,drawable='full'}}}}"}) {
            try {
                parse(invalid);
            } catch (RuntimeException expected) {
                continue;
            }
            throw new AssertionError("Invalid icon resource graph accepted");
        }
        System.out.println(
                "Icon meter model/skin checks passed: fractions, odd capacity, overlap, wrapping and resource admission.");
    }

    private static GuiIconMeterModel model(Map<String, Object> values) {
        return new GuiIconMeterModel(values, 9, 9, 8, 9);
    }

    private static GuiResourceDefinitions parse(String fields) {
        return new GuiResourceParser().parse(JsePlatform.standardGlobals().load("return {" + fields + "}").call(),
                "resources");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

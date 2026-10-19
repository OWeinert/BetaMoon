package betamoon.gui.animation;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

/** Finite-duration curves shared by GUI tracks and the Lua calculation helpers. */
public interface GuiEasing {
    double sample(double progress);

    static GuiEasing parse(LuaValue value) {
        if (value.type() == LuaValue.TSTRING) {
            switch (value.checkjstring()) {
                case "linear":
                    return p -> p;
                case "ease_in":
                    return bezier(0.42, 0, 1, 1);
                case "ease_out":
                    return bezier(0, 0, 0.58, 1);
                case "ease_in_out":
                    return bezier(0.42, 0, 0.58, 1);
                case "bounce_out":
                    return GuiEasing::bounce;
                case "bounce_in":
                    return p -> 1 - bounce(1 - p);
                case "bounce_in_out":
                    return p -> p < 0.5 ? (1 - bounce(1 - 2 * p)) / 2 : (1 + bounce(2 * p - 1)) / 2;
                default:
                    throw new GuiAnimationValueError("Unknown GUI easing: " + value.tojstring());
            }
        }
        if (!value.istable() || value.checktable().keys().length != 1 || !value.get("bezier").istable()) {
            throw new LuaError("easing must be a preset name or {bezier={x1,y1,x2,y2}}.");
        }
        LuaValue points = value.get("bezier");
        if (points.length() != 4 || points.checktable().keys().length != 4) {
            throw new LuaError("bezier requires exactly four coordinates.");
        }
        return bezier(number(points.get(1)), number(points.get(2)), number(points.get(3)), number(points.get(4)));
    }

    static double number(LuaValue value) {
        if (value.type() != LuaValue.TNUMBER || !Double.isFinite(value.todouble())) {
            throw new GuiAnimationValueError("GUI numbers must be finite numeric values, not numeric strings.");
        }
        return value.todouble();
    }

    static GuiEasing bezier(double x1, double y1, double x2, double y2) {
        if (!Double.isFinite(x1) || !Double.isFinite(y1) || !Double.isFinite(x2) || !Double.isFinite(y2)
                || x1 < 0 || x1 > 1 || x2 < 0 || x2 > 1) {
            throw new GuiAnimationValueError("Bezier coordinates must be finite and x coordinates must be in 0..1.");
        }
        return p -> {
            if (p <= 0 || p >= 1) {
                return p <= 0 ? 0 : 1;
            }
            double low = 0;
            double high = 1;
            // Bisection handles curves with zero derivative without Newton instability.
            for (int i = 0; i < 36; i++) {
                double t = (low + high) * 0.5;
                if (cubic(t, x1, x2) < p) {
                    low = t;
                } else {
                    high = t;
                }
            }
            return cubic((low + high) * 0.5, y1, y2);
        };
    }

    static double cubic(double t, double a, double b) {
        double u = 1 - t;
        return 3 * u * u * t * a + 3 * u * t * t * b + t * t * t;
    }

    static double bounce(double p) {
        double n = 7.5625;
        double d = 2.75;
        if (p < 1 / d) {
            return n * p * p;
        }
        if (p < 2 / d) {
            p -= 1.5 / d;
            return n * p * p + 0.75;
        }
        if (p < 2.5 / d) {
            p -= 2.25 / d;
            return n * p * p + 0.9375;
        }
        p -= 2.625 / d;
        return n * p * p + 0.984375;
    }
}

package betamoon.tileentity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.luaj.vm2.LuaValue;

/** Immutable gameplay definition for one interactive container control. */
public final class ContainerControlDefinition {
    public enum Type {
        ACTION,
        TOGGLE,
        NUMBER,
        TEXT,
        CHOICE,
        CUSTOM
    }

    public enum Source {
        NONE,
        DATA,
        SESSION
    }

    public final String name;
    public final Type type;
    public final Source source;
    public final String field;
    public final double minimum;
    public final double maximum;
    public final double step;
    public final double pageStep;
    public final int maximumLength;
    public final boolean wrap;
    public final List<Object> choices;
    public final ContainerGuiDefinition.Condition enabledWhen;
    public final LuaValue beforeChange;
    public final LuaValue onActivate;
    public final LuaValue onChange;
    public final LuaValue onEdit;
    public final LuaValue onCommit;
    public final LuaValue onInput;

    public ContainerControlDefinition(String name, Type type, Source source, String field,
            double minimum, double maximum, double step, double pageStep, int maximumLength, boolean wrap,
            List<Object> choices, ContainerGuiDefinition.Condition enabledWhen, LuaValue beforeChange,
            LuaValue onActivate, LuaValue onChange, LuaValue onEdit, LuaValue onCommit, LuaValue onInput) {
        this.name = name;
        this.type = type;
        this.source = source;
        this.field = field;
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
        this.pageStep = pageStep;
        this.maximumLength = maximumLength;
        this.wrap = wrap;
        this.choices = Collections.unmodifiableList(new ArrayList<Object>(choices));
        this.enabledWhen = enabledWhen;
        this.beforeChange = beforeChange;
        this.onActivate = onActivate;
        this.onChange = onChange;
        this.onEdit = onEdit;
        this.onCommit = onCommit;
        this.onInput = onInput;
    }

    public boolean hasValue() {
        return source != Source.NONE;
    }
}

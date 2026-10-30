package betamoon.gui.style;

import betamoon.gui.drawing.GuiPaintProgram;
import betamoon.gui.framework.GuiContext;
import betamoon.gui.widget.GuiStyledView;
import betamoon.luaapi.utils.LuaCallbackScope;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * One Lua build prepares all declared state alternatives; hover, focus and
 * caret blink select in Java.
 */
public final class GuiPreparedTemplate implements GuiVisualTemplate {
    public interface Source {
        GuiPaintProgram[] prepare(GuiContext context, GuiStyledView view);

        default void accept(GuiPaintProgram[] programs) {
        }
    }
    private final Set<String> parts;
    private Source source;
    private Consumer<Throwable> failures;
    private List<Object> attempted;
    private GuiPaintProgram[] programs;
    private GuiPaintProgram.Frame lastFrame;
    private Map<String, Object> accepted = Collections.emptyMap();
    private Throwable pendingFailure;
    private boolean samplingFailed;
    private boolean useCurrent;
    private GuiPaintProgram sampledProgram;
    private long sampledRevision;
    private int sampledWidth;
    private int sampledHeight;

    public GuiPreparedTemplate(Set<String> parts, Source source, Consumer<Throwable> failures) {
        this.parts = parts;
        this.source = Objects.requireNonNull(source, "template source");
        this.failures = failures;
    }

    public Set<String> parts() {
        return parts;
    }

    @Override
    public void prepare(GuiContext context, GuiStyledView view) {
        if (pendingFailure != null) {
            Throwable failure = pendingFailure;
            pendingFailure = null;
            failures.accept(failure);
        }
        List<Object> next = Arrays.asList(view.getWidth(), view.getHeight(), view.templateValues(true),
                view.editor() == null ? 0 : view.editor().caret(),
                view.editor() == null ? 0 : view.editor().selectionStart(),
                view.editor() == null ? 0 : view.editor().selectionEnd(), view.textOffset());
        if (next.equals(attempted)) {
            return;
        }
        attempted = next;
        try (LuaCallbackScope scope = LuaCallbackScope.binding()) {
            GuiPaintProgram[] candidate = source.prepare(context, view);
            if (candidate == null || candidate.length != 128) {
                throw new IllegalArgumentException("Template must prepare its finite state table");
            }
            Map<String, Object> values = view.templateValues(true);
            Map<GuiPaintProgram, Boolean> unique = new IdentityHashMap<>();
            for (GuiPaintProgram program : candidate) {
                if (unique.put(program, true) == null) {
                    program.sample(view.getWidth(), view.getHeight(), values);
                }
            }
            source.accept(candidate);
            programs = candidate.clone();
            sampledProgram = null;
            accepted = values;
            useCurrent = true;
            samplingFailed = false;
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            useCurrent = false;
            failures.accept(failure);
            if (programs == null) {
                throw new IllegalArgumentException("Template has no usable prepared paint", failure);
            }
        }
    }

    @Override
    public void paint(GuiContext context, GuiStyledView view, GuiControlStyle style) {
        if (programs == null) {
            return;
        }
        int state = view.interactionStates() | (view.editor() != null && view.caretVisible(context) ? 64 : 0);
        GuiPaintProgram program = programs[state];
        long revision = useCurrent ? view.templateRevision() : -1;
        if (program != sampledProgram || revision != sampledRevision || sampledWidth != view.getWidth()
                || sampledHeight != view.getHeight()) {
            sampledProgram = program;
            sampledRevision = revision;
            sampledWidth = view.getWidth();
            sampledHeight = view.getHeight();
            try {
                lastFrame = program.sample(view.getWidth(), view.getHeight(),
                        useCurrent ? view.templateValues(false) : accepted);
                samplingFailed = false;
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable failure) {
                if (!samplingFailed) {
                    samplingFailed = true;
                    pendingFailure = failure;
                }
                if (lastFrame != null) {
                    try {
                        lastFrame.requireActive();
                    } catch (RuntimeException expired) {
                        lastFrame = null;
                    }
                }
            }
        }
        if (lastFrame != null) {
            context.getRenderer().pushClip(view.getBounds());
            try {
                lastFrame.paint(context, view.getLeft(), view.getTop());
            } finally {
                context.getRenderer().popClip();
            }
        }
    }

    @Override
    public void close() {
        source = null;
        failures = null;
        sampledProgram = null;
        attempted = null;
        programs = null;
        lastFrame = null;
        accepted = Collections.emptyMap();
        pendingFailure = null;
    }
}

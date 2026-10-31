package betamoon.gui.drawing;

import betamoon.gui.framework.GuiPreparationPhase;
import betamoon.gui.graphics.GraphicsExpression;
import betamoon.interop.runtime.NativeContext;
import betamoon.luaapi.utils.LuaCallbackScope;
import java.util.Arrays;
import java.util.Collections;

/**
 * Complete prospective drawing deltas, including nested prepared rows, are
 * admitted before adoption.
 */
public final class GuiPaintScopeTest {
    public static void main(String[] arguments) {
        GuiPaintProgram full = rectangles(1024);
        GuiPaintProgram one = rectangles(1);
        try (GuiPaintBudget root = new GuiPaintBudget(); GuiPreparationPhase preparation = GuiPreparationPhase.open()) {
            GuiPaintBudget first = root.scope();
            GuiPaintBudget second = root.scope();
            GuiPaintBudget third = root.scope();
            GuiPaintBudget fourth = root.scope();
            for (GuiPaintBudget scope : Arrays.asList(first, second, third, fourth)) {
                scope.accept("same-path", new GuiPaintProgram[]{full});
            }
            GuiPaintBudget.activateReplacing(Arrays.asList(first, second, third, fourth), Collections.emptyList());
            expect(() -> root.accept("extra", new GuiPaintProgram[]{one}));
            GuiPaintBudget replacement = root.scope();
            replacement.accept("same-path", new GuiPaintProgram[]{full});
            expect(() -> GuiPaintBudget.activateReplacing(Collections.singletonList(replacement),
                    Collections.emptyList()));
            require(!replacement.active() && first.active(), "Refused paint adoption partly changed live scopes");
            GuiPaintBudget.validateReplacing(Collections.singletonList(replacement), Collections.singletonList(first));
            require(!replacement.active() && first.active(), "Paint validation adopted a candidate");
            GuiPaintBudget.activateReplacing(Collections.singletonList(replacement), Collections.singletonList(first));
            require(replacement.active() && !first.active(), "A valid complete paint delta was not adopted");
            first.close();
            fourth.close();
            GuiPaintBudget parent = root.scope();
            GuiPaintBudget child = parent.scope();
            GuiPaintBudget grandchild = child.scope();
            child.accept("same-path", new GuiPaintProgram[]{full});
            grandchild.accept("same-path", new GuiPaintProgram[]{one});
            GuiPaintBudget.activateReplacing(Arrays.asList(child, grandchild), Collections.emptyList());
            expect(() -> GuiPaintBudget.activateReplacing(Collections.singletonList(parent), Collections.emptyList()));
            require(!parent.active() && child.active(), "Nested drawing exceeded the prospective parent budget");
            grandchild.close();
            GuiPaintBudget.activateReplacing(Collections.singletonList(parent), Collections.emptyList());
            expect(() -> root.accept("extra", new GuiPaintProgram[]{one}));
            parent.close();
            root.accept("extra", new GuiPaintProgram[]{one});
            expect(() -> child.accept("stale", new GuiPaintProgram[]{one}));
            root.clear();
            expect(() -> GuiPaintBudget.activateReplacing(Arrays.asList(second, second), Collections.emptyList()));
            try (NativeContext callback = NativeContext.denied("paint-scope-test.lua")) {
                expect(root::scope);
            }
            try (LuaCallbackScope callback = LuaCallbackScope.binding()) {
                expect(root::scope);
            }
        }
        metadataBudget(full);
        System.out.println("Paint scopes passed: full-budget replacement, atomic refusal, nested admission, metadata "
                + "bounds, Java preparation, scope expiry and disposal.");
    }

    private static void metadataBudget(GuiPaintProgram program) {
        GuiPaintBudget stale;
        GuiPaintBudget root = new GuiPaintBudget();
        try (GuiPreparationPhase preparation = GuiPreparationPhase.open()) {
            for (int index = 0; index < 16; index++) {
                GuiPaintBudget scope = root.scope();
                scope.accept("candidate", new GuiPaintProgram[]{program});
            }
            stale = root.scope();
            expect(() -> stale.accept("overflow", new GuiPaintProgram[]{program}));
            stale.accept("empty", new GuiPaintProgram[]{new GuiPaintProgram.Builder().build()});
        }
        root.close();
        root.close();
        expect(() -> stale.accept("stale", new GuiPaintProgram[]{program}));
    }

    private static GuiPaintProgram rectangles(int count) {
        GuiPaintProgram.Builder builder = new GuiPaintProgram.Builder();
        GraphicsExpression zero = GraphicsExpression.constant(0);
        GraphicsExpression one = GraphicsExpression.constant(1);
        for (int index = 0; index < count; index++) {
            builder.rectangle(zero, zero, one, one, 0xFFFFFFFF);
        }
        return builder.build();
    }

    private static void expect(Runnable operation) {
        try {
            operation.run();
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return;
        }
        throw new AssertionError("Invalid paint scope operation was accepted");
    }

    private static void require(boolean accepted, String message) {
        if (!accepted) {
            throw new AssertionError(message);
        }
    }
}

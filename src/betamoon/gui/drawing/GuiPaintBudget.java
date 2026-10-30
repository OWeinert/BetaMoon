package betamoon.gui.drawing;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Atomic document accounting includes complete template alternatives as well as
 * visible draw cost.
 */
public final class GuiPaintBudget {
    private final Map<String, Cost> sources = new LinkedHashMap<>();
    public void accept(String key, GuiPaintProgram[] programs) {
        Cost candidate = new Cost(programs);
        Cost total = new Cost();
        total.add(candidate);
        for (Map.Entry<String, Cost> entry : sources.entrySet()) {
            if (!entry.getKey().equals(key)) {
                total.add(entry.getValue());
            }
        }
        if (total.commands > 4096 || total.vertices > 65536 || total.hitVertices > 16384 || total.expressions > 32768
                || total.text > 131072 || total.metadataCommands > 16384 || total.metadataVertices > 262144
                || total.metadataExpressions > 131072 || total.metadataText > 524288) {
            throw new IllegalArgumentException("UI document exceeds its prepared paint/template budget");
        }
        sources.put(key, candidate);
    }

    public void clear() {
        sources.clear();
    }
    private static final class Cost {
        int commands;
        int vertices;
        int hitVertices;
        int expressions;
        int text;
        int metadataCommands;
        int metadataVertices;
        int metadataExpressions;
        int metadataText;
        Cost() {
        }

        Cost(GuiPaintProgram[] programs) {
            if (programs.length < 1 || programs.length > 128) {
                throw new IllegalArgumentException("Paint alternative budget exceeded");
            }
            Map<GuiPaintProgram, Boolean> unique = new IdentityHashMap<>();
            for (GuiPaintProgram program : programs) {
                if (unique.put(program, true) != null) {
                    continue;
                }
                commands = Math.max(commands, program.commands());
                vertices = Math.max(vertices, program.vertices());
                hitVertices = Math.max(hitVertices, program.hitVertices());
                expressions = Math.max(expressions, program.expressions());
                text = Math.max(text, program.textUnits());
                metadataCommands += program.commands();
                metadataVertices += program.vertices();
                metadataExpressions += program.expressions();
                metadataText += program.textUnits();
            }
        }

        void add(Cost cost) {
            commands += cost.commands;
            vertices += cost.vertices;
            hitVertices += cost.hitVertices;
            expressions += cost.expressions;
            text += cost.text;
            metadataCommands += cost.metadataCommands;
            metadataVertices += cost.metadataVertices;
            metadataExpressions += cost.metadataExpressions;
            metadataText += cost.metadataText;
        }
    }
}

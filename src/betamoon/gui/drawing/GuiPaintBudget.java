package betamoon.gui.drawing;

import betamoon.gui.framework.GuiPreparationPhase;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Collection;
import java.util.Set;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

/**
 * Atomic document accounting includes complete template alternatives as well as
 * visible draw cost.
 */
public final class GuiPaintBudget implements AutoCloseable {
    private final Ledger ledger;
    private final GuiPaintBudget parent;
    private final int depth;
    private final boolean root;
    private final Map<String, Cost> sources = new LinkedHashMap<>();
    private boolean closed;
    private boolean active;

    public GuiPaintBudget() {
        ledger = new Ledger();
        parent = null;
        depth = 0;
        root = true;
        active = true;
        ledger.scopes.put(this, Boolean.TRUE);
    }

    private GuiPaintBudget(GuiPaintBudget parent) {
        this.parent = parent;
        ledger = parent.ledger;
        depth = parent.depth + 1;
        root = false;
        ledger.scopes.put(this, Boolean.TRUE);
    }

    /**
     * Candidate scopes reserve metadata immediately. Displayed cost is admitted as
     * one complete row replacement; nested scopes become effective with their
     * parent.
     */
    public GuiPaintBudget scope() {
        requireOpen();
        if (!GuiPreparationPhase.active()) {
            throw new IllegalStateException("Paint scopes require Java logical preparation outside callbacks");
        }
        if (ledger.scopes.size() >= 4096 || depth >= 63) {
            throw new IllegalArgumentException("UI document exceeds 4096 paint scopes or 64 scope levels");
        }
        return new GuiPaintBudget(this);
    }

    public void accept(String key, GuiPaintProgram[] programs) {
        requireOpen();
        Objects.requireNonNull(key, "paint source identity");
        Cost candidate = new Cost(programs);
        Cost own = cost(key, candidate);
        requireDisplayed(own);
        Cost total = new Cost();
        for (GuiPaintBudget scope : ledger.scopes.keySet()) {
            Cost cost = scope == this ? own : scope.cost(null, null);
            total.addMetadata(cost);
            if (scope.effective(Collections.emptyMap())) {
                total.addDisplayed(cost);
            }
        }
        requireDisplayed(total);
        if (total.metadataCommands > 16384 || total.metadataVertices > 262144 || total.metadataExpressions > 131072
                || total.metadataText > 524288) {
            throw new IllegalArgumentException("UI document exceeds its prepared paint/template budget");
        }
        sources.put(key, candidate);
    }

    private static void requireDisplayed(Cost total) {
        if (total.commands > 4096 || total.vertices > 65536 || total.hitVertices > 16384 || total.expressions > 32768
                || total.text > 131072) {
            throw new IllegalArgumentException("UI document exceeds its displayed paint/template budget");
        }
    }

    private boolean effective(Map<GuiPaintBudget, Boolean> changes) {
        for (GuiPaintBudget scope = this; scope != null; scope = scope.parent) {
            if (!changes.getOrDefault(scope, scope.active)) {
                return false;
            }
        }
        return true;
    }

    public static void validateReplacing(Collection<GuiPaintBudget> candidates,
            Collection<GuiPaintBudget> predecessors) {
        replace(candidates, predecessors, false);
    }

    public static void activateReplacing(Collection<GuiPaintBudget> candidates,
            Collection<GuiPaintBudget> predecessors) {
        replace(candidates, predecessors, true);
    }

    private static void replace(Collection<GuiPaintBudget> candidates, Collection<GuiPaintBudget> predecessors,
            boolean activate) {
        Map<GuiPaintBudget, Boolean> changes = new IdentityHashMap<>();
        Set<Ledger> ledgers = Collections.newSetFromMap(new IdentityHashMap<Ledger, Boolean>());
        for (GuiPaintBudget candidate : candidates) {
            checked(candidate, false, changes, ledgers);
        }
        for (GuiPaintBudget previous : predecessors) {
            checked(previous, true, changes, ledgers);
        }
        for (Ledger ledger : ledgers) {
            Cost total = new Cost();
            for (GuiPaintBudget scope : ledger.scopes.keySet()) {
                if (scope.effective(changes)) {
                    total.addDisplayed(scope.cost(null, null));
                }
            }
            requireDisplayed(total);
        }
        if (activate) {
            for (Map.Entry<GuiPaintBudget, Boolean> change : changes.entrySet()) {
                change.getKey().active = change.getValue();
            }
        }
    }

    private static void checked(GuiPaintBudget scope, boolean expected, Map<GuiPaintBudget, Boolean> changes,
            Set<Ledger> ledgers) {
        Objects.requireNonNull(scope, "row paint scope");
        scope.requireOpen();
        if (scope.root || scope.active != expected || changes.put(scope, !expected) != null) {
            throw new IllegalArgumentException("Row paint scopes must be unique and in their expected phase");
        }
        ledgers.add(scope.ledger);
    }

    private Cost cost(String replacement, Cost candidate) {
        Cost total = new Cost();
        for (Map.Entry<String, Cost> entry : sources.entrySet()) {
            if (!entry.getKey().equals(replacement)) {
                total.add(entry.getValue());
            }
        }
        if (candidate != null) {
            total.add(candidate);
        }
        return total;
    }

    public void clear() {
        requireOpen();
        sources.clear();
    }

    public boolean active() {
        return !closed && !ledger.closed && active;
    }

    private void requireOpen() {
        if (closed || ledger.closed) {
            throw new IllegalStateException("Prepared UI paint scope has closed");
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        if (root) {
            ledger.closed = true;
            for (GuiPaintBudget scope : ledger.scopes.keySet()) {
                scope.closed = true;
                scope.sources.clear();
            }
            ledger.scopes.clear();
        } else {
            List<GuiPaintBudget> retiring = new ArrayList<>();
            for (GuiPaintBudget scope : ledger.scopes.keySet()) {
                for (GuiPaintBudget ancestor = scope; ancestor != null; ancestor = ancestor.parent) {
                    if (ancestor == this) {
                        retiring.add(scope);
                        break;
                    }
                }
            }
            for (GuiPaintBudget scope : retiring) {
                scope.closed = true;
                scope.active = false;
                scope.sources.clear();
                ledger.scopes.remove(scope);
            }
        }
    }

    private static final class Ledger {
        final Map<GuiPaintBudget, Boolean> scopes = new IdentityHashMap<>();
        boolean closed;
    }

    private static final class Cost {
        long commands;
        long vertices;
        long hitVertices;
        long expressions;
        long text;
        long metadataCommands;
        long metadataVertices;
        long metadataExpressions;
        long metadataText;
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
            addDisplayed(cost);
            addMetadata(cost);
        }

        void addDisplayed(Cost cost) {
            commands += cost.commands;
            vertices += cost.vertices;
            hitVertices += cost.hitVertices;
            expressions += cost.expressions;
            text += cost.text;
        }

        void addMetadata(Cost cost) {
            metadataCommands += cost.metadataCommands;
            metadataVertices += cost.metadataVertices;
            metadataExpressions += cost.metadataExpressions;
            metadataText += cost.metadataText;
        }

    }
}

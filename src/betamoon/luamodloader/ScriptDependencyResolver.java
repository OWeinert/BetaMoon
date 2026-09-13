package betamoon.luamodloader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves script dependencies and records failures on affected scripts. */
final class ScriptDependencyResolver {
    Map<String, ScriptMod> indexByName(List<ScriptMod> mods, List<String> errors) {
        Map<String, ScriptMod> modsByName = new HashMap<>();
        for (int i = 0; i < mods.size(); i++) {
            ScriptMod mod = mods.get(i);
            if (modsByName.containsKey(mod.name)) {
                String message = "Duplicate Lua mod name";
                errors.add(message + ": " + mod.name);
                LuaScriptErrors.add(mod.name, message + ".");
                LuaScriptRegistry.markFailedByFile(mod.sourceFileName, message + ".");
                continue;
            }
            modsByName.put(mod.name, mod);
        }
        return modsByName;
    }

    List<ScriptMod> order(Map<String, ScriptMod> modsByName, List<String> errors) {
        List<ScriptMod> ordered = new ArrayList<>();
        Map<String, VisitState> states = new HashMap<>();
        Set<String> failed = new HashSet<>();
        for (String name : modsByName.keySet()) {
            if (!states.containsKey(name)) {
                visit(name, modsByName, states, ordered, new ArrayList<>(), errors, failed);
            }
        }
        return ordered;
    }

    void visit(String name, Map<String, ScriptMod> modsByName, Map<String, VisitState> states, List<ScriptMod> ordered,
            List<String> stack, List<String> errors, Set<String> failed) {
        VisitState state = states.get(name);
        if (state == VisitState.VISITING) {
            String message = "Circular dependency detected: " + formatCycle(stack, name);
            errors.add(message);
            LuaScriptErrors.add(name, message);
            LuaScriptRegistry.markFailedByName(name, message);
            markCycleFailed(stack, name, failed);
            return;
        }
        if (state == VisitState.VISITED) {
            return;
        }

        states.put(name, VisitState.VISITING);
        stack.add(name);
        ScriptMod mod = modsByName.get(name);
        if (mod == null) {
            stack.remove(stack.size() - 1);
            return;
        }

        List<String> missingDependencies = new ArrayList<>();
        List<String> failedDependencies = new ArrayList<>();
        for (int i = 0; i < mod.dependencies.size(); i++) {
            String dependency = mod.dependencies.get(i);
            if (!modsByName.containsKey(dependency)) {
                missingDependencies.add(dependency);
                continue;
            }

            visit(dependency, modsByName, states, ordered, stack, errors, failed);
            if (failed.contains(dependency)) {
                failedDependencies.add(dependency);
            }
        }

        if (!missingDependencies.isEmpty()) {
            mod.missingDependencies = missingDependencies;
            fail(name, "Missing dependencies for '" + name + "': " + formatNames(missingDependencies), errors, failed);
            finishVisit(name, stack, states);
            return;
        }
        if (!failedDependencies.isEmpty()) {
            fail(name, "Dependency failed to load for '" + name + "': " + formatNames(failedDependencies), errors,
                    failed);
            finishVisit(name, stack, states);
            return;
        }

        mod.missingDependencies = null;
        states.put(name, VisitState.VISITED);
        ordered.add(mod);
        stack.remove(stack.size() - 1);
    }

    String formatCycle(List<String> stack, String name) {
        int start = stack.indexOf(name);
        if (start == -1) {
            return name;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = start; i < stack.size(); i++) {
            if (builder.length() > 0) {
                builder.append(" -> ");
            }
            builder.append(stack.get(i));
        }
        builder.append(" -> ").append(name);
        return builder.toString();
    }

    private void finishVisit(String name, List<String> stack, Map<String, VisitState> states) {
        stack.remove(stack.size() - 1);
        states.put(name, VisitState.VISITED);
    }

    private void fail(String name, String message, List<String> errors, Set<String> failed) {
        errors.add(message);
        LuaScriptErrors.add(name, message);
        LuaScriptRegistry.markFailedByName(name, message);
        failed.add(name);
    }

    private void markCycleFailed(List<String> stack, String name, Set<String> failed) {
        int start = stack.indexOf(name);
        if (start == -1) {
            failed.add(name);
            return;
        }

        for (int i = start; i < stack.size(); i++) {
            String modName = stack.get(i);
            failed.add(modName);
            LuaScriptRegistry.markFailedByName(modName, "Circular dependency detected.");
        }
        failed.add(name);
    }

    private String formatNames(List<String> names) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append("'").append(names.get(i)).append("'");
        }
        return builder.toString();
    }

    enum VisitState {
        VISITING, VISITED
    }
}

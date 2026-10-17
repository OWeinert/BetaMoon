package betamoon.worldgen.creation;

import java.util.LinkedHashMap;
import java.util.Map;

/** Screen-local edits; copies make Done and Cancel transactional. */
public final class GenerationDraft {
    public final GenerationCatalog catalog;
    private final Map<String, Boolean> choices = new LinkedHashMap<String, Boolean>();

    public GenerationDraft(GenerationCatalog catalog) {
        this.catalog = catalog;
        reset();
    }

    public GenerationDraft copy() {
        GenerationDraft copy = new GenerationDraft(catalog);
        copy.choices.putAll(choices);
        return copy;
    }

    public void reset() {
        choices.clear();
        for (GenerationOption option : catalog.options.values()) {
            choices.put(option.key, option.baseline);
        }
    }

    public boolean selected(String key) {
        return Boolean.TRUE.equals(choices.get(key));
    }

    public boolean editable(GenerationOption option) {
        if (option.lockedReason != null) {
            return false;
        }
        for (String parent : option.parents) {
            if (!enabled(parent)) {
                return false;
            }
        }
        return true;
    }

    public boolean enabled(String key) {
        GenerationOption option = catalog.options.get(key);
        if (option == null || !selected(key)) {
            return false;
        }
        for (String parent : option.parents) {
            if (!enabled(parent)) {
                return false;
            }
        }
        return true;
    }

    public void toggle(String key) {
        GenerationOption option = catalog.options.get(key);
        if (option != null && editable(option)) {
            choices.put(key, !selected(key));
        }
    }

    public boolean modified() {
        for (GenerationOption option : catalog.options.values()) {
            if (selected(option.key) != option.baseline) {
                return true;
            }
        }
        return false;
    }

    Map<String, Boolean> choices() {
        return new LinkedHashMap<String, Boolean>(choices);
    }
}

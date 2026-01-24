package betamoon.config;

public final class ConfigField<TValue> {

    private String name;
    private TValue value;

    public ConfigField(String name, TValue value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return this.name;
    }

    public TValue getValue() {
        return this.value;
    }
}
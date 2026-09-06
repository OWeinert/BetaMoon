package betamoon.instrumentation.api;

/** Describes a cohesive set of bytecode hooks registered during agent startup. */
public interface HookModule {
    String getId();

    void register(HookRegistrar registrar);
}

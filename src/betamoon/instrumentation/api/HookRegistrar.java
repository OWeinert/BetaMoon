package betamoon.instrumentation.api;

/**
 * Registration boundary exposed to hook modules.
 *
 * The concrete registry owns lifecycle and indexing concerns; modules only
 * need the ability to contribute validated definitions.
 */
public interface HookRegistrar {
    void register(HookDefinition definition);
}

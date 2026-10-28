package betamoon.luaapi.interop;

import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.interfaces.NativeImplementation;
import betamoon.interop.reference.NativeTypeRef;
import java.util.Map;

/**
 * Retainable class-host identity/metadata, without native payloads, classes,
 * loaders or callbacks.
 */
final class NativeLuaHost extends NativeLuaImplementation {
    private final NativeTypeRef base;
    private final String fingerprint;

    NativeLuaHost(NativeAccessApi host, NativeImplementation implementation, NativeTypeRef base, String fingerprint) {
        super(host, implementation);
        this.base = base;
        this.fingerprint = fingerprint;
        properties.remove("interfaces");
        properties.put("base", new NativeLuaReference(host, base));

    }

    @Override
    Map<String, Object> describe() {
        return CatalogValues.record("base", base, "shape", fingerprint, "ownership", implementation.value.describe());
    }
}

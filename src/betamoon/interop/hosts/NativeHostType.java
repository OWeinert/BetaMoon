package betamoon.interop.hosts;

import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.reference.NativeMemberRef;
import betamoon.interop.reference.NativeTypeRef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reusable symbolic class shape. It holds no loader, live root, callback or
 * native instance.
 */
public final class NativeHostType {
    private final String key;
    public final NativeTypeRef base;
    public final NativeMemberRef constructor;
    public final List<NativeMemberRef> overrides;

    public NativeHostType(NativeTypeRef base, NativeMemberRef constructor, List<NativeMemberRef> overrides) {
        if (base == null || !base.descriptor.startsWith("L") || constructor == null
                || constructor.kind != NativeMemberRef.Kind.CONSTRUCTOR || overrides == null || overrides.isEmpty()
                || overrides.size() > 128) {
            throw new NativeAccessFailure("invalid_native_host_type",
                    "supply a class base, exact constructor and 1..128 overrides");
        }
        Set<String> keys = new HashSet<>();
        for (NativeMemberRef method : overrides) {
            if (method == null || method.kind != NativeMemberRef.Kind.METHOD || !keys.add(method.key())) {
                throw new NativeAccessFailure("invalid_native_host_type",
                        "overrides require distinct exact method references");
            }
        }
        this.base = base;
        this.constructor = constructor;
        this.overrides = Collections.unmodifiableList(new ArrayList<>(overrides));
        List<String> sorted = new ArrayList<>(keys);
        Collections.sort(sorted);
        key = base.key() + ":" + constructor.key() + ":" + String.join(";", sorted);
    }

    public String key() {
        return key;
    }
}

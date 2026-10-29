package betamoon.interop.binding;

/**
 * Prepared ordinary-syntax alternatives. Absence is data, not a failed exact
 * binding.
 */
public final class NativeOrdinaryMember {
    public final NativeMemberPlan field;
    public final boolean method;
    private final boolean ambiguousFields;

    NativeOrdinaryMember(NativeMemberPlan field, boolean method, boolean ambiguousFields) {
        this.field = field;
        this.method = method;
        this.ambiguousFields = ambiguousFields;
    }

    public NativeOrdinaryMember requireUnambiguousFields(String name) {
        if (ambiguousFields) {
            throw new NativeAccessFailure("ambiguous_member", name + "; use an exact declaring field selector");
        }
        return this;
    }
}

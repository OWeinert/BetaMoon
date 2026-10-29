package betamoon.luaapi.interop;

import betamoon.interop.reference.NativeMemberRef;
import betamoon.interop.reference.NativeMemberReference;
import betamoon.interop.reference.NativeReferenceSelectors;
import betamoon.interop.reference.NativeTypeRef;
import betamoon.interop.reference.NativeTypeReference;
import betamoon.interop.runtime.NativeContext;
import org.luaj.vm2.LuaValue;

final class NativeLuaReference extends NativeLuaHandle implements NativeMemberReference, NativeTypeReference {
    final NativeTypeRef typeRef;
    final NativeMemberRef memberRef;

    @Override
    public NativeMemberRef nativeMemberReference() {
        return memberRef;
    }

    @Override
    public NativeTypeRef nativeTypeReference() {
        return typeRef;
    }

    private final NativeAccessApi host;

    NativeLuaReference(NativeAccessApi host, NativeTypeRef type) {
        super("Native TypeRef(" + type.name() + ")");
        this.host = host;
        typeRef = type;
        memberRef = null;
    }

    NativeLuaReference(NativeAccessApi host, NativeMemberRef member) {
        super("Native " + member.kind + "Ref(" + member.name + member.descriptor + ")");
        this.host = host;
        typeRef = null;
        memberRef = member;
    }

    @Override
    protected LuaValue createProperty(String name) {
        NativeTypeRef type = typeRef;
        NativeMemberRef member = memberRef;
        if (type != null) {
            switch (name) {
                case "componentType":
                    return type.dimensions() > 0 ? host.reference(type.component()) : NIL;
                case "kind":
                    return valueOf("type");
                case "name":
                    return valueOf(type.name());
                case "descriptor":
                    return valueOf(type.descriptor);
                case "names":
                    return valueOf(type.names);
                case "loaderId":
                    return type.loaderId == null ? NIL : valueOf(type.loaderId);
                case "originId":
                    return type.originId == null ? NIL : valueOf(type.originId);
                case "dimensions":
                    return valueOf(type.dimensions());
                case "method":
                    return NativeLuaSupport.function(this,
                            args -> host.reference(host.selector(type, NativeMemberRef.Kind.METHOD, args)));
                case "field":
                    return NativeLuaSupport.function(this,
                            args -> host.reference(host.selector(type, NativeMemberRef.Kind.FIELD, args)));
                case "constructor":
                    return NativeLuaSupport.function(this,
                            args -> host.reference(new NativeMemberRef(NativeMemberRef.Kind.CONSTRUCTOR, type, null,
                                    "new", args.checkjstring(1), null, null)));
                case "describe":
                    return NativeLuaSupport.function(this, args -> host.describe(this, args.arg1()));
                default:
                    return NIL;
            }
        }
        if (name.equals("call") && member.kind != NativeMemberRef.Kind.METHOD
                || name.equals("new") && member.kind != NativeMemberRef.Kind.CONSTRUCTOR
                || name.equals("valueType") && member.kind != NativeMemberRef.Kind.FIELD
                || name.equals("returnType") && member.kind == NativeMemberRef.Kind.FIELD) {
            return NIL;
        }
        switch (name) {
            case "kind":
                return valueOf(member.kind.name());
            case "name":
                return valueOf(member.name);
            case "descriptor":
                return member.descriptor == null ? NIL : valueOf(member.descriptor);
            case "lookupType":
                return host.reference(member.lookupType);
            case "names":
                return valueOf(member.names);
            case "descriptorNames":
                return valueOf(member.descriptorNames);
            case "declaringType":
                return member.declaringType == null ? NIL : host.reference(member.declaringType);
            case "parameterTypes":
                return host.metadata(member.parameters(), true);
            case "valueType":
                return member.valueType() == null ? NIL : host.reference(member.valueType());
            case "returnType":
                return member.valueType() == null ? NIL : host.reference(member.valueType());
            case "describe":
                return NativeLuaSupport.function(this, args -> host.describe(this, args.arg1()));
            case "call":
                return NativeLuaSupport.function(this,
                        args -> new NativeLuaMethod(host, NativeContext.current(), false, member).callSelected(args));
            case "new":
                return NativeLuaSupport.function(this,
                        args -> new NativeLuaMethod(host, NativeContext.current(), false, member).construct(args));
            default:
                return NIL;
        }
    }

    @Override
    public boolean eq_b(LuaValue other) {
        return NativeReferenceSelectors.equal(this, other);
    }
}

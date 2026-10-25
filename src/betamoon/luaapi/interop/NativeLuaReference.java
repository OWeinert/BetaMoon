package betamoon.luaapi.interop;

import betamoon.interop.reference.NativeMemberRef;
import betamoon.interop.reference.NativeTypeRef;
import betamoon.interop.runtime.NativeContext;
import org.luaj.vm2.LuaValue;

final class NativeLuaReference extends NativeLuaHandle {
    final NativeTypeRef typeRef;
    final NativeMemberRef memberRef;

    NativeLuaReference(NativeAccessApi host, NativeTypeRef type) {
        super("Native TypeRef(" + type.name() + ")");
        typeRef = type;
        memberRef = null;
        properties.put("kind", valueOf("type"));
        properties.put("name", valueOf(type.name()));
        properties.put("descriptor", valueOf(type.descriptor));
        properties.put("names", valueOf(type.names));
        properties.put("loaderId", type.loaderId == null ? NIL : valueOf(type.loaderId));
        properties.put("originId", type.originId == null ? NIL : valueOf(type.originId));
        properties.put("dimensions", valueOf(type.dimensions()));
        if (type.dimensions() > 0) {
            properties.put("componentType", new NativeLuaReference(host, type.component()));
        }
        properties.put("method", NativeLuaSupport.function(this,
                args -> new NativeLuaReference(host, host.selector(type, NativeMemberRef.Kind.METHOD, args))));
        properties.put("field", NativeLuaSupport.function(this,
                args -> new NativeLuaReference(host, host.selector(type, NativeMemberRef.Kind.FIELD, args))));
        properties.put("constructor",
                NativeLuaSupport.function(this,
                        args -> new NativeLuaReference(host, new NativeMemberRef(NativeMemberRef.Kind.CONSTRUCTOR, type,
                                null, "new", args.checkjstring(1), null, null))));
        properties.put("describe", NativeLuaSupport.function(this, args -> host.describe(this, args.arg1())));
    }

    NativeLuaReference(NativeAccessApi host, NativeMemberRef member) {
        super("Native " + member.kind + "Ref(" + member.name + member.descriptor + ")");
        typeRef = null;
        memberRef = member;
        properties.put("kind", valueOf(member.kind.name()));
        properties.put("name", valueOf(member.name));
        properties.put("descriptor", member.descriptor == null ? NIL : valueOf(member.descriptor));
        properties.put("lookupType", new NativeLuaReference(host, member.lookupType));
        properties.put("names", valueOf(member.names));
        properties.put("descriptorNames", valueOf(member.descriptorNames));
        properties.put("declaringType",
                member.declaringType == null ? NIL : new NativeLuaReference(host, member.declaringType));
        properties.put("parameterTypes", host.metadata(member.parameters(), true));
        properties.put(member.kind == NativeMemberRef.Kind.FIELD ? "valueType" : "returnType",
                member.valueType() == null ? NIL : new NativeLuaReference(host, member.valueType()));
        properties.put("describe", NativeLuaSupport.function(this, args -> host.describe(this, args.arg1())));
        if (member.kind == NativeMemberRef.Kind.METHOD) {
            properties.put("call", NativeLuaSupport.function(this,
                    args -> new NativeLuaMethod(host, NativeContext.current(), false, member).callSelected(args)));
        } else if (member.kind == NativeMemberRef.Kind.CONSTRUCTOR) {
            properties.put("new", NativeLuaSupport.function(this,
                    args -> new NativeLuaMethod(host, NativeContext.current(), false, member).construct(args)));
        }
    }

    @Override
    public boolean eq_b(LuaValue other) {
        if (!(other instanceof NativeLuaReference)) {
            return false;
        }
        NativeLuaReference reference = (NativeLuaReference) other;
        return typeRef != null
                ? reference.typeRef != null && typeRef.key().equals(reference.typeRef.key())
                : reference.memberRef != null && memberRef.key().equals(reference.memberRef.key());
    }
}

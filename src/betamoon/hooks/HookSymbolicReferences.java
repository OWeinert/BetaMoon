package betamoon.hooks;

import betamoon.interop.reference.NativeMemberRef;
import betamoon.interop.reference.NativeMemberReference;
import betamoon.interop.reference.NativeReferenceSelectors;
import betamoon.interop.reference.NativeTypeRef;
import betamoon.interop.reference.NativeTypeReference;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/**
 * Pure selectors shared with normal native access, without discovery or
 * execution.
 */
public final class HookSymbolicReferences extends HookLuaValue {
    private LuaValue runtimeNative;

    void activate(LuaValue nativeApi) {
        runtimeNative = nativeApi;
    }

    @Override
    public LuaValue get(LuaValue key) {
        return runtimeNative == null ? super.get(key) : runtimeNative.get(key);
    }

    public HookSymbolicReferences() {
        properties.put("type", method(args -> {
            NativeTypeRef type = NativeReferenceSelectors.type(args.arg1(), args.arg(2));
            return new TypeReference(type);
        }));
    }

    public static NativeMemberRef select(NativeTypeRef type, NativeMemberRef.Kind kind, Varargs args) {
        return NativeReferenceSelectors.member(type, kind, args);
    }

    private final class TypeReference extends HookLuaValue implements NativeTypeReference {
        private final NativeTypeRef type;

        TypeReference(NativeTypeRef type) {
            this.type = type;
            properties.put("kind", valueOf("type"));
            properties.put("name", valueOf(type.name()));
            properties.put("descriptor", valueOf(type.descriptor));
            properties.put("names", valueOf(type.names));
            properties.put("loaderId", type.loaderId == null ? NIL : valueOf(type.loaderId));
            properties.put("originId", type.originId == null ? NIL : valueOf(type.originId));
            properties.put("dimensions", valueOf(type.dimensions()));
            if (type.dimensions() > 0) {
                properties.put("componentType", new TypeReference(type.component()));
            }
            properties.put("method",
                    method(args -> new MemberReference(select(type, NativeMemberRef.Kind.METHOD, args))));
            properties.put("field",
                    method(args -> new MemberReference(select(type, NativeMemberRef.Kind.FIELD, args))));
            properties.put("constructor",
                    method(args -> new MemberReference(new NativeMemberRef(NativeMemberRef.Kind.CONSTRUCTOR, type, null,
                            "new", HookDefinition.text(args.arg1()), null, null))));
            properties.put("describe", method(args -> {
                requireRuntime();
                return runtimeNative.get("describe")
                        .invoke(varargsOf(new LuaValue[]{runtimeNative, this, args.arg1()}));
            }));
        }

        @Override
        public NativeTypeRef nativeTypeReference() {
            return type;
        }

        @Override
        public boolean eq_b(LuaValue other) {
            return NativeReferenceSelectors.equal(this, other);
        }
    }

    private final class MemberReference extends HookLuaValue implements NativeMemberReference {
        private final NativeMemberRef member;

        MemberReference(NativeMemberRef member) {
            this.member = member;
            properties.put("kind", valueOf(member.kind.name()));
            properties.put("name", valueOf(member.name));
            properties.put("descriptor", member.descriptor == null ? NIL : valueOf(member.descriptor));
            properties.put("lookupType", new TypeReference(member.lookupType));
            properties.put("declaringType",
                    member.declaringType == null ? NIL : new TypeReference(member.declaringType));
            properties.put("names", valueOf(member.names));
            properties.put("descriptorNames", valueOf(member.descriptorNames));
            LuaTable parameters = new LuaTable();
            int index = 1;
            for (NativeTypeRef parameter : member.parameters()) {
                parameters.set(index++, new TypeReference(parameter));
            }
            properties.put("parameterTypes", parameters);
            properties.put(member.kind == NativeMemberRef.Kind.FIELD ? "valueType" : "returnType",
                    member.valueType() == null ? NIL : new TypeReference(member.valueType()));
            properties.put("describe", method(args -> {
                requireRuntime();
                return runtimeNative.get("describe")
                        .invoke(varargsOf(new LuaValue[]{runtimeNative, this, args.arg1()}));
            }));
            if (member.kind != NativeMemberRef.Kind.FIELD) {
                String operation = member.kind == NativeMemberRef.Kind.CONSTRUCTOR ? "new" : "call";
                properties.put(operation, method(args -> {
                    LuaValue ref = runtimeReference();
                    return ref.get(operation).invoke(varargsOf(ref, args));
                }));
            }
        }

        @Override
        public NativeMemberRef nativeMemberReference() {
            return member;
        }

        @Override
        public boolean eq_b(LuaValue other) {
            return NativeReferenceSelectors.equal(this, other);
        }

        private LuaValue runtimeReference() {
            requireRuntime();
            LuaValue type = runtimeType(member.lookupType);
            if (member.kind == NativeMemberRef.Kind.CONSTRUCTOR) {
                return type.get("constructor").call(type, valueOf(member.descriptor));
            }
            LuaTable policies = new LuaTable();
            policies.set("names", member.names);
            policies.set("descriptorNames", member.descriptorNames);
            if (member.declaringType != null) {
                policies.set("declaringType", runtimeType(member.declaringType));
            }
            return type.get(member.kind == NativeMemberRef.Kind.FIELD ? "field" : "method")
                    .invoke(varargsOf(new LuaValue[]{type, valueOf(member.name),
                            member.descriptor == null ? NIL : valueOf(member.descriptor), policies}))
                    .arg1();
        }
    }

    private LuaValue runtimeType(NativeTypeRef reference) {
        LuaTable options = new LuaTable();
        options.set("names", reference.names);
        if (reference.loaderId != null) {
            options.set("loaderId", reference.loaderId);
        }
        if (reference.originId != null) {
            options.set("originId", reference.originId);
        }
        return runtimeNative.get("type").call(runtimeNative, valueOf(reference.name()), options);
    }

    private void requireRuntime() {
        if (runtimeNative == null) {
            throw new HookFailure("forbidden_phase", "native execution/query is unavailable during hook declaration");
        }
    }
}

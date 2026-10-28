package betamoon.luaapi.interop;

import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.binding.NativeMemberPlan;
import betamoon.interop.binding.NativeOrdinaryMember;
import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.reference.NativeMemberRef;
import betamoon.interop.reference.NativeTypeRef;
import betamoon.interop.runtime.NativeContext;
import betamoon.interop.value.NativeConversions;
import java.lang.reflect.Array;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaValue;

/**
 * Class structure can be cached; an object payload lives only in its context's
 * cleared-on-close registry.
 */
final class NativeLuaView extends NativeLuaHandle {
    final boolean classView;
    final NativeContext context;
    private final NativeAccessApi host;
    private final boolean direct;
    private final NativeTypeRef lookup;
    private final WeakReference<Class<?>> typeReference;
    private final boolean array;
    private final boolean container;
    private final Integer identity;

    NativeLuaView(NativeAccessApi host, NativeContext context, boolean direct, Object object) {
        this(host, context, direct, context.bindings.reference(object.getClass()), object.getClass(),
                context.retainScoped(object));
    }

    NativeLuaView(NativeAccessApi host, NativeContext context, boolean direct, NativeTypeRef lookup, Class<?> type) {
        this(host, context, direct, lookup, type, null);
    }

    private NativeLuaView(NativeAccessApi host, NativeContext context, boolean direct, NativeTypeRef lookup,
            Class<?> type, Integer identity) {
        super(identity == null ? "Native class(" + type.getName() + ")" : "Native object(" + type.getName() + ")");
        this.host = host;
        this.context = context;
        this.direct = direct;
        this.lookup = lookup;
        if (identity == null) {
            context.retainMetadata(type);
        }
        typeReference = new WeakReference<>(type);
        array = type.isArray();
        container = array || List.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type);
        this.identity = identity;
        this.classView = identity == null;
        diagnosticType = lookup;
        diagnosticDirect = direct;
    }

    private boolean reserved(String name) {
        switch (name) {
            case "describe":
            case "sameObject":
            case "method":
            case "field":
                return true;
            case "constructor":
            case "new":
            case "enum":
                return classView;
            default:
                return !classView && container() && NativeLuaContainers.supports(name);
        }
    }

    private boolean container() {
        return container;
    }

    private Class<?> nativeType() {
        return typeReference.get();
    }

    @Override
    protected LuaValue createProperty(String name) {
        if (!classView && container() && NativeLuaContainers.supports(name)) {
            return NativeLuaContainers.create(this, host, direct, () -> payload(context), name);
        }
        switch (name) {
            case "describe":
                return NativeLuaSupport.scopedFunction(this, context, args -> {
                    NativeContext execution = executionContext();
                    return host.metadata(CatalogValues.record("type", context.bindings.reference(nativeType()),
                            "identity", classView ? "class" : context.identity(identity), "bound", true, "policy",
                            direct ? "direct" : "managed", "context", execution.describe()), true);
                });
            case "sameObject":
                return NativeLuaSupport.scopedFunction(this, context,
                        args -> LuaValue.valueOf(!classView && args.arg1() instanceof NativeLuaView
                                && !((NativeLuaView) args.arg1()).classView
                                && payload(context) == ((NativeLuaView) args.arg1()).payload(context)));
            case "method":
                return NativeLuaSupport.scopedFunction(this, context, args -> new NativeLuaMethod(host,
                        executionContext(), direct, host.selector(lookup, NativeMemberRef.Kind.METHOD, args)));
            case "field":
                return NativeLuaSupport.scopedFunction(this, context, args -> {
                    NativeContext execution = executionContext();
                    NativeMemberRef reference = host.selector(lookup, NativeMemberRef.Kind.FIELD, args);
                    Integer receiver = classView ? null : execution.retainScoped(payload(execution));
                    return new NativeLuaField(host, execution, direct,
                            execution.bindings.exact(reference, execution, direct), receiver, reference);
                });
            case "constructor":
                if (!classView) {
                    return NIL;
                }
                return NativeLuaSupport.scopedFunction(this, context,
                        args -> new NativeLuaMethod(host, context, direct,
                                new NativeMemberRef(NativeMemberRef.Kind.CONSTRUCTOR, lookup, null, "new",
                                        NativeAccessApi.text(args.arg1()), null, null)));
            case "new":
                if (!classView) {
                    return NIL;
                }
                return NativeLuaSupport.scopedFunction(this, context, args -> {
                    NativeContext execution = host.operationContext(context, args);
                    Object[] input = host.arguments(args, execution);
                    NativeMemberPlan constructor = execution.bindings.constructor(nativeType(), input, execution);
                    return host.result(constructor.callConverted(execution, direct, null,
                            execution.bindings.arguments(constructor, input, execution)), execution, direct);
                });
            case "enum":
                if (!classView) {
                    return NIL;
                }
                return NativeLuaSupport.scopedFunction(this, context, args -> {
                    context.require(NativeContext.Effect.INITIALIZE, direct);
                    if (!nativeType().isEnum()) {
                        throw new NativeAccessFailure("not_an_enum", nativeType().getName());
                    }
                    return host.result(enumValue(nativeType(), NativeAccessApi.text(args.arg1())), context, direct);
                });
            default:
                return NIL;
        }
    }

    Object payload(NativeContext caller) {
        caller.check();
        if (caller.bindings != context.bindings || classView) {
            throw new NativeAccessFailure("wrong_native_context", "expected an object in this native session");
        }
        return context.value(identity);
    }

    NativeContext executionContext() {
        context.check();
        return classView ? context : context.ownershipContext(identity);
    }

    @Override
    public LuaValue get(LuaValue key) {
        try {
            NativeContext execution = executionContext();
            LuaValue helper = super.get(key);
            if (!helper.isnil()) {
                return helper;
            }
            if (!classView && array && key.type() == TNUMBER) {
                Object array = payload(context);
                return host.result(Array.get(array, NativeAccessApi.integer(key, 0, Array.getLength(array) - 1)),
                        execution, direct);
            }
            String name = NativeAccessApi.text(key);
            NativeOrdinaryMember member = context.bindings.ordinary(lookup, name, classView, context);
            NativeMemberPlan field = member.field;
            boolean method = member.method;
            if (field != null && method) {
                throw new NativeAccessFailure("ambiguous_member", name + "; use field() or method()");
            }
            if (field != null) {
                return host.result(field.get(execution, direct, classView ? null : payload(execution)), execution,
                        direct);
            }
            if (method) {
                return NativeLuaSupport.scopedFunction(this, context, args -> {
                    try {
                        NativeContext operation = host.operationContext(executionContext(), args);
                        Object receiver = classView ? null : payload(operation);
                        Object[] input = host.arguments(args, operation);
                        NativeMemberPlan plan = operation.bindings.method(lookup, name, receiver, input, operation);
                        Object result = plan.callConverted(operation, direct, receiver,
                                operation.bindings.arguments(plan, input, operation));
                        return plan.valueType == Void.TYPE ? LuaValue.NONE : host.result(result, operation, direct);
                    } catch (NativeAccessFailure failure) {
                        throw explain(failure, key);
                    }
                });
            }
            throw new NativeAccessFailure("member_not_found", name + "; nonpublic access uses exact direct selectors");
        } catch (NativeAccessFailure | IllegalArgumentException exception) {
            if (exception instanceof NativeAccessFailure) {
                explain((NativeAccessFailure) exception, key);
            }
            throw NativeLuaSupport.error(exception);
        }
    }

    @Override
    public void set(LuaValue key, LuaValue value) {
        try {
            context.check();
            if (!classView && array && key.type() == TNUMBER) {
                context.require(NativeContext.Effect.WRITE, direct);
                Object array = payload(context);
                int index = NativeAccessApi.integer(key, 0, Array.getLength(array) - 1);
                Object input = host.argument(value, context);
                context.bindings.validateNull(input, nativeType().getComponentType(), context);
                Array.set(array, index, NativeConversions.convert(input, nativeType().getComponentType()));
                return;
            }
            String name = NativeAccessApi.text(key);
            if (reserved(name)) {
                throw new NativeAccessFailure("reserved_member_name", "select the native field through field()");
            }
            NativeOrdinaryMember member = context.bindings.ordinary(lookup, name, classView, context);
            NativeMemberPlan field = member.field;
            if (field == null || member.method) {
                throw new NativeAccessFailure("ambiguous_or_missing_field", name + "; use field()");
            }
            Object input = host.argument(value, context);
            context.bindings.validateNull(input, field.valueType, context);
            field.setConverted(context, direct, classView ? null : payload(context),
                    NativeConversions.convert(input, field.valueType));
        } catch (NativeAccessFailure | IllegalArgumentException exception) {
            if (exception instanceof NativeAccessFailure) {
                explain((NativeAccessFailure) exception, key);
            }
            throw NativeLuaSupport.error(exception);
        }
    }

    private NativeAccessFailure explain(NativeAccessFailure failure, LuaValue key) {
        context.bindings.explain(failure, lookup, null, null, context, direct);
        if (key.type() == TSTRING) {
            failure.explain(CatalogValues.record("requestedMember", key.tojstring()));
        }
        return failure;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> type, String name) {
        try {
            return Enum.valueOf((Class) type, name);
        } catch (IllegalArgumentException exception) {
            throw new NativeAccessFailure("enum_constant_not_found", type.getName() + "." + name, exception);
        }
    }
}

package betamoon.luaapi.interop;

import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.diagnostics.NativeDiagnosticGuidance;
import betamoon.interop.query.NativeDataPlan;
import betamoon.interop.query.NativeDataQueries.Request;
import betamoon.interop.runtime.NativeContext;
import betamoon.interop.value.NativeExactValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/**
 * Lua boundary for immutable plans and atomic publication of detached
 * one/many-query results.
 */
final class NativeLuaDataQueries {
    private NativeLuaDataQueries() {
    }

    static LuaValue function(NativeLuaAccess access, NativeAccessApi host, NativeContext context, boolean direct,
            boolean many) {
        return NativeLuaSupport.function(access, arguments -> sample(host, context, direct, arguments, many));
    }

    private static Varargs sample(NativeAccessApi host, NativeContext context, boolean direct, Varargs arguments,
            boolean many) {
        NativeDataPlan plan = null;
        try {
            context.check();
            plan = NativeLuaDataPlan.require(arguments.arg1());
            if (arguments.narg() > (many ? 2 : 3)) {
                throw new NativeAccessFailure("invalid_data_requests", "unexpected sample arguments");
            }
            List<Request> requests = new ArrayList<>();
            NativeContext execution = context;
            if (many) {
                LuaTable table = NativeLuaDataPlan.plain(arguments.arg(2));
                int length = NativeLuaAccess.sequence(table, NativeDataPlan.MAXIMUM_REQUESTS);
                for (int index = 1; index <= length; index++) {
                    try {
                        LuaTable request = NativeLuaDataPlan.plain(table.rawget(index), "receiver", "inputs");
                        execution = ownership(host, execution, request.rawget("receiver"), request.rawget("inputs"));
                        requests.add(request(host, execution, request.rawget("receiver"), request.rawget("inputs")));
                    } catch (NativeAccessFailure failure) {
                        throw failure.explain(CatalogValues.record("requestIndex", index, "dataPath", "$"));
                    }
                }
            } else {
                execution = ownership(host, execution, arguments.arg(2), arguments.arg(3));
                requests.add(request(host, execution, arguments.arg(2), arguments.arg(3)));
            }
            Object result = execution.bindings.dataQueries.execute(plan, requests, execution, direct, many);
            execution.check();
            LuaValue detached = detached(host, execution, result);
            execution.check();
            return detached;
        } catch (NativeAccessFailure failure) {
            if (context.bindings != null) {
                context.bindings.explain(failure,
                        plan == null || failure.diagnostic().containsKey("requestedType") ? null : plan.type, null,
                        null, context, direct);
            }
            if (failure.reason.equals("native_exception") || failure.reason.equals("native_linkage_failure")) {
                throw failure;
            }
            NativeContext.recordFailure(failure);
            Map<String, Object> detail = new LinkedHashMap<>(failure.diagnostic());
            detail.put("reason", failure.reason);
            detail.put("message", failure.getMessage());
            detail.put("guidance", NativeDiagnosticGuidance.forReason(failure.reason));
            return NativeAccessApi.absent(failure.reason, host.metadata(detail, false));
        }
    }

    private static NativeContext ownership(NativeAccessApi host, NativeContext context, LuaValue receiver,
            LuaValue inputs) {
        NativeContext result = host.operationContext(context, receiver);
        if (!inputs.isnil()) {
            LuaTable table = NativeLuaDataPlan.plain(inputs);
            for (LuaValue key : table.keys()) {
                NativeLuaDataPlan.name(key);
                result = host.operationContext(result, table.rawget(key));
            }
        }
        return result;
    }

    private static Request request(NativeAccessApi host, NativeContext context, LuaValue receiver, LuaValue inputs) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (!inputs.isnil()) {
            LuaTable table = NativeLuaDataPlan.plain(inputs);
            for (LuaValue key : table.keys()) {
                context.dataBudget.work(1);
                LuaValue value = table.rawget(key);
                int bytes = 32 + key.rawlen();
                if (value.type() == LuaValue.TSTRING) {
                    bytes += value.rawlen();
                } else if (value instanceof NativeLuaExact && ((NativeLuaExact) value).exact.value instanceof String) {
                    bytes += ((String) ((NativeLuaExact) value).exact.value).length() * 2;
                }
                context.dataBudget.inputs(bytes);
                values.put(NativeLuaDataPlan.name(key), host.argument(value, context));
            }
        }
        return new Request(host.receiver(receiver, context), values);
    }

    private static LuaValue detached(NativeAccessApi host, NativeContext context, Object value) {
        if (value instanceof NativeExactValue) {
            return ((NativeExactValue) value).value == null
                    ? NativeLuaExact.NULL
                    : new NativeLuaExact(host, (NativeExactValue) value);
        }
        if (value instanceof Map) {
            LuaTable result = new LuaTable();
            for (Map.Entry<String, Object> entry : CatalogValues.object(value).entrySet()) {
                result.rawset(entry.getKey(), detached(host, context, entry.getValue()));
            }
            return result;
        }
        if (value instanceof List) {
            LuaTable result = new LuaTable();
            int index = 1;
            for (Object element : (List<?>) value) {
                result.rawset(index++, detached(host, context, element));
            }
            return result;
        }
        LuaValue result = host.result(value, context, false);
        if (result instanceof NativeLuaView) {
            throw new NativeAccessFailure("native_data_projection_required", "result was not detached");
        }
        return result;
    }
}

package betamoon.interop.diagnostics;

import betamoon.interop.catalog.CatalogValues;
import java.util.Map;

/**
 * Stable detached next steps shared by native execution and hook diagnostics.
 */
public final class NativeDiagnosticGuidance {
    private NativeDiagnosticGuidance() {
    }

    public static Map<String, Object> forStatus(String status) {
        if ("applied".equals(status) || "active".equals(status) || "prepared".equals(status)
                || "target_pending_load".equals(status) || "namespace_absent".equals(status)) {
            return null;
        }
        return forReason(status);
    }

    public static Map<String, Object> forReason(String reason) {
        String code;
        String message;
        switch (reason == null ? "" : reason) {
            case "hook_continuation_used":
                code = "continue_once";
                message = "Continue at most once during the active around callback; reacquire native views after continuing.";
                break;
            case "hook_recursion":
                code = "check_hook_reentry";
                message = "Check reentry policy and nested wrapper count; methods and active chains support at most 16 around callbacks.";
                break;
            case "hook_invalid_value":
                code = "check_hook_signature";
                message = "Use the exact argument/return descriptor; around callbacks return one typed result, or no value for void.";
                break;
            case "native_error_recovered":
                code = "review_declared_recovery";
                message = "A declared nativeErrors=recover policy replaced this native failure with a validated fallback.";
                break;
            case "around_secondary_failure":
                code = "inspect_secondary_failure";
                message = "The original native error remains primary; inspect this copied secondary wrapper failure.";
                break;
            case "native_client_action_denied":
                code = "use_authorized_input";
                message = "Submit the owning host's prepared command during its current authorized input callback.";
                break;
            case "native_client_limit":
                code = "reduce_client_resources";
                message = "Reduce prepared resources or queued commands and release unused host resources.";
                break;
            case "native_client_context_unavailable":
            case "native_context_unavailable":
            case "native_phase_denied":
            case "native_preparation_denied":
            case "preparation_required":
            case "forbidden_phase":
                code = "use_allowed_entry";
                message = "Prepare exact bindings in an allowed update or preparation entry; read-only callers cannot gain effects.";
                break;
            case "direct_required":
            case "private_access_denied":
            case "nonpublic_requires_direct":
                code = "select_direct_access";
                message = "Select direct() explicitly in an entry that permits the operation; enclosing restrictions still apply.";
                break;
            case "wrong_receiver":
            case "receiver_type_mismatch":
            case "static_receiver_must_be_nil":
            case "wrong_declaring_owner":
                code = "check_receiver";
                message = "Use an instance assignable to the declaring type; static members use nil.";
                break;
            case "source_unavailable":
            case "source_mismatch":
                code = "attach_matching_source";
                message = "Attach source matching this instance and origin; metadata cannot supply missing source bodies.";
                break;
            case "wrong_owner":
            case "wrong_native_session":
            case "stale_owner":
            case "stale_scope":
            case "stale_world":
            case "stale_player":
            case "native_scope_expired":
            case "expired_scope":
            case "inactive_scope":
            case "stale_environment":
            case "hook_call_expired":
            case "native_client_resource_closed":
            case "owned_value_closed":
            case "native_implementation_closed":
            case "native_session_closed":
            case "owner_not_ready":
                code = "acquire_current_scope";
                message = "Acquire fresh views in a current callback; retain copied data or permitted owned handles.";
                break;
            case "agent_unavailable":
            case "dispatcher_not_visible":
            case "mapping_mismatch":
            case "mapping_conflict":
                code = "check_agent_installation";
                message = "Check that the agent and mod use matching artifacts/mappings, then restart the instance.";
                break;
            case "ambiguous_loader":
            case "ambiguous_origin":
                code = "select_exact_origin";
                message = "Inspect loader/origin candidates and select the intended installed definition explicitly.";
                break;
            case "match_failed":
            case "transformation_failed":
            case "restart_required":
            case "target_loaded_too_early":
                code = "inspect_hook_site";
                message = "Inspect the exact hook target, observed matches and installed capability; changed startup sites require restart.";
                break;
            case "invalid_native_attachment":
            case "native_adapter_unavailable":
            case "invalid_native_implementation":
                code = "inspect_native_contract";
                message = "Check the exact interface, adapter and ownership contract required by this native consumer.";
                break;
            case "access_denied":
                code = "check_entry_permissions";
                message = "Check member visibility and current entry permissions; direct() preserves enclosing restrictions.";
                break;
            case "native_data_work_limit":
            case "native_data_byte_limit":
            case "native_data_input_limit":
            case "native_data_depth_limit":
                code = "reduce_data_query";
                message = "Reduce the selection, slice, input size or batch; nested and repeated queries share entry limits.";
                break;
            case "native_data_projection_required":
            case "native_data_cycle":
            case "invalid_data_projection":
                code = "check_data_projection";
                message = "Select native object fields explicitly and keep traversed shapes acyclic and bounded.";
                break;
            case "native_data_collection_changed":
                code = "retry_stable_selection";
                message = "Sample when the collection is stable; a query does not freeze native state or roll back calls.";
                break;
            case "unsupported_thread":
            case "wrong_thread":
                code = "use_client_entry";
                message = "Use an audited client-thread entry; synchronous native callbacks cannot be moved to a worker.";
                break;
            case "native_client_resource_unavailable":
            case "native_client_preparation_failed":
            case "native_client_environment_failed":
            case "native_client_disposal_failed":
            case "native_client_action_failed":
            case "native_exception":
            case "callback_failed":
            case "callback_disabled":
            case "native_callback_failed":
            case "native_linkage_failure":
                code = "inspect_cause";
                message = "Inspect the copied cause evidence and exact selector; completed native effects are not rolled back.";
                break;
            default:
                code = "inspect_selector";
                message = "Inspect the exact selector, namespace, origin and current entry permissions in this diagnostic.";
                break;
        }
        return CatalogValues.record("code", code, "message", message);
    }
}

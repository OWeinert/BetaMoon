package betamoon.interop.binding;

import betamoon.interop.catalog.CatalogJson;
import betamoon.interop.catalog.CatalogValues;
import betamoon.interop.diagnostics.NativeDiagnosticEvidenceTest;
import betamoon.interop.diagnostics.NativeDiagnosticEvidenceTest.HostileFailure;
import betamoon.interop.diagnostics.NativeDiagnosticGuidance;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class NativeFailureJournalTest {
    private NativeFailureJournalTest() {
    }

    public static void main(String[] arguments) {
        NativeDiagnosticEvidenceTest.run();
        NativeFailureJournal journal = new NativeFailureJournal();
        HostileFailure.calls = 0;
        HostileFailure cause = new HostileFailure("original", null);
        NativeAccessFailure failure = new NativeAccessFailure("native_exception", "call failed", cause);
        for (int index = 0; index < 100; index++) {
            journal.record(failure);
        }
        Map<String, Object> snapshot = journal.snapshot();
        List<?> records = (List<?>) snapshot.get("records");
        require(records.size() == 1, "duplicate records aggregated");
        Map<?, ?> record = (Map<?, ?>) records.get(0);
        require(((Number) record.get("count")).longValue() == 100, "duplicate count");
        require(record.get("nativeCauseType").equals(HostileFailure.class.getName()), "legacy cause type");
        require(record.get("nativeCauses") instanceof Map, "copied cause chain");
        require(failure.getCause() == cause, "execution retains original native exception identity");
        require(HostileFailure.calls == 0, "journal invokes no foreign exception getters");
        detached(snapshot);

        java.lang.management.ThreadMXBean bean = java.lang.management.ManagementFactory.getThreadMXBean();
        if (bean instanceof com.sun.management.ThreadMXBean) {
            com.sun.management.ThreadMXBean allocation = (com.sun.management.ThreadMXBean) bean;
            if (allocation.isThreadAllocatedMemorySupported()) {
                allocation.setThreadAllocatedMemoryEnabled(true);
                for (int index = 0; index < 10000; index++) {
                    journal.record(failure);
                }
                long before = allocation.getThreadAllocatedBytes(Thread.currentThread().getId());
                for (int index = 0; index < 10000; index++) {
                    journal.record(failure);
                }
                long bytes = allocation.getThreadAllocatedBytes(Thread.currentThread().getId()) - before;
                require(bytes <= 256, "repeat evidence must avoid per-occurrence allocation: " + bytes);
            }
        }
        NativeFailureJournal grouped = new NativeFailureJournal();
        for (int index = 0; index < 1000; index++) {
            grouped.record(
                    new NativeAccessFailure("native_exception", "same call", new HostileFailure("cause " + index, null))
                            .explain(CatalogValues.record("requestedType", "fixture.Type", "requestedMember", "call")));
        }
        Map<?, ?> groupedRecord = (Map<?, ?>) ((List<?>) grouped.snapshot().get("records")).get(0);
        require(((Number) groupedRecord.get("count")).longValue() == 1000,
                "fresh failures grouped before cause capture");
        require(HostileFailure.calls == 0, "failure storms invoke no foreign formatters");
        NativeFailureJournal bounded = new NativeFailureJournal();
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < 2000; index++) {
            text.append('\u20AC');
        }
        Throwable chain = null;
        for (int index = 0; index < 8; index++) {
            chain = new RuntimeException(text.toString(), chain);
        }
        for (int index = 0; index < 100; index++) {
            bounded.record(new NativeAccessFailure("native_exception", "call " + index, chain));
        }
        Map<String, Object> budget = bounded.snapshot();
        require(((Number) budget.get("omitted")).longValue() > 0, "aggregate journal limit exercised");
        require(((List<?>) budget.get("records")).size() < 64, "byte budget applies before count budget");
        require(CatalogJson.encode(budget)
                .getBytes(StandardCharsets.UTF_8).length <= ((Number) budget.get("maximumBytes")).intValue(),
                "whole encoded journal bounded");

        NativeFailureJournal oversized = new NativeFailureJournal();
        char[] chars = new char[70000];
        Arrays.fill(chars, 'x');
        oversized.record(new NativeAccessFailure("wrong_receiver", "fixture")
                .explain(CatalogValues.record("requestedType", new String(chars))));
        require(((List<?>) oversized.snapshot().get("records")).isEmpty(), "oversized record rejected");
        require(((Number) oversized.snapshot().get("omitted")).longValue() == 1, "oversized omission counted");
        require("select_direct_access"
                .equals(NativeDiagnosticGuidance.forReason("nonpublic_requires_direct").get("code")),
                "actual private-access guidance");
        require("acquire_current_scope".equals(NativeDiagnosticGuidance.forReason("expired_scope").get("code")),
                "actual expired-scope guidance");
        require("inspect_native_contract"
                .equals(NativeDiagnosticGuidance.forReason("invalid_native_attachment").get("code")),
                "attachment guidance does not suggest hook-site changes");
        require(NativeDiagnosticGuidance.forStatus("applied") == null, "successful sites are not failures");
        System.out.println("Native diagnostic cause, guidance and journal contracts passed.");
    }

    private static void detached(Object value) {
        if (value instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                require(entry.getKey() instanceof String, "detached string key");
                detached(entry.getValue());
            }
        } else if (value instanceof List) {
            for (Object entry : (List<?>) value) {
                detached(entry);
            }
        } else {
            require(value == null || value instanceof String || value instanceof Number || value instanceof Boolean,
                    "no retained native diagnostic payload");
        }
    }

    private static void require(boolean value, String detail) {
        if (!value) {
            throw new AssertionError(detail);
        }
    }
}

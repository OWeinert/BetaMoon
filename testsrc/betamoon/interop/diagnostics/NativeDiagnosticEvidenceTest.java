package betamoon.interop.diagnostics;

import java.lang.reflect.ReflectPermission;
import java.nio.charset.StandardCharsets;
import java.security.Permission;
import java.util.List;
import java.util.Map;

public final class NativeDiagnosticEvidenceTest {
    private NativeDiagnosticEvidenceTest() {
    }

    public static void run() {
        HostileFailure.calls = 0;
        HostileFailure cause = new HostileFailure("native detail", new IllegalStateException("inner detail"));
        Map<String, Object> evidence = NativeDiagnosticEvidence.capture(cause);
        List<?> chain = (List<?>) evidence.get("chain");
        require(chain.size() == 2, "base-field cause chain");
        require("native detail".equals(((Map<?, ?>) chain.get(0)).get("message")), "stored native message");
        require("native detail".equals(NativeDiagnosticEvidence.message(cause)), "safe summary");
        require(HostileFailure.calls == 0, "foreign exception methods were invoked");
        require(!Boolean.TRUE.equals(evidence.get("unavailable")), "Java 8 cause field reader available");

        RuntimeException first = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second");
        first.initCause(second);
        second.initCause(first);
        require(Boolean.TRUE.equals(NativeDiagnosticEvidence.capture(first).get("cycle")), "cause cycle reported");
        require(!Boolean.TRUE.equals(NativeDiagnosticEvidence.capture(new RuntimeException()).get("cycle")),
                "unset cause sentinel is not a cycle");
        Throwable longChain = null;
        for (int index = 0; index < 20; index++) {
            longChain = new RuntimeException("cause " + index, longChain);
        }
        Map<String, Object> limited = NativeDiagnosticEvidence.capture(longChain);
        require(((List<?>) limited.get("chain")).size() == 8 && Boolean.TRUE.equals(limited.get("truncated")),
                "cause count limit");

        String emoji = new String(Character.toChars(0x1F642));
        require(NativeDiagnosticEvidence.bounded(emoji, 3).isEmpty(), "never split an astral code point");
        require(emoji.equals(NativeDiagnosticEvidence.bounded(emoji, 4)), "four-byte code point retained");
        require("\uFFFDa\uFFFD".equals(NativeDiagnosticEvidence.bounded("\uD800a\uDC00", 20)),
                "malformed UTF-16 normalized");
        StringBuilder large = new StringBuilder();
        for (int index = 0; index < 2000; index++) {
            large.append(emoji);
        }
        Map<?, ?> message = (Map<?, ?>) ((List<?>) NativeDiagnosticEvidence
                .capture(new RuntimeException(large.toString())).get("chain")).get(0);
        require(((String) message.get("message")).getBytes(StandardCharsets.UTF_8).length <= 2048,
                "UTF-8 message byte bound");
        require(Boolean.TRUE.equals(message.get("messageTruncated")), "message truncation marked");

        SecurityManager previous = System.getSecurityManager();
        NativeDiagnosticEvidence.Reader denied;
        try {
            System.setSecurityManager(new SecurityManager() {
                @Override
                public void checkPermission(Permission permission) {
                    if (permission instanceof ReflectPermission
                            && permission.getName().equals("suppressAccessChecks")) {
                        throw new SecurityException("fixture refuses private field access");
                    }
                }
            });
            denied = new NativeDiagnosticEvidence.Reader();
        } finally {
            System.setSecurityManager(previous);
        }
        Map<String, Object> typeOnly = denied.capture(cause);
        require(Boolean.TRUE.equals(typeOnly.get("unavailable")), "denied field access is explicit");
        Map<?, ?> row = (Map<?, ?>) ((List<?>) typeOnly.get("chain")).get(0);
        require(row.get("type").equals(HostileFailure.class.getName()) && !row.containsKey("message"),
                "denial yields type-only evidence");
        require(HostileFailure.calls == 0, "fallback never invokes an exception method");
    }

    public static final class HostileFailure extends RuntimeException {
        public static int calls;

        public HostileFailure(String message, Throwable cause) {
            super(message, cause);
        }

        @Override
        public String getMessage() {
            calls++;
            throw new AssertionError("foreign getMessage invoked");
        }

        @Override
        public synchronized Throwable getCause() {
            calls++;
            throw new AssertionError("foreign getCause invoked");
        }

        @Override
        public String getLocalizedMessage() {
            calls++;
            throw new AssertionError("foreign getLocalizedMessage invoked");
        }

        @Override
        public String toString() {
            calls++;
            throw new AssertionError("foreign toString invoked");
        }
    }

    private static void require(boolean value, String detail) {
        if (!value) {
            throw new AssertionError(detail);
        }
    }
}

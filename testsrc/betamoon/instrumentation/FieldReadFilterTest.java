package betamoon.instrumentation;

import betamoon.instrumentation.api.AroundHookDefinition;
import betamoon.instrumentation.api.ClassRef;
import betamoon.instrumentation.api.FieldReadRedirectHookDefinition;
import betamoon.instrumentation.api.FieldRef;
import betamoon.instrumentation.api.HandlerRef;
import betamoon.instrumentation.api.MethodRef;
import betamoon.instrumentation.api.ValueBinding;
import betamoon.instrumentation.diagnostics.HookStatus;
import betamoon.instrumentation.diagnostics.TransformationReport;
import betamoon.instrumentation.mapping.RuntimeNamespace;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.instrumentation.registry.HookRegistry;
import betamoon.instrumentation.transform.BetaMoonTransformer;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

/**
 * Real JVM execution verifies read filters never replace or mutate a native
 * backing field.
 */
public final class FieldReadFilterTest {
    private static boolean hide;
    private static int filtered;

    public static void run(TinyMappingResolver mappings) throws Exception {
        for (RuntimeNamespace namespace : new RuntimeNamespace[]{RuntimeNamespace.NAMED, RuntimeNamespace.CLIENT}) {
            verify(mappings, namespace, 2);
            verify(mappings, namespace, 3);
        }
    }

    public static List<?> filter(List<?> original) {
        filtered++;
        return hide ? Collections.emptyList() : original;
    }

    public static int begin() {
        return 0;
    }

    public static int result(int value) {
        return value;
    }

    private static void verify(TinyMappingResolver mappings, RuntimeNamespace namespace, int expectedReads)
            throws Exception {
        String name = "betamoon/instrumentation/FieldReadFilterTest$Source";
        ClassRef type = new ClassRef(name);
        String callbacks = "betamoon/instrumentation/FieldReadFilterTest";
        TransformationReport report = new TransformationReport();
        HookRegistry registry = new HookRegistry(report);
        registry.register(new FieldReadRedirectHookDefinition("test:filter",
                new MethodRef(type, "renderedCount", "()I"), new FieldRef(type, "messages", "Ljava/util/List;"),
                HandlerRef.of(callbacks, "filter", "(Ljava/util/List;)Ljava/util/List;")).filterReads(expectedReads));
        registry.register(AroundHookDefinition.builder("test:other", new MethodRef(type, "storedCount", "()I"))
                .capture(HandlerRef.of(callbacks, "begin", "()I"))
                .onReturn(HandlerRef.of(callbacks, "result", "(I)I"), ValueBinding.returnValue()).build());
        BetaMoonTransformer transformer = new BetaMoonTransformer(registry.freeze(mappings, namespace), mappings,
                report, true, false);
        byte[] original;
        try (InputStream input = FieldReadFilterTest.class.getResourceAsStream("/" + name + ".class")) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            original = output.toByteArray();
        }
        byte[] transformed = transformer.transform(null, name, null, null, original);
        require(transformed != null, "Unrelated hooks disappeared when the optional field shape was unsupported");
        require(report.snapshot().stream()
                .anyMatch(value -> value.getHookId().equals("test:filter")
                        && value.getStatus() == (expectedReads == 2 ? HookStatus.APPLIED : HookStatus.NO_MATCH)),
                "Field read shape did not publish its exact result");
        require(transformer.transform(null, name, null, null, transformed) == null,
                "Field filters or unrelated hooks were not idempotent");
        class Loader extends ClassLoader {
            Class<?> fixture() {
                return defineClass(name.replace('/', '.'), transformed, 0, transformed.length);
            }
        }
        Class<?> fixture = new Loader().fixture();
        Object instance = fixture.newInstance();
        hide = false;
        filtered = 0;
        require(fixture.getMethod("renderedCount").invoke(instance).equals(1), "Unclaimed field value changed");
        require(filtered == (expectedReads == 2 ? 2 : 0), "Filter did not execute once per selected native read");
        hide = true;
        try {
            require(fixture.getMethod("renderedCount").invoke(instance).equals(expectedReads == 2 ? 0 : 1),
                    "Rendering did not receive the filtered value, or unsupported shape changed");
            require(fixture.getMethod("storedCount").invoke(instance).equals(1),
                    "Rendering suppression mutated the backing native list");
        } finally {
            hide = false;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static final class Source {
        private final List<String> messages = Collections.singletonList("native message");

        public int renderedCount() {
            return messages.isEmpty() ? 0 : messages.size();
        }

        public int storedCount() {
            return messages.size();
        }
    }
}

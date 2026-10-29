package betamoon.hooks;

import betamoon.instrumentation.mapping.RuntimeNamespace;
import betamoon.instrumentation.mapping.TinyMappingResolver;
import betamoon.interop.reference.NativeMemberRef;
import betamoon.interop.reference.NativeTypeRef;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Distinguishes class, member and descriptor name domains without loading
 * mapped types.
 */
final class HookMappingTest {
    private static int checks;

    static int verify() throws Exception {
        String tiny = "tiny\t2\t0\tnamed\tclient\tserver\n" + "c\texample/Target\tt\ts\n"
                + "\tm\t(Lexample/Argument;)I\tread\tm\tn\n" + "\tm\t()I\tserverOnly\t\tx\n"
                + "c\texample/Argument\ta\tb\n";
        TinyMappingResolver mappings = TinyMappingResolver
                .readStrict(new ByteArrayInputStream(tiny.getBytes(StandardCharsets.UTF_8)));
        NativeTypeRef named = NativeTypeRef.named("example.Target", "mapped", null, null);
        NativeTypeRef runtime = NativeTypeRef.named("t", "runtime", null, null);
        check(target(mappings, new NativeMemberRef(NativeMemberRef.Kind.METHOD, named, null, "read",
                "(Lexample/Argument;)I", "mapped", "mapped")).equals("t.m(La;)I"), "mapped class/member/descriptor");
        check(target(mappings,
                new NativeMemberRef(NativeMemberRef.Kind.METHOD, named, null, "read", "(La;)I", "mapped", "runtime"))
                .equals("t.m(La;)I"), "mapped member with runtime descriptor");
        check(target(mappings,
                new NativeMemberRef(NativeMemberRef.Kind.METHOD, runtime, named, "read", "(La;)I", "mapped", "runtime"))
                .equals("t.m(La;)I"), "runtime lookup with equivalent mapped declaring owner");
        check(target(mappings,
                new NativeMemberRef(NativeMemberRef.Kind.METHOD, runtime, null, "m", "(La;)I", "runtime", "runtime"))
                .equals("t.m(La;)I"), "literal runtime selector");
        NativeMemberRef absent = new NativeMemberRef(NativeMemberRef.Kind.METHOD, named, null, "serverOnly", "()I",
                "mapped", "mapped");
        check(HookTarget.resolve(absent, mappings, mappings.snapshot(), RuntimeNamespace.CLIENT).isEmpty(),
                "blank client mapping cell does not become a mapped literal");
        NativeMemberRef wrongOwner = new NativeMemberRef(NativeMemberRef.Kind.METHOD, named,
                NativeTypeRef.named("example.Argument", "mapped", null, null), "read", "(Lexample/Argument;)I",
                "mapped", "mapped");
        try {
            HookTarget.resolve(wrongOwner, mappings);
            throw new AssertionError("unrelated declaring owner accepted");
        } catch (HookFailure expected) {
            check(expected.reason.equals("match_failed"), "unrelated declaring owner rejected");
        }
        String inherited = "tiny\t2\t0\tnamed\tclient\tserver\n"
                + "c\texample/Parent\tfixture/hooks/MappingParent\tfixture/hooks/MappingParent\n"
                + "\tm\t()I\tdamage\ta\ta\n\tm\t()I\tprotectedValue\tq\tq\n"
                + "\tm\t()I\tprivateValue\tp\tp\n\tm\t()I\tstaticValue\ts\ts\n"
                + "c\texample/Child\tfixture/hooks/MappingChild\tfixture/hooks/MappingChild\n";
        TinyMappingResolver inheritance = TinyMappingResolver
                .readStrict(new ByteArrayInputStream(inherited.getBytes(StandardCharsets.UTF_8)));
        NativeTypeRef child = NativeTypeRef.named("example.Child", "mapped", null, null);
        for (String[] method : new String[][]{{"damage", "a"}, {"protectedValue", "q"}}) {
            NativeMemberRef ref = new NativeMemberRef(NativeMemberRef.Kind.METHOD, child, null, method[0], "()I",
                    "mapped", "mapped");
            List<HookTarget> resolved = HookTarget.resolve(ref, inheritance, inheritance.snapshot(),
                    RuntimeNamespace.CLIENT);
            check(resolved.size() == 1
                    && resolved.get(0).key().equals("fixture/hooks/MappingChild." + method[1] + "()I"),
                    "virtual ancestor mapping names the declared override");
            check(resolved.get(0).mappingOwner.equals("fixture/hooks/MappingParent"),
                    "ancestor mapping proof retained");
        }
        for (String method : new String[]{"privateValue", "staticValue"}) {
            NativeMemberRef ref = new NativeMemberRef(NativeMemberRef.Kind.METHOD, child, null, method, "()I", "mapped",
                    "mapped");
            check(HookTarget.resolve(ref, inheritance, inheritance.snapshot(), RuntimeNamespace.CLIENT).isEmpty(),
                    "private/static methods do not inherit mapping names");
        }
        check(System.getProperty("betamoon.mappingParent.initialized") == null,
                "ancestor bytes do not initialize classes");
        return checks;
    }

    private static String target(TinyMappingResolver mappings, NativeMemberRef member) {
        List<HookTarget> targets = HookTarget.resolve(member, mappings, mappings.snapshot(), RuntimeNamespace.CLIENT);
        check(targets.size() == 1, "one client selector");
        return targets.get(0).key();
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

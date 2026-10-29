package betamoon.hooks;

import java.util.Arrays;
import java.util.List;

/**
 * Copy-on-edit must preserve failed-edit isolation and readonly transport
 * across loader callbacks.
 */
final class HookBufferContractTest {
    static int verify() {
        String declaration = "local r=betamoon.hooks:createRegistry(); r:register({id='buffer',"
                + "target={class='fixture.hooks.HookTargetFixture',method='longValue',descriptor='(J)J'},"
                + "at='before',effects='%s'},function() end)";
        HookDefinition observe = HookDeclarations.evaluate("buffers", "buffers.lua",
                String.format(declaration, "observe"), 1, null, new HookDeclarations.Budget()).get(0).definition;
        HookDefinition edit = HookDeclarations.evaluate("buffers", "buffers.lua", String.format(declaration, "edit"), 1,
                null, new HookDeclarations.Budget()).get(0).definition;
        Object[] original = {17L};
        try (HookInvocation call = new HookInvocation(observe, "fixture/Target.value(J)J", HookBufferContractTest.class,
                null, original, null, null)) {
            List<?> transport = call.transportArguments();
            try {
                ((List<Object>) transport).set(0, 99L);
                throw new AssertionError("readonly bridge arguments were mutable");
            } catch (UnsupportedOperationException expected) {
            }
            Object[] copy = call.argumentValues();
            copy[0] = 99L;
            if (!call.argument(0).equals(17L) || call.committedArguments() != original) {
                throw new AssertionError("observer copied needlessly or exposed a mutable snapshot");
            }
            try {
                call.acceptTransport(Arrays.asList(99L), null, false);
                throw new AssertionError("readonly transported mutation was accepted");
            } catch (HookFailure expected) {
                if (!expected.reason.equals("hook_edit_denied")) {
                    throw expected;
                }
            }
        }
        if (!original[0].equals(17L)) {
            throw new AssertionError("observer close cleared borrowed native arguments");
        }
        try (HookInvocation failed = new HookInvocation(edit, "fixture/Target.value(J)J", HookBufferContractTest.class,
                null, original, null, null)) {
            failed.argument(0, 99L);
            if (!original[0].equals(17L)) {
                throw new AssertionError("staged edit changed native input before commit");
            }
        }
        List<?> committed;
        try (HookInvocation successful = new HookInvocation(edit, "fixture/Target.value(J)J",
                HookBufferContractTest.class, null, original, null, null)) {
            successful.argument(0, 33L);
            successful.validate();
            committed = successful.transportArguments();
        }
        try (HookInvocation receiver = new HookInvocation(edit, "fixture/Target.value(J)J",
                HookBufferContractTest.class, null, original, null, null)) {
            receiver.acceptTransport(committed, null, false);
            receiver.validate();
            Object[] result = receiver.committedArguments();
            receiver.close();
            if (!result[0].equals(33L) || !original[0].equals(17L)) {
                throw new AssertionError("close destroyed a transferred commit or native source");
            }
        }
        return 6;
    }
}

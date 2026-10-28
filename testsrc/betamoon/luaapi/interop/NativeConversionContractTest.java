package betamoon.luaapi.interop;

import betamoon.interop.binding.NativeAccessFailure;
import betamoon.interop.reference.NativeTypeRef;
import betamoon.interop.value.NativeConversions;
import betamoon.interop.value.NativeExactValue;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Cross-product parity and strict encoding boundaries; no trial conversion on
 * an overload miss.
 */
final class NativeConversionContractTest {
    static void run() {
        NativeTypeRef longType = NativeTypeRef.named("long", null, null, null);
        NativeTypeRef doubleType = NativeTypeRef.named("double", null, null, null);
        Object[] values = {null, NativeExactValue.NULL, true, false, "", "a", "ab", '\uD800', Byte.MIN_VALUE,
                Short.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE, 0D, -0D, 1.5D,
                127D, 128D, 32767D, 32768D, 2147483647D, 2147483648D, 9007199254740991D, 9007199254740992D, Double.NaN,
                Double.POSITIVE_INFINITY, Double.MAX_VALUE, Float.MAX_VALUE,
                new NativeExactValue(longType, Long.MIN_VALUE), new NativeExactValue(longType, Long.MAX_VALUE),
                new NativeExactValue(doubleType, Double.NaN), new NativeExactValue(doubleType, -0D),
                new NativeExactValue(NativeTypeRef.named("java.lang.String", null, null, null), null), new int[]{1},
                new Object()};
        Class<?>[] targets = {boolean.class, byte.class, short.class, int.class, long.class, char.class, float.class,
                double.class, Boolean.class, Byte.class, Short.class, Integer.class, Long.class, Character.class,
                Float.class, Double.class, Object.class, Number.class, String.class, int[].class, void.class};
        for (Object value : values) {
            for (Class<?> target : targets) {
                boolean accepted;
                try {
                    NativeConversions.convert(value, target);
                    accepted = true;
                } catch (NativeAccessFailure refusal) {
                    accepted = false;
                }
                if (NativeConversions.compatible(value, target) != accepted
                        || (NativeConversions.cost(value, target) >= 0) != accepted) {
                    throw new AssertionError("Conversion/overload compatibility differs for " + target);
                }
            }
        }
        String[] strings = {"", "ascii\0", "\u00e9", "\uD83D\uDE80", "\uD800", "\uDC00", "x\uDBFFx", "\uDC00\uD800",
                "\uDBFF\uDFFF", "a\uD800\uDC00z"};
        for (String text : strings) {
            boolean expected;
            try {
                StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                        .encode(CharBuffer.wrap(text));
                expected = true;
            } catch (CharacterCodingException invalid) {
                expected = false;
            }
            if (NativeConversions.validUtf16(text) != expected) {
                throw new AssertionError("UTF16 surrogate parity");
            }
        }
        byte[] slice = {(byte) 255, 'a', (byte) 0xc3, (byte) 0xa9, (byte) 255};
        if (!NativeConversions.utf8(slice, 1, 3).equals("a\u00e9")) {
            throw new AssertionError("UTF8 slice bounds");
        }
        for (byte[] invalid : new byte[][]{{(byte) 0xc0, (byte) 0x80}, {(byte) 0xed, (byte) 0xa0, (byte) 0x80},
                {(byte) 0xf4, (byte) 0x90, (byte) 0x80, (byte) 0x80}, {(byte) 0xe2, (byte) 0x80}}) {
            try {
                NativeConversions.utf8(invalid);
                throw new AssertionError("Malformed UTF8 accepted");
            } catch (NativeAccessFailure expected) {
                if (!expected.reason.equals("invalid_utf8")) {
                    throw expected;
                }
            }
        }
        Object fractional = Double.valueOf(1.5D);
        ThreadMXBean memory = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        memory.setThreadAllocatedMemoryEnabled(true);
        for (int repeat = 0; repeat < 10000; repeat++) {
            NativeConversions.cost(fractional, Integer.TYPE);
        }
        long thread = Thread.currentThread().getId();
        long bytes = memory.getThreadAllocatedBytes(thread);
        int checksum = 0;
        for (int repeat = 0; repeat < 10000; repeat++) {
            checksum += NativeConversions.cost(fractional, Integer.TYPE);
        }
        bytes = memory.getThreadAllocatedBytes(thread) - bytes;
        if (checksum != -10000 || bytes != 0) {
            throw new AssertionError("Expected overload misses allocated " + bytes);
        }
        System.out.println(
                "Conversion contracts passed: 756 compatibility cases, strict byte slices/surrogates and zero-allocation rejected overloads.");
    }
}

package betamoon.interop.query;

import betamoon.interop.binding.NativeAccessFailure;

/**
 * Shared by nested scopes in one audited entry; failed attempts do not refund
 * consumed work.
 */
public final class NativeDataBudget {
    private int work;
    private int bytes;
    private int inputBytes;

    public void work(int count) {
        if (count < 0 || count > NativeDataPlan.MAXIMUM_WORK - work) {
            throw new NativeAccessFailure("native_data_work_limit", "entry exceeds 4096 selected values/operations");
        }
        work += count;
    }

    public void inputs(int count) {
        if (count < 0 || count > NativeDataPlan.MAXIMUM_BYTES - inputBytes) {
            throw new NativeAccessFailure("native_data_input_limit", "entry exceeds 256 KiB of query inputs");
        }
        inputBytes += count;
    }

    public void bytes(int count) {
        if (count < 0 || count > NativeDataPlan.MAXIMUM_BYTES - bytes) {
            throw new NativeAccessFailure("native_data_byte_limit", "entry exceeds 256 KiB of detached output");
        }
        bytes += count;
    }
}

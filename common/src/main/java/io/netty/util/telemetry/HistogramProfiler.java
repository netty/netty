package io.netty.util.telemetry;

import io.netty.util.internal.MathUtil;
import io.netty.util.internal.ObjectUtil;

import java.util.Arrays;

public final class HistogramProfiler implements Profiler {
    private final int[] records;
    private final int recordsMask;
    private final TimeSupplier timeSupplier;

    private long start;
    private int index;

    public HistogramProfiler(int maxRecords, TimeSupplier timeSupplier) {
        if (!MathUtil.isPowerOfTwo(ObjectUtil.checkPositive(maxRecords, "maxRecords"))) {
            throw new IllegalArgumentException("maxRecords must be a power of 2. Consider using MathUtil.safeFindNextPositivePowerOfTwo()");
        }
        records = new int[maxRecords];
        Arrays.fill(records, -1);
        recordsMask = maxRecords - 1;
        this.timeSupplier = ObjectUtil.checkNotNull(timeSupplier, "timeSupplier");
    }

    @Override
    public void start() {
        start = timeSupplier.currentTime();
    }

    @Override
    public void stop() {
        final long end = timeSupplier.currentTime();
        records[index++ & recordsMask] = (int) (end - start);
    }

    @Override
    public String print() {
        int[] indices = new int[records.length];
        int j = 0;
        for (int i = index; i < records.length; i++) {
            indices[j++] = i;
        }
        for (int i = 0; i < index; i++) {
            indices[j++] = i;
        }
        assert j == indices.length : j;
        StringBuilder builder = new StringBuilder();
        int label = 1;
        for (int index : indices) {
            int record = records[index];
            if (record != -1) {
                builder.append(label++);
                builder.append(": ");
                builder.append(record);
                builder.append(System.lineSeparator());
            }
        }
        return builder.toString();
    }
}

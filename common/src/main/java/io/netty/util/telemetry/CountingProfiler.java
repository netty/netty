package io.netty.util.telemetry;

import io.netty.util.internal.ObjectUtil;

public final class CountingProfiler implements Profiler {
    private final Counter totalElapsed;
    private final Counter count;
    private final TimeSupplier timeSupplier;

    private long start;

    public CountingProfiler(CounterFactory counterFactory, TimeSupplier timeSupplier) {
        totalElapsed = counterFactory.newLongCounter();
        count = counterFactory.newIntegerCounter();
        this.timeSupplier = ObjectUtil.checkNotNull(timeSupplier, "timeSupplier");
    }

    @Override
    public void start() {
        count.increment();
        start = timeSupplier.currentTime();
    }

    @Override
    public void stop() {
        final long end = timeSupplier.currentTime();
        totalElapsed.incrementTimes(end - start);
    }

    @Override
    public String print() {
        String average = Long.toUnsignedString(Long.divideUnsigned(totalElapsed.longResultUnsigned(), count.longResultUnsigned()));
        return "Total: " + totalElapsed.print() + ", average: " + average + ", times: " + count.print();
    }
}

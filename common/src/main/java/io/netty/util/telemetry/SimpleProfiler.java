package io.netty.util.telemetry;

import io.netty.util.internal.ObjectUtil;

public final class SimpleProfiler implements Profiler {
    private final Counter totalElapsed;
    private final TimeSupplier timeSupplier;

    private long start;

    public SimpleProfiler(CounterFactory counterFactory, TimeSupplier timeSupplier) {
        totalElapsed = counterFactory.newLongCounter();
        this.timeSupplier = ObjectUtil.checkNotNull(timeSupplier, "timeSupplier");
    }

    @Override
    public void start() {
        start = timeSupplier.currentTime();
    }

    @Override
    public void stop() {
        final long end = timeSupplier.currentTime();
        totalElapsed.incrementTimes(end - start);
    }

    @Override
    public String print() {
        return "Total: " + totalElapsed.print();
    }
}

package io.netty.util.telemetry;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicLong;

final class CheckedConcurrentLongCounter implements Counter {
    private final AtomicLong value = new AtomicLong(Long.MIN_VALUE);

    @Override
    public void increment() {
        //noinspection ResultOfMethodCallIgnored
        Math.incrementExact(value.getAndIncrement());
    }

    @Override
    public void incrementTimes(int value) {
        //noinspection ResultOfMethodCallIgnored
        Math.addExact(this.value.getAndAdd(value), value);
    }

    @Override
    public void incrementTimes(long value) {
        //noinspection ResultOfMethodCallIgnored
        Math.addExact(this.value.getAndAdd(value), value);
    }

    @Override
    public int integerResultUnsigned() {
        return Math.toIntExact(longResultUnsigned());
    }

    @Override
    public long longResultUnsigned() {
        return value.get();
    }

    @Override
    public BigInteger bigIntegerResult() {
        return BigInteger.valueOf(Long.MIN_VALUE + longResultUnsigned());
    }

    @Override
    public String print() {
        return Long.toUnsignedString(longResultUnsigned());
    }
}

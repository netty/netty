package io.netty.util.telemetry;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;

final class CheckedConcurrentIntegerCounter implements Counter {
    private final AtomicInteger value = new AtomicInteger(Integer.MIN_VALUE);

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
        Math.addExact(this.value.getAndAdd(Math.toIntExact(value)), value);
    }

    @Override
    public int integerResultUnsigned() {
        return value.get();
    }

    @Override
    public long longResultUnsigned() {
        return integerResultUnsigned();
    }

    @Override
    public BigInteger bigIntegerResult() {
        return BigInteger.valueOf(Integer.MIN_VALUE + integerResultUnsigned());
    }

    @Override
    public String print() {
        return Integer.toUnsignedString(integerResultUnsigned());
    }
}

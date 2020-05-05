package io.netty.util.telemetry;

import java.math.BigInteger;
import java.util.concurrent.atomic.LongAdder;

final class ConcurrentLongCounter implements Counter {
    private final LongAdder adder = new LongAdder();

    {
        adder.add(Long.MIN_VALUE);
    }

    @Override
    public void increment() {
        adder.increment();
    }

    @Override
    public void incrementTimes(int value) {
        adder.add(value);
    }

    @Override
    public void incrementTimes(long value) {
        adder.add(value);
    }

    @Override
    public int integerResultUnsigned() {
        return (int) longResultUnsigned();
    }

    @Override
    public long longResultUnsigned() {
        return adder.sum();
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

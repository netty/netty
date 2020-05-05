package io.netty.util.telemetry;

import java.math.BigInteger;

final class LongCounter implements Counter {
    private long value = Long.MIN_VALUE;

    @Override
    public void increment() {
        value++;
    }

    @Override
    public void incrementTimes(int value) {
        this.value += value;
    }

    @Override
    public void incrementTimes(long value) {
        this.value += value;
    }

    @Override
    public int integerResultUnsigned() {
        return (int) longResultUnsigned();
    }

    @Override
    public long longResultUnsigned() {
        return value;
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

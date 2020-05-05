package io.netty.util.telemetry;

import java.math.BigInteger;

final class CheckedLongCounter implements Counter {
    private long value = Long.MIN_VALUE;

    @Override
    public void increment() {
        value = Math.incrementExact(value);
    }

    @Override
    public void incrementTimes(int value) {
        this.value = Math.addExact(this.value, value);
    }

    @Override
    public void incrementTimes(long value) {
        this.value = Math.addExact(this.value, value);
    }

    @Override
    public int integerResultUnsigned() {
        return Math.toIntExact(longResultUnsigned());
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

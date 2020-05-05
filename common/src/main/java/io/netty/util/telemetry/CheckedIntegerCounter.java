package io.netty.util.telemetry;

import java.math.BigInteger;

final class CheckedIntegerCounter implements Counter {
    private int value = Integer.MIN_VALUE;

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
        this.value = Math.addExact(this.value, Math.toIntExact(value));
    }

    @Override
    public int integerResultUnsigned() {
        return value;
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

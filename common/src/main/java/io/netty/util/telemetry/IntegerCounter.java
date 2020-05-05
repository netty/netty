package io.netty.util.telemetry;

import java.math.BigInteger;

final class IntegerCounter implements Counter {
    private int value = Integer.MIN_VALUE;

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
